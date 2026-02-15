package com.example.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.data.model.User;
import com.example.data.model.boss.Boss;
import com.example.data.repo.BossRepository;
import com.example.data.repo.UserRepository;
import com.example.data.service.BossService;
import com.example.data.service.LevelingService;
import com.example.myapplication.R;
import com.example.ui.auth.LoginActivity;
import com.example.ui.boss.BossPrepActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileFragment extends Fragment {

    private ImageView ivAvatar, ivQr;
    private TextView tvUsername;
    private TextView tvLevel, tvTitle, tvXp, tvPp, tvNext, tvCoins, tvBadges;
    private ProgressBar progress, progressXp;
    private Button btnChangePass, btnLogout, btnBossFight;

    private final UserRepository userRepo = new UserRepository();
    private final BossRepository bossRepo = new BossRepository();

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicijalizacija UI komponenti
        ivAvatar = view.findViewById(R.id.ivAvatar);
        ivQr = view.findViewById(R.id.ivQr);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvLevel = view.findViewById(R.id.tvLevelProg);
        tvTitle = view.findViewById(R.id.tvTitleProg);
        tvXp = view.findViewById(R.id.tvXp);
        tvPp = view.findViewById(R.id.tvPp);
        tvNext = view.findViewById(R.id.tvNext);
        tvCoins = view.findViewById(R.id.tvCoins);
        tvBadges = view.findViewById(R.id.tvBadges);
        progress = view.findViewById(R.id.progress);
        progressXp = view.findViewById(R.id.progressXp);
        btnChangePass = view.findViewById(R.id.btnChangePass);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnBossFight = view.findViewById(R.id.btnBossFight);

        // Dugme je inicijalno skriveno
        btnBossFight.setVisibility(View.GONE);

        btnLogout.setOnClickListener(v -> doLogout());

        // Klik na dugme otvara Boss Preparation Activity
        btnBossFight.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), BossPrepActivity.class);
            startActivity(i);
        });

        load();
    }

    private void load() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        userRepo.getCurrentUser().addOnSuccessListener(this::applyUser);
    }

    private void applyUser(DocumentSnapshot snap) {
        if (!isAdded() || getContext() == null) return;
        if (progress != null) progress.setVisibility(View.GONE);

        User u = snap.toObject(User.class);
        if (u == null) return;

        // Prikaz avatara
        int resId = getResources().getIdentifier(
                "avatar_" + u.avatarIndex, "drawable", requireContext().getPackageName());
        if (resId != 0) ivAvatar.setImageResource(resId);

        tvUsername.setText(u.username != null ? u.username : "");

        try {
            String qr = (u.qrId != null && !u.qrId.isEmpty()) ? u.qrId : u.uid;
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(qr, BarcodeFormat.QR_CODE, 400, 400);
            ivQr.setImageBitmap(bitmap);
        } catch (Exception ignored) {}

        int threshold = LevelingService.getThresholdForLevel(u.level);
        int xp = (int) u.xp;
        int pct = threshold <= 0 ? 0 : (int) Math.round((xp * 100.0) / threshold);
        pct = Math.max(0, Math.min(100, pct));

        tvLevel.setText("Level " + u.level);
        tvTitle.setText(u.title != null ? u.title : "");
        tvPp.setText("Power: " + u.pp);
        tvXp.setText("XP: " + xp + " / " + threshold);
        progressXp.setProgress(pct);
        tvNext.setText("Next level at: " + threshold + " XP");
        tvCoins.setText("Coins: " + u.coins);
        tvBadges.setText("Badges: " + u.badges);

        setupBossButton(u);
    }

    /**
     * Upravlja vidljivošću dugmeta za borbu sa bosom prema specifikaciji.
     * Dugme se pojavljuje tek na nivou 2 i nestaje ako je bos poražen.
     */
    private void setupBossButton(User user) {
        if (user.level < 2) {
            btnBossFight.setVisibility(View.GONE);
            return;
        }

        bossRepo.getCurrentBoss().addOnSuccessListener(doc -> {
            if (!isAdded()) return;

            if (doc != null && doc.exists()) {
                Boss currentBoss = doc.toObject(Boss.class);

                if (currentBoss != null) {
                    if (!currentBoss.isDefeated()) {
                        btnBossFight.setVisibility(View.VISIBLE);
                    } else {
                        btnBossFight.setVisibility(View.GONE);
                    }
                } else {
                    btnBossFight.setVisibility(View.VISIBLE);
                }
            } else {
                // Ako dokument ne postoji, a nivo je 2+, korisnik ima pravo na borbu
                btnBossFight.setVisibility(View.VISIBLE);
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) btnBossFight.setVisibility(View.GONE);
        });
    }

    private void doLogout() {
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}