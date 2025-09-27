package com.example.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class VerifyMailActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_mail);

        TextView tv = findViewById(R.id.tvInfo);
        Button btnResend = findViewById(R.id.btnResend);

        String email = getIntent().getStringExtra("email");
        tv.setText("Na " + (email != null ? email : "email adresu") + " je poslat link za aktivaciju. Link važi 24h.");

        btnResend.setOnClickListener(v -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) { toast("Uloguj se pa pokušaj ponovo."); return; }

            new UserRepository().resetVerificationWindow(u.getUid())
                    .addOnSuccessListener(ignored ->
                            u.sendEmailVerification()
                                    .addOnSuccessListener(xx -> toast("Poslali smo novi verifikacioni email."))
                                    .addOnFailureListener(e -> toast("Slanje nije uspelo: " + e.getMessage()))
                    )
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
