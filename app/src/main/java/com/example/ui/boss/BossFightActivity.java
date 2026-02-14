package com.example.ui.boss;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.boss.Boss;
import com.example.data.model.boss.FightResult;
import com.example.data.repo.EquipmentRepository;
import com.example.data.service.BossService;
import com.example.data.service.EquipmentService;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;

import java.util.Locale;
import java.util.Random;

public class BossFightActivity extends AppCompatActivity {

    private final BossService bossService = new BossService();
    private final EquipmentRepository equipmentRepo = new EquipmentRepository();
    private final EquipmentService equipmentService = new EquipmentService();
    private final Random random = new Random();

    private TextView tvBossLevel, tvBossHpText, tvUserPp, tvChance, tvCombatLog;
    private ProgressBar pbBossHp;
    private ImageView ivBoss;
    private LinearLayout layoutAttempts;
    private Button btnAttack;

    private Boss boss;

    private int bossLevel = 1;
    private int effectivePp = 0;
    private int hitBonusPct = 0;
    private int extraTryPct = 0;

    private double successRatePct = 0.0; // 0..100
    private int maxAttacksThisBattle = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boss_fight);

        tvBossLevel = findViewById(R.id.tvBossLevel);
        tvBossHpText = findViewById(R.id.tvBossHpText);
        pbBossHp = findViewById(R.id.pbBossHp);
        ivBoss = findViewById(R.id.ivBoss);
        tvCombatLog = findViewById(R.id.tvCombatLog);

        tvUserPp = findViewById(R.id.tvUserPp);
        tvChance = findViewById(R.id.tvChance);
        layoutAttempts = findViewById(R.id.layoutAttempts);
        btnAttack = findViewById(R.id.btnAttack);

        bossLevel = getIntent().getIntExtra("bossLevel", 1);
        effectivePp = getIntent().getIntExtra("effectivePp", 0);
        hitBonusPct = getIntent().getIntExtra("hitBonusPct", 0);
        extraTryPct = getIntent().getIntExtra("extraTryPct", 0);

        btnAttack.setEnabled(false);
        btnAttack.setOnClickListener(v -> doAttack());

        loadAndStartBattle();
    }

    private void loadAndStartBattle() {
        equipmentRepo.getUser()
                .addOnSuccessListener(userDoc -> {
                    Long lvl = userDoc.getLong("level");
                    bossLevel = (lvl != null) ? lvl.intValue() : bossLevel;
                    Timestamp lastLevelUp = userDoc.getTimestamp("lastLevelUpDate");

                    equipmentService.computeEffectiveStats(userDoc)
                            .addOnSuccessListener(stats -> {
                                effectivePp = stats.effectivePp;
                                hitBonusPct = stats.hitBonusPct;
                                extraTryPct = stats.extraTryPct;

                                bossService.calculateTaskSuccessRate(lastLevelUp)
                                        .addOnSuccessListener(rate -> {
                                            successRatePct = clamp(rate + hitBonusPct, 0.0, 100.0);
                                            startOrResumeBoss();
                                        })
                                        .addOnFailureListener(e -> {
                                            successRatePct = clamp(0.0 + hitBonusPct, 0.0, 100.0);
                                            startOrResumeBoss();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to load stats", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void startOrResumeBoss() {
        int attacks = 5;
        if (random.nextInt(100) < extraTryPct) attacks += 1;
        maxAttacksThisBattle = attacks;

        bossService.getBossForBattle(bossLevel, attacks)
                .addOnSuccessListener(b -> {
                    boss = b;
                    bindUi(true);
                    btnAttack.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load boss", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindUi(boolean animateHp) {
        tvBossLevel.setText("Boss (level " + boss.getLevel() + ")");
        tvBossHpText.setText(String.format(Locale.US, "HP: %d / %d", boss.getCurrentHp(), boss.getMaxHp()));

        tvUserPp.setText("Your PP: " + effectivePp);
        tvChance.setText(String.format(Locale.US, "Hit chance: %.0f%%", successRatePct));

        // HP bar
        int pct = (boss.getMaxHp() <= 0) ? 0 : (int) Math.round((boss.getCurrentHp() * 100.0) / boss.getMaxHp());
        pct = Math.max(0, Math.min(100, pct));
        if (animateHp) animateProgress(pbBossHp, pbBossHp.getProgress(), pct);
        else pbBossHp.setProgress(pct);

        // attempts orbs
        renderAttemptsOrbs(boss.getAttacksLeft(), maxAttacksThisBattle);
    }

    private void renderAttemptsOrbs(int left, int total) {
        layoutAttempts.removeAllViews();
        for (int i = 0; i < total; i++) {
            ImageView orb = new ImageView(this);
            orb.setImageResource(R.drawable.ic_orb);
            int size = dp(18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = dp(6);
            orb.setLayoutParams(lp);
            orb.setAlpha(i < left ? 1.0f : 0.25f);
            layoutAttempts.addView(orb);
        }
    }

    private void doAttack() {
        if (boss == null) return;
        if (boss.isDefeated() || boss.getAttacksLeft() <= 0) return;

        btnAttack.setEnabled(false);

        int roll = random.nextInt(101); // 0..100
        bossService.performAttack(boss, effectivePp, successRatePct, roll)
                .addOnSuccessListener(hit -> {
                    tvCombatLog.setText(hit ? "Hit!" : "Miss!");
                    if (hit) bossHitAnim();
                    else bossMissAnim();

                    bindUi(true);

                    if (boss.isDefeated() || boss.getAttacksLeft() <= 0) {
                        endBattle();
                    } else {
                        btnAttack.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    btnAttack.setEnabled(true);
                    Toast.makeText(this, "Attack failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void endBattle() {
        btnAttack.setEnabled(false);
        tvCombatLog.setText("Resolving rewards...");

        bossService.resolveBattleRewards(boss)
                .addOnSuccessListener(result -> {
                    // consume one-shot potion + one use of active clothes
                    equipmentRepo.consumeOneShotPotionsIfAny();
                    equipmentRepo.consumeAllActiveClothesOneUse();

                    openResult(result);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to resolve rewards", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void openResult(FightResult r) {
        Intent i = new Intent(this, FightResultActivity.class);
        i.putExtra("bossDefeated", r.bossDefeated);
        i.putExtra("coinsEarned", r.coinsEarned);
        i.putExtra("droppedItemName", r.droppedItemName);
        i.putExtra("isWeapon", r.isWeapon);
        startActivity(i);
        finish();
    }

    // --- Animations ---
    private void bossHitAnim() {
        TranslateAnimation shake = new TranslateAnimation(-8, 8, 0, 0);
        shake.setDuration(120);
        shake.setRepeatCount(3);
        shake.setRepeatMode(TranslateAnimation.REVERSE);
        ivBoss.startAnimation(shake);
    }

    private void bossMissAnim() {
        ivBoss.animate().translationXBy(10f).setDuration(80).withEndAction(() ->
                ivBoss.animate().translationXBy(-10f).setDuration(80).start()
        ).start();
    }

    private void animateProgress(ProgressBar pb, int from, int to) {
        ObjectAnimator anim = ObjectAnimator.ofInt(pb, "progress", from, to);
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
