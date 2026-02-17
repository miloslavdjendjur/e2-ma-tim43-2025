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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        "Avatar 1","Avatar 2","Avatar 3","Avatar 4","Avatar 5",
                        "Avatar 6","Avatar 7","Avatar 8","Avatar 9","Avatar 10"
                }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAvatar.setAdapter(adapter);

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String email = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String pass = etPass.getText().toString();
        String pass2 = etPass2.getText().toString();

        // Basic validation
        if (email.isEmpty() || username.isEmpty() || pass.isEmpty() || pass2.isEmpty()) {
            toast("Fill out all fields.");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Email not valid");
            return;
        }
        if (!pass.equals(pass2)) {
            toast("Passwords do not match");
            return;
        }

        authRepo.register(email, pass).addOnSuccessListener(authResult -> {
            FirebaseUser fbUser = authResult.getUser();
            if (fbUser == null) {
                toast("Error while registering");
                return;
            }

            String uid = fbUser.getUid();
            int avatarIndex = spAvatar.getSelectedItemPosition(); // 0..9

            User u = new User(uid, email, username, avatarIndex);

            userRepo.reserveUsernameAndCreateUser(u).addOnSuccessListener(v -> {
                fbUser.sendEmailVerification()
                        .addOnSuccessListener(x -> {
                            toast("Verification email sent.");
                            startActivity(new Intent(this, VerifyMailActivity.class).putExtra("email", email));
                            finish();
                        })
                        .addOnFailureListener(e -> toast("Error sending verification email: " + e.getMessage()));
            }).addOnFailureListener(e -> {
                toast(e.getMessage() != null && e.getMessage().contains("Username already") ?
                        "Username already taken." :
                        "Error saving profile: " + e.getMessage());

                FirebaseUser cur = authRepo.current();
                if (cur != null) cur.delete();
            });

        }).addOnFailureListener(e -> toast("Registration unsuccessful: " + e.getMessage()));
    }

    private void toast(@NonNull String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
