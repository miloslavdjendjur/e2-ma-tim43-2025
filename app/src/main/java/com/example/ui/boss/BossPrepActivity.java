package com.example.ui.boss;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.repo.EquipmentRepository;
import com.example.data.service.EquipmentService;
import com.example.myapplication.R;
import com.example.ui.equipment.EquipmentStoreActivity;
import com.example.ui.equipment.MyEquipmentActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Locale;

public class BossPrepActivity extends AppCompatActivity {

    private final EquipmentRepository repo = new EquipmentRepository();
    private final EquipmentService service = new EquipmentService();

    private TextView tvLoading, tvBossLevel, tvBossName;
    private ImageView ivBoss;

    private TextView tvSlotWeapon, tvSlotShield, tvSlotGloves, tvSlotBoots, tvSlotPotion;
    private TextView tvEffectivePp, tvHitBonus, tvExtraTry;

    private Button btnMyEquipment, btnStore, btnStartFight;

    private int userLevel = 1;
    private int effectivePp = 0;
    private int hitBonusPct = 0;
    private int extraTryPct = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boss_prep);

        tvLoading = findViewById(R.id.tvLoading);
        tvBossLevel = findViewById(R.id.tvBossLevel);
        tvBossName = findViewById(R.id.tvBossName);
        ivBoss = findViewById(R.id.ivBoss);

        tvSlotWeapon = findViewById(R.id.tvSlotWeapon);
        tvSlotShield = findViewById(R.id.tvSlotShield);
        tvSlotGloves = findViewById(R.id.tvSlotGloves);
        tvSlotBoots = findViewById(R.id.tvSlotBoots);
        tvSlotPotion = findViewById(R.id.tvSlotPotion);

        tvEffectivePp = findViewById(R.id.tvEffectivePp);
        tvHitBonus = findViewById(R.id.tvHitBonus);
        tvExtraTry = findViewById(R.id.tvExtraTry);

        btnMyEquipment = findViewById(R.id.btnMyEquipment);
        btnStore = findViewById(R.id.btnStore);
        btnStartFight = findViewById(R.id.btnStartFight);

        btnMyEquipment.setOnClickListener(v -> startActivity(new Intent(this, MyEquipmentActivity.class)));
        btnStore.setOnClickListener(v -> startActivity(new Intent(this, EquipmentStoreActivity.class)));
        btnStartFight.setOnClickListener(v -> startFight());

        setEnabled(false);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setEnabled(false);
        load();
    }

    private void setEnabled(boolean enabled) {
        btnMyEquipment.setEnabled(enabled);
        btnStore.setEnabled(enabled);
        btnStartFight.setEnabled(enabled);
        tvLoading.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void load() {
        repo.getUser()
                .addOnSuccessListener(userDoc -> {
                    Long lvl = userDoc.getLong("level");
                    userLevel = (lvl != null) ? lvl.intValue() : 1;

                    tvBossName.setText("The Boss");
                    tvBossLevel.setText("Level: " + userLevel);

                    service.computeEffectiveStats(userDoc)
                            .addOnSuccessListener(stats -> {
                                effectivePp = stats.effectivePp;
                                hitBonusPct = stats.hitBonusPct;
                                extraTryPct = stats.extraTryPct;

                                tvEffectivePp.setText("Effective PP: " + effectivePp);
                                tvHitBonus.setText(String.format(Locale.US, "Hit bonus: +%d%%", hitBonusPct));
                                tvExtraTry.setText(String.format(Locale.US, "Extra try chance: %d%%", extraTryPct));

                                loadSlots();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to load equipment stats", Toast.LENGTH_SHORT).show();
                                setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show();
                    setEnabled(true);
                });
    }

    private void loadSlots() {
        Tasks.whenAllSuccess(repo.getWeapons(), repo.getClothes(), repo.getPotions())
                .addOnSuccessListener(list -> {
                    QuerySnapshot weapons = (QuerySnapshot) list.get(0);
                    QuerySnapshot clothes = (QuerySnapshot) list.get(1);
                    QuerySnapshot potions = (QuerySnapshot) list.get(2);

                    // Weapon: show if any weapon doc exists
                    String weaponText = "None";
                    for (QueryDocumentSnapshot d : weapons) {
                        String type = d.getString("type");
                        Long level = d.getLong("level");
                        if (type != null) {
                            weaponText = type + (level != null ? (" +" + level) : "");
                            break;
                        }
                    }
                    tvSlotWeapon.setText(weaponText);

                    // Clothes: active + usesLeft
                    tvSlotShield.setText(statusForClothes(clothes, "SHIELD"));
                    tvSlotGloves.setText(statusForClothes(clothes, "GLOVES"));
                    tvSlotBoots.setText(statusForClothes(clothes, "BOOTS"));

                    // Potion: pendingUse
                    String potionText = "None pending";
                    for (QueryDocumentSnapshot d : potions) {
                        Boolean pending = d.getBoolean("pendingUse");
                        if (pending != null && pending) {
                            String type = d.getString("type");
                            Long count = d.getLong("count");
                            potionText = (type != null ? type : "Potion") + (count != null ? (" x" + count) : "");
                            break;
                        }
                    }
                    tvSlotPotion.setText(potionText);

                    setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load slots", Toast.LENGTH_SHORT).show();
                    setEnabled(true);
                });
    }

    private String statusForClothes(QuerySnapshot clothes, String type) {
        for (QueryDocumentSnapshot d : clothes) {
            String t = d.getString("type");
            if (t == null || !t.equals(type)) continue;

            Boolean active = d.getBoolean("active");
            Long usesLeft = d.getLong("usesLeft");
            Long stacked = d.getLong("stackedPercent");

            if (active != null && active && usesLeft != null && usesLeft > 0) {
                int pct = (stacked != null) ? stacked.intValue() : 0;
                return "Active (+" + pct + "%, " + usesLeft + " uses)";
            }
            return "Inactive";
        }
        return "Inactive";
    }

    private void startFight() {
        Intent i = new Intent(this, BossFightActivity.class);
        i.putExtra("bossLevel", userLevel);
        i.putExtra("effectivePp", effectivePp);
        i.putExtra("hitBonusPct", hitBonusPct);
        i.putExtra("extraTryPct", extraTryPct);
        startActivity(i);
    }
}
