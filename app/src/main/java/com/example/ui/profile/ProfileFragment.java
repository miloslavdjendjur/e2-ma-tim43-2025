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
import com.example.data.repo.UserRepository;
import com.example.data.service.LevelingService;
import com.example.myapplication.R;
import com.example.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileFragment extends Fragment {

    private ImageView ivAvatar, ivQr;
    private TextView tvUsername;

    private TextView tvLevel, tvTitle, tvXp, tvPp, tvNext, tvCoins, tvBadges;
    private ProgressBar progress, progressXp;

    private Button btnChangePass, btnLogout;

    private final UserRepository userRepo = new UserRepository();

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        btnChangePass.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));

        btnLogout.setOnClickListener(v -> doLogout());

        loadUser();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUser();
    }

    private void loadUser() {
        progress.setVisibility(View.VISIBLE);

        try {
            userRepo.getCurrentUser()
                    .addOnSuccessListener(this::applyUser)
                    .addOnFailureListener(e -> progress.setVisibility(View.GONE));
        } catch (Exception e) {
            progress.setVisibility(View.GONE);
        }
    }

    private void applyUser(DocumentSnapshot snap) {
        progress.setVisibility(View.GONE);

        User u = snap.toObject(User.class);
        if (u == null) return;

        // avatar
        int resId = getResources().getIdentifier(
                "avatar_" + u.avatarIndex, "drawable", requireContext().getPackageName());
        if (resId != 0) ivAvatar.setImageResource(resId);

        tvUsername.setText(u.username != null ? u.username : "");

        // QR
        try {
            String qr = (u.qrId != null && !u.qrId.isEmpty()) ? u.qrId : u.uid;
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(qr, BarcodeFormat.QR_CODE, 400, 400);
            ivQr.setImageBitmap(bitmap);
        } catch (Exception ignored) {}

        // Level progress
        int threshold = LevelingService.getThresholdForLevel(u.level);
        int xp = (int) u.xp;
        int pct = threshold <= 0 ? 0 : (int) Math.round((xp * 100.0) / threshold);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;

        tvLevel.setText("Level " + u.level);
        tvTitle.setText(u.title != null ? u.title : "");
        tvPp.setText("PP: " + u.pp);
        tvXp.setText("XP: " + xp + " / " + threshold);
        progressXp.setProgress(pct);

        tvNext.setText("Next level at: " + threshold + " XP");

        tvCoins.setText("Novčići: " + u.coins);
        tvBadges.setText("Bedževi: " + u.badges);
    }

    private void doLogout() {
        FirebaseAuth.getInstance().signOut();

        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);

        requireActivity().finish();
    }
}
