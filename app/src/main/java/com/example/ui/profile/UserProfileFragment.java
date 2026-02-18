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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.AvatarUtils;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.data.service.LevelingService;
import com.example.myapplication.R;
import com.example.ui.shop.ShopItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.google.zxing.BarcodeFormat;

import java.util.ArrayList;
import java.util.List;

public class UserProfileFragment extends Fragment {

    private String targetUid;
    private final UserRepository repo = new UserRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ImageView ivAvatar, ivQr;
    private TextView tvUsername, tvTitle, tvLevel, tvXp, tvPp;
    private ProgressBar progressXp;
    private Button btnInvite;

    // Lista za opremu
    private RecyclerView rvEquipment;
    private ProfileEquipmentAdapter equipmentAdapter;

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
        progressXp = view.findViewById(R.id.progressOtherXp);
        tvXp = view.findViewById(R.id.tvOtherXp);
        tvPp = view.findViewById(R.id.tvOtherPp);
        ivQr = view.findViewById(R.id.ivOtherQr);
        btnInvite = view.findViewById(R.id.btnInviteToAlliance);

        rvEquipment = view.findViewById(R.id.rvOtherEquipment);
        rvEquipment.setLayoutManager(new LinearLayoutManager(getContext()));
        equipmentAdapter = new ProfileEquipmentAdapter();
        rvEquipment.setAdapter(equipmentAdapter);

        if (targetUid != null) {
            loadUserProfile(targetUid);
            setupInviteButton(targetUid);
            loadTargetUserEquipment(targetUid);
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


    private void loadTargetUserEquipment(String uid) {
        List<ShopItem> allItems = new ArrayList<>();

        db.collection("users").document(uid).collection("equipment_potions").get()
                .addOnSuccessListener(potions -> {
                    for (DocumentSnapshot doc : potions) {
                        try {
                            String typeStr = doc.getString("type");
                            int count = doc.getLong("count") != null ? doc.getLong("count").intValue() : 0;

                            if (count > 0) {
                                String name = "Napitak";
                                if (typeStr != null) {
                                    if (typeStr.contains("PERM_PP5")) name = "Eliksir (+5%)";
                                    else if (typeStr.contains("PERM_PP10")) name = "Eliksir (+10%)";
                                    else if (typeStr.contains("PP20")) name = "Napitak (+20%)";
                                    else if (typeStr.contains("PP40")) name = "Napitak (+40%)";
                                }

                                int icon = getIconForType("POTION", typeStr);

                                allItems.add(ShopItem.createForInventory(name, "Količina: " + count, count, null, "POTION", icon, false));
                            }
                        } catch (Exception e) {}
                    }
                    loadClothes(uid, allItems);
                });
    }

    private void loadClothes(String uid, List<ShopItem> allItems) {
        db.collection("users").document(uid).collection("equipment_clothes").get()
                .addOnSuccessListener(clothes -> {
                    for (DocumentSnapshot doc : clothes) {
                        try {
                            String typeStr = doc.getString("type");
                            boolean active = Boolean.TRUE.equals(doc.getBoolean("active"));
                            int count = doc.getLong("count") != null ? doc.getLong("count").intValue() : 0;

                            if (count > 0 || active) {
                                String name = "Oprema";
                                if (typeStr != null) {
                                    if (typeStr.contains("GLOVES")) name = "Rukavice";
                                    else if (typeStr.contains("BOOTS")) name = "Čizme";
                                    else if (typeStr.contains("SHIELD")) name = "Štit";
                                }

                                int icon = getIconForType("CLOTHES", typeStr);

                                allItems.add(ShopItem.createForInventory(name, active ? "Aktivno" : "U inventaru", count, null, "CLOTHES", icon, active));
                            }
                        } catch (Exception e) {}
                    }
                    loadWeapons(uid, allItems);
                });
    }

    private void loadWeapons(String uid, List<ShopItem> allItems) {
        db.collection("users").document(uid).collection("equipment_weapons").get()
                .addOnSuccessListener(weapons -> {
                    for (DocumentSnapshot doc : weapons) {
                        try {
                            String typeStr = doc.getString("type");
                            int level = doc.getLong("level") != null ? doc.getLong("level").intValue() : 0;

                            if (level > 0) {
                                String name = (typeStr != null && typeStr.contains("SWORD")) ? "Mač" : "Luk i Strela";

                                int icon = getIconForType("WEAPON", typeStr);

                                allItems.add(ShopItem.createForInventory(name, "Level: " + level, 1, null, "WEAPON", icon, true));
                            }
                        } catch (Exception e) {}
                    }
                    // KRAJ: Osveži adapter da prikaže sve
                    equipmentAdapter.setItems(allItems);
                });
    }

    private int getIconForType(String category, String typeStr) {
        if (typeStr == null) return R.drawable.ic_launcher_foreground;

        typeStr = typeStr.toUpperCase();

        if ("POTION".equals(category)) {
            return R.drawable.potion;
        }
        else if ("CLOTHES".equals(category)) {
            if (typeStr.contains("GLOVES")) return R.drawable.gloves;
            if (typeStr.contains("BOOTS")) return R.drawable.boots;
            if (typeStr.contains("SHIELD")) return R.drawable.sheild;
        }
        else if ("WEAPON".equals(category)) {
            if (typeStr.contains("SWORD")) return R.drawable.sword;
            if (typeStr.contains("BOW")) return R.drawable.bow;
        }

        return R.drawable.ic_launcher_foreground;
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