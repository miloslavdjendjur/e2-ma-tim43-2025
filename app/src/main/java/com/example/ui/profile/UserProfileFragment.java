package com.example.ui.profile;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog; // Dodat import za dijalog

import com.example.data.model.AvatarUtils;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.data.service.LevelingService;
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.google.zxing.BarcodeFormat;

public class UserProfileFragment extends Fragment {

    private String targetUid;
    private final UserRepository repo = new UserRepository();

    private ImageView ivAvatar, ivQr;
    private TextView tvUsername, tvTitle, tvLevel, tvXp, tvPp, tvBadges;
    private ProgressBar progressXp;
    private Button btnInvite;

    public UserProfileFragment() {
        super(R.layout.fragment_user_profile);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetUid = getArguments().getString("TARGET_UID");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivAvatar = view.findViewById(R.id.ivOtherAvatar);
        tvUsername = view.findViewById(R.id.tvOtherUsername);
        tvTitle = view.findViewById(R.id.tvOtherTitle);
        tvLevel = view.findViewById(R.id.tvOtherLevel);

        progressXp = view.findViewById(R.id.progressXp);
        tvXp = view.findViewById(R.id.tvOtherXp);

        tvPp = view.findViewById(R.id.tvOtherPp);
        tvBadges = view.findViewById(R.id.tvOtherBadges);
        ivQr = view.findViewById(R.id.ivOtherQr);
        btnInvite = view.findViewById(R.id.btnInviteToAlliance);

        if (targetUid != null) {
            loadUserProfile(targetUid);
            setupInviteButton(targetUid);
        } else {
            Toast.makeText(getContext(), "Greška: Korisnik nije pronađen.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadUserProfile(String uid) {
        repo.getUser(uid).addOnSuccessListener(snap -> {
            if (!isAdded()) return;
            User u = snap.toObject(User.class);
            if (u == null) return;

            ivAvatar.setImageResource(AvatarUtils.imageResForAvatarIndex(u.avatarIndex));
            tvUsername.setText(u.username);
            tvTitle.setText(u.title != null ? u.title : "");
            tvLevel.setText("Level " + u.level);
            tvPp.setText("PP: " + u.pp);
            tvBadges.setText("Bedževi: " + u.badges);

            int threshold = LevelingService.getThresholdForLevel(u.level);
            int xp = (int) u.xp;
            int pct = threshold <= 0 ? 0 : (int) Math.round((xp * 100.0) / threshold);

            progressXp.setProgress(Math.min(100, pct));
            tvXp.setText(xp + " / " + threshold + " XP");

            try {
                String qrContent = (u.qrId != null && !u.qrId.isEmpty()) ? u.qrId : u.uid;
                BarcodeEncoder encoder = new BarcodeEncoder();
                Bitmap bitmap = encoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 400, 400);
                ivQr.setImageBitmap(bitmap);
            } catch (Exception e) {
                ivQr.setVisibility(View.GONE);
            }
        });
    }

    private void setupInviteButton(String targetUid) {
        String myUid = FirebaseAuth.getInstance().getUid();

        if (myUid == null || myUid.equals(targetUid)) {
            btnInvite.setVisibility(View.GONE);
            return;
        }

        repo.getCurrentUser().addOnSuccessListener(snapMe -> {
            User me = snapMe.toObject(User.class);

            if (me == null || me.allianceId == null || me.allianceId.isEmpty()) {
                btnInvite.setVisibility(View.GONE);
                return;
            }

            repo.getUser(targetUid).addOnSuccessListener(snapTarget -> {
                User target = snapTarget.toObject(User.class);
                if (target == null) return;

                boolean alreadyInMyAlliance = target.allianceId != null && target.allianceId.equals(me.allianceId);

                if (alreadyInMyAlliance) {
                    btnInvite.setVisibility(View.GONE);
                } else {
                    btnInvite.setVisibility(View.VISIBLE);
                    // OVO JE METODA KOJA TI JE FALILA
                    btnInvite.setOnClickListener(v -> showConfirmInviteDialog(me, targetUid));
                }
            });
        });
    }

    private void showConfirmInviteDialog(User me, String targetUid) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Poziv u savez")
                .setMessage("Da li sigurno želiš da pošalješ pozivnicu ovom korisniku?")
                .setPositiveButton("Pošalji", (dialog, which) -> sendInvite(me, targetUid))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void sendInvite(User me, String targetUid) {
        repo.getAlliance(me.allianceId).addOnSuccessListener(alliance -> {
            if (alliance != null) {
                repo.inviteToAlliance(targetUid, alliance, me.username)
                        .addOnSuccessListener(v ->
                                Toast.makeText(getContext(), "Pozivnica poslata!", Toast.LENGTH_SHORT).show()
                        );
            }
        });
    }
}