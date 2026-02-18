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

import com.example.data.model.equipment.type.PotionType;
import com.example.data.repo.EquipmentRepository;
import com.example.data.service.EquipmentService;
import com.example.myapplication.R;
import com.example.ui.equipment.EquipmentStoreActivity;
import com.example.ui.equipment.MyEquipmentActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.myapplication.MainActivity;

import java.util.Locale;

public class BossPrepActivity extends AppCompatActivity {

    private final EquipmentRepository repo = new EquipmentRepository();
    private final EquipmentService service = new EquipmentService();

    // NEW UI (matches new activity_boss_prep.xml)
    private LinearProgressIndicator progress;
    private TextView tvLevel;

    private ImageView ivBossIcon;

    private TextView tvEffectivePp, tvHitBonus, tvExtraTry;
    private TextView tvWeaponValue, tvShieldValue, tvGlovesValue, tvBootsValue;

    // We reuse tvNote for potion effects / tips
    private TextView tvNote, tvRewards;

    private Button btnStore, btnStartFight, btnMyEquipment;
    private final com.example.data.service.BossService bossService = new com.example.data.service.BossService();

    private int userLevel = 1;
    private int effectivePp = 0;
    private int hitBonusPct = 0;
    private int extraTryPct = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boss_prep);

        progress = findViewById(R.id.progress);
        tvLevel = findViewById(R.id.tvLevel);

        ivBossIcon = findViewById(R.id.ivBossIcon);

        tvEffectivePp = findViewById(R.id.tvEffectivePp);
        tvHitBonus = findViewById(R.id.tvHitBonus);
        tvExtraTry = findViewById(R.id.tvExtraTry);

        tvWeaponValue = findViewById(R.id.tvWeaponValue);
        tvShieldValue = findViewById(R.id.tvShieldValue);
        tvGlovesValue = findViewById(R.id.tvGlovesValue);
        tvBootsValue = findViewById(R.id.tvBootsValue);

        tvNote = findViewById(R.id.tvNote);
        tvRewards = findViewById(R.id.tvRewards);

        btnMyEquipment = findViewById(R.id.btnMyEquipment);
        btnStore = findViewById(R.id.btnStore);
        btnStartFight = findViewById(R.id.btnStartFight);

        btnMyEquipment.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("NAVIGATE_TO", "shopFragment");
            intent.putExtra("TAB_INDEX", 1);
            intent.putExtra("IS_FROM_BOSS_PREP", true);

            startActivity(intent);
        });
        btnStore.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("NAVIGATE_TO", "shopFragment");
            intent.putExtra("TAB_INDEX", 0);
            intent.putExtra("IS_FROM_BOSS_PREP", true);

            startActivity(intent);
        });
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

        if (progress != null) {
            progress.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private void load() {
        repo.getUser()
                .addOnSuccessListener(userDoc -> {
                    Long lvl = userDoc.getLong("level");
                    userLevel = (lvl != null) ? lvl.intValue() : 1;

                    if (ivBossIcon == null) {
                        ivBossIcon.setImageResource(R.drawable.boss_profile);
                    }

                    int bossLevelToFight = Math.max(1, userLevel - 1);
                    tvLevel.setText("Boss level: " + bossLevelToFight);


                    service.computeEffectiveStats(userDoc)
                            .addOnSuccessListener(stats -> {
                                effectivePp = stats.effectivePp;
                                hitBonusPct = stats.hitBonusPct;
                                extraTryPct = stats.extraTryPct;

                                tvEffectivePp.setText("Power: " + effectivePp);

                                if (tvRewards != null) {
                                    tvRewards.setText(potentialRewardsText(userLevel));
                                }
                                tvHitBonus.setText(String.format(Locale.US, "Hit bonus: +%d%%", hitBonusPct));
                                tvExtraTry.setText(String.format(Locale.US, "Extra try chance: %d%%", extraTryPct));

                                loadSlotsAndPotions();
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

    private void loadSlotsAndPotions() {
        Tasks.whenAllSuccess(repo.getWeapons(), repo.getClothes(), repo.getPotions())
                .addOnSuccessListener(list -> {
                    QuerySnapshot weapons = (QuerySnapshot) list.get(0);
                    QuerySnapshot clothes = (QuerySnapshot) list.get(1);
                    QuerySnapshot potions = (QuerySnapshot) list.get(2);

                    // Weapon (simple: first weapon found)
                    tvWeaponValue.setText(firstWeaponText(weapons));

                    // Clothes types (your Firestore types: SHIELD / GLOVES / BOOTS)
                    tvShieldValue.setText(statusForClothes(clothes, "SHIELD"));
                    tvGlovesValue.setText(statusForClothes(clothes, "GLOVES"));
                    tvBootsValue.setText(statusForClothes(clothes, "BOOTS"));

                    // Potion effects in tvNote
                    tvNote.setText(potionEffectsText(potions));

                    setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load equipment/potions", Toast.LENGTH_SHORT).show();
                    setEnabled(true);
                });
    }

    private String firstWeaponText(QuerySnapshot weapons) {
        String weaponText = "—";
        for (QueryDocumentSnapshot d : weapons) {
            String type = d.getString("type");
            Long level = d.getLong("level");
            if (type != null) {
                weaponText = type + (level != null ? (" +" + level) : "");
                break;
            }
        }
        return weaponText;
    }

    private String statusForClothes(QuerySnapshot clothes, String type) {
        for (QueryDocumentSnapshot d : clothes) {
            String t = d.getString("type");
            if (t == null || !t.equals(type)) continue;

            Boolean active = d.getBoolean("active");
            Long usesLeft = d.getLong("usesLeft");
            Long stacked = d.getLong("stackedPercent"); // your schema

            if (active != null && active && usesLeft != null && usesLeft > 0) {
                int pct = (stacked != null) ? stacked.intValue() : 0;
                return "Active (+" + pct + "%, " + usesLeft + " uses)";
            }
            return "Inactive";
        }
        return "Inactive";
    }

    private String potionEffectsText(QuerySnapshot potions) {
        // You currently store "pendingUse" + "type" + "count".
        // We’ll list all pending potions and their effect descriptions.
        StringBuilder sb = new StringBuilder();

        boolean anyPending = false;

        for (QueryDocumentSnapshot d : potions) {
            Boolean pending = d.getBoolean("pendingUse");
            if (pending == null || !pending) continue;

            anyPending = true;

            String typeStr = d.getString("type");
            Long count = d.getLong("count");
            int c = (count != null) ? count.intValue() : 1;

            String effect = describePotion(typeStr);

            sb.append("• ")
                    .append(typeStr != null ? typeStr : "Potion")
                    .append(" x").append(c);

            if (!effect.isEmpty()) {
                sb.append(" — ").append(effect);
            }
            sb.append("\n");
        }

        if (!anyPending) {
            sb.append("No pending potions.\n");
        }

        // Small helpful tip (matches your prep screen idea)
        sb.append("\nTip: One-shot potions are consumed in the next boss fight.");

        return sb.toString().trim();
    }

    private String describePotion(String typeStr) {
        if (typeStr == null) return "";

        // Your enum:
        // ONE_SHOT_PP20, ONE_SHOT_PP40, PERM_PP5, PERM_PP10
        try {
            PotionType t = PotionType.valueOf(typeStr);
            switch (t) {
                case ONE_SHOT_PP20:
                    return "+20% PP (one fight)";
                case ONE_SHOT_PP40:
                    return "+40% PP (one fight)";
                case PERM_PP5:
                    return "+5% PP (permanent)";
                case PERM_PP10:
                    return "+10% PP (permanent)";
                default:
                    return "";
            }
        } catch (IllegalArgumentException ignored) {
            // If Firestore stores different strings, just show raw type
            return "";
        }
    }

    private void startFight() {
        int bossLevelToFight = Math.max(1, userLevel - 1);

        Intent i = new Intent(this, BossFightActivity.class);
        i.putExtra("bossLevel", bossLevelToFight);
        i.putExtra("effectivePp", effectivePp);
        i.putExtra("hitBonusPct", hitBonusPct);
        i.putExtra("extraTryPct", extraTryPct);
        startActivity(i);
    }

    private String potentialRewardsText(int bossLevel) {
        int baseCoins = bossService.calculateBaseCoinReward(bossLevel);
        int halfCoins = (int) Math.round(baseCoins * 0.5);

        // Drop chance: 20% ako pobediš, 10% ako boss "escape" ali je HP <= 50%
        // Ako boss escape i HP > 50% -> 0 reward
        int dropWinPct = 20;
        int dropHalfPct = 10;

        StringBuilder sb = new StringBuilder();

        sb.append("If you defeat the boss:\n");
        sb.append("• Coins: ").append(baseCoins).append("\n");
        sb.append("• Drop chance: ").append(dropWinPct).append("%\n\n");

        sb.append("If the boss escapes but HP ≤ 50%:\n");
        sb.append("• Coins: ").append(halfCoins).append("\n");
        sb.append("• Drop chance: ").append(dropHalfPct).append("%\n\n");

        sb.append("If the boss escapes and HP > 50%:\n");
        sb.append("• No rewards\n\n");

        sb.append("Possible drops (when drop happens):\n");
        sb.append("• Weapon (5% of drops): SWORD or BOW\n");
        sb.append("• Clothes (95% of drops):\n");
        sb.append("  - GLOVES: +10% PP\n");
        sb.append("  - SHIELD: +10% max HP\n");
        sb.append("  - BOOTS: +40% chance for 1 extra attack (next fight)");

        return sb.toString();
    }

}
