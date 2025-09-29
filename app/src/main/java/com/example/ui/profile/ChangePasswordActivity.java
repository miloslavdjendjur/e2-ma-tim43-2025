package com.example.ui.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;

public class ChangePasswordActivity extends AppCompatActivity {
    private EditText etOld, etNew, etNew2;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        etOld = findViewById(R.id.etOld);
        etNew = findViewById(R.id.etNew);
        etNew2 = findViewById(R.id.etNew2);
        progress = findViewById(R.id.progress);

        Button btn = findViewById(R.id.btnConfirm);
        btn.setOnClickListener(v -> change());
    }

    private void change() {
        String o = etOld.getText().toString();
        String n1 = etNew.getText().toString();
        String n2 = etNew2.getText().toString();

        if (TextUtils.isEmpty(o) || TextUtils.isEmpty(n1) || TextUtils.isEmpty(n2)) {
            Toast.makeText(this, "Popunite sva polja", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!n1.equals(n2)) {
            Toast.makeText(this, "Lozinke se ne poklapaju", Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(android.view.View.VISIBLE);
        var auth = FirebaseAuth.getInstance();
        var user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        var cred = EmailAuthProvider.getCredential(user.getEmail(), o);
        user.reauthenticate(cred).addOnSuccessListener(aVoid ->
                user.updatePassword(n1).addOnSuccessListener(v -> {
                    progress.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "Lozinka promenjena", Toast.LENGTH_SHORT).show();
                    finish();
                }).addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                })
        ).addOnFailureListener(e -> {
            progress.setVisibility(View.GONE);
            Toast.makeText(this, "Pogrešna stara lozinka", Toast.LENGTH_SHORT).show();
        });
    }
}
