package com.example.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileActivity extends AppCompatActivity {
    private ImageView ivAvatar, ivQr;
    private TextView tvUsername, tvLevel, tvTitle, tvXP, tvPP, tvCoins, tvBadges;
    private ProgressBar progress;
    private Button btnChangePass;

    private final UserRepository repo = new UserRepository();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        bindViews();
        loadUser();
    }

    private void bindViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        ivQr = findViewById(R.id.ivQr);
        tvUsername = findViewById(R.id.tvUsername);
        tvLevel = findViewById(R.id.tvLevel);
        tvTitle = findViewById(R.id.tvTitle);
        tvXP = findViewById(R.id.tvXP);
        tvPP = findViewById(R.id.tvPP);
        tvCoins = findViewById(R.id.tvCoins);
        tvBadges = findViewById(R.id.tvBadges);
        progress = findViewById(R.id.progress);
        btnChangePass = findViewById(R.id.btnChangePass);

        btnChangePass.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
    }

    private void loadUser() {
        progress.setVisibility(View.VISIBLE);
        repo.getCurrentUser().addOnSuccessListener(this::applyUser)
                .addOnFailureListener(e -> progress.setVisibility(View.GONE));
    }

    private void applyUser(DocumentSnapshot snap) {
        progress.setVisibility(View.GONE);
        User u = snap.toObject(User.class);
        if (u == null) return;

        int resId = getResources().getIdentifier("avatar_" + u.avatarIndex,
                "drawable", getPackageName());
        if (resId != 0) ivAvatar.setImageResource(resId);

        tvUsername.setText(u.username);
        tvLevel.setText("Lvl " + u.level);
        tvTitle.setText(u.title);
        tvXP.setText("XP: " + u.xp);
        tvPP.setText("PP: " + u.pp);
        tvCoins.setText("Novčići: " + u.coins);
        tvBadges.setText("Bedževi: " + u.badges);

        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(u.uid, BarcodeFormat.QR_CODE, 400, 400);
            ivQr.setImageBitmap(bitmap);
        } catch (Exception ignored) {}
    }
}
