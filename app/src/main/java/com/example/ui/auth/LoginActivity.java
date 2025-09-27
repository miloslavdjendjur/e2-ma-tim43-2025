package com.example.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.repo.AuthRepository;
import com.example.data.repo.UserRepository;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPass;
    private Button btnLogin, btnGoRegister;

    private final AuthRepository authRepo = new AuthRepository();
    private final UserRepository userRepo = new UserRepository();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPass  = findViewById(R.id.etPass);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> doLogin());
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass = etPass.getText().toString();

        if (email.isEmpty() || pass.isEmpty()) { toast("Unesi email i lozinku"); return; }

        authRepo.login(email, pass).addOnSuccessListener(ar -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) { toast("Greška pri prijavi"); return; }

            u.reload().addOnSuccessListener(x -> {
                if (!u.isEmailVerified()) {
                    toast("Nalog nije aktiviran email-om. Proveri inbox.");
                    startActivity(new Intent(this, VerifyMailActivity.class).putExtra("email", email));
                    return;
                }

                userRepo.getUser(u.getUid()).addOnSuccessListener(this::handleUserDoc)
                        .addOnFailureListener(e -> toast("Greška pri čitanju profila: " + e.getMessage()));
            });

        }).addOnFailureListener(e -> toast("Prijava nije uspela: " + e.getMessage()));
    }

    private void handleUserDoc(DocumentSnapshot ds) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { toast("Sesija istekla"); return; }

        Timestamp createdAt = ds.getTimestamp("createdAt");
        long now = System.currentTimeMillis();
        long ageMs = (createdAt != null) ? now - createdAt.toDate().getTime() : 0L;
        long maxAgeMs = 24L * 60L * 60L * 1000L; // 24h

        if (ageMs > maxAgeMs) {
            // Striktna varijanta: ne dopuštamo aktivaciju posle 24h
            toast("Link je istekao (24h). Registruj se ponovo.");
            // (po želji: ovde možeš i da cur.delete() + releaseUsername(...) da očistiš nalog)
            return;
        }

        Boolean active = ds.getBoolean("active");
        if (active == null || !active) {
            userRepo.setActive(u.getUid())
                    .addOnSuccessListener(v -> proceedToMain(u.getUid()))
                    .addOnFailureListener(e -> { toast("Greška pri aktivaciji: " + e.getMessage()); proceedToMain(u.getUid()); });
        } else {
            proceedToMain(u.getUid());
        }
    }

    private void proceedToMain(@NonNull String uid) {
        userRepo.updateLastLogin(uid);
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void toast(@NonNull String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
