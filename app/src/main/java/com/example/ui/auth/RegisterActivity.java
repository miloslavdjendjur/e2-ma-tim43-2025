package com.example.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.User;
import com.example.data.repo.AuthRepository;
import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etUsername, etPass, etPass2;
    private Spinner spAvatar;
    private Button btnRegister;

    private final AuthRepository authRepo = new AuthRepository();
    private final UserRepository userRepo = new UserRepository();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPass = findViewById(R.id.etPass);
        etPass2 = findViewById(R.id.etPass2);
        spAvatar = findViewById(R.id.spAvatar);
        btnRegister = findViewById(R.id.btnRegister);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Avatar 0","Avatar 1","Avatar 2","Avatar 3","Avatar 4"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAvatar.setAdapter(adapter);

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String email = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String pass = etPass.getText().toString();
        String pass2 = etPass2.getText().toString();

        if (email.isEmpty() || username.isEmpty() || pass.isEmpty() || pass2.isEmpty()) { toast("Popuni sva polja"); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("Email nije ispravan"); return; }
        if (!pass.equals(pass2)) { toast("Lozinke se ne poklapaju"); return; }

        // 1) Kreiraj Auth user
        authRepo.register(email, pass).addOnSuccessListener(authResult -> {
            FirebaseUser fbUser = authResult.getUser();
            if (fbUser == null) { toast("Greška pri registraciji"); return; }

            String uid = fbUser.getUid();
            int avatarIndex = spAvatar.getSelectedItemPosition();
            User u = new User(uid, email, username, avatarIndex);

            // 2) Rezervacija username-a + kreiranje users/{uid}
            userRepo.reserveUsernameAndCreateUser(u).addOnSuccessListener(v -> {

                // 3) Default verifikacioni mejl (bez ActionCodeSettings)
                fbUser.sendEmailVerification()
                        .addOnSuccessListener(x -> {
                            toast("Verifikacioni email poslat.");
                            // ostavi korisnika ulogovanog da bi 'Resend' radio iz Verify ekrana
                            startActivity(new Intent(this, VerifyMailActivity.class).putExtra("email", email));
                            finish();
                        })
                        .addOnFailureListener(e -> toast("Slanje verifikacionog emaila nije uspelo: " + e.getMessage()));

            }).addOnFailureListener(e -> {
                // username je zauzet ili neka druga greška – počisti sveže kreiran Auth nalog
                toast(e.getMessage() != null && e.getMessage().contains("Username already") ?
                        "Korisničko ime je zauzeto. Izaberi drugo." :
                        "Greška pri čuvanju profila: " + e.getMessage());

                FirebaseUser cur = authRepo.current();
                if (cur != null) cur.delete(); // da može ponovna registracija
            });

        }).addOnFailureListener(e -> toast("Registracija nije uspela: " + e.getMessage()));
    }

    private void toast(@NonNull String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
