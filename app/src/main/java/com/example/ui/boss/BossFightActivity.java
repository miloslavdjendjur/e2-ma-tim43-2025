package com.example.ui.boss;

import android.animation.ObjectAnimator;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
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
    private ImageView ivBoss, ivHurt;
    private LinearLayout layoutAttempts;
    private Button btnAttack;

    private Boss boss;

    private int bossLevel = 1;
    private int effectivePp = 0;
    private int hitBonusPct = 0;
    private int extraTryPct = 0;

    private double successRatePct = 0.0;
    private int maxAttacksThisBattle = 5;

    private AnimationDrawable bossAnim;

    // We keep refs to callbacks so we can cancel them (prevents animation overlap)
    private Runnable resumeAfterAnimRunnable;
    private Runnable endBattleRunnable;
    private Runnable hideOverlayRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boss_fight);

        tvBossLevel = findViewById(R.id.tvBossLevel);
        tvBossHpText = findViewById(R.id.tvBossHpText);
        pbBossHp = findViewById(R.id.pbBossHp);

        ivBoss = findViewById(R.id.ivBoss);
        ivHurt = findViewById(R.id.ivHurt);

        tvCombatLog = findViewById(R.id.tvCombatLog);
        tvUserPp = findViewById(R.id.tvUserPp);
        tvChance = findViewById(R.id.tvChance);
        layoutAttempts = findViewById(R.id.layoutAttempts);
        btnAttack = findViewById(R.id.btnAttack);

        // Pixel-art feel
        ivBoss.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivBoss.setAdjustViewBounds(true);

        if (ivHurt != null) {
            ivHurt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivHurt.setAdjustViewBounds(true);
            ivHurt.setVisibility(View.GONE);
        }

        // extras from prep
        bossLevel = getIntent().getIntExtra("bossLevel", 1);
        effectivePp = getIntent().getIntExtra("effectivePp", 0);
        hitBonusPct = getIntent().getIntExtra("hitBonusPct", 0);
        extraTryPct = getIntent().getIntExtra("extraTryPct", 0);

        // Start idle immediately
        playBossAnim(R.drawable.boss_idle_anim);

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
        if (boss == null) return;

        tvBossLevel.setText("Boss (level " + boss.getLevel() + ")");
        tvBossHpText.setText(String.format(Locale.US, "HP: %d / %d", boss.getCurrentHp(), boss.getMaxHp()));

        tvUserPp.setText("Your PP: " + effectivePp);
        tvChance.setText(String.format(Locale.US, "Hit chance: %.0f%%", successRatePct));

        int pct = (boss.getMaxHp() <= 0) ? 0 : (int) Math.round((boss.getCurrentHp() * 100.0) / boss.getMaxHp());
        pct = Math.max(0, Math.min(100, pct));

        if (animateHp) animateProgress(pbBossHp, pbBossHp.getProgress(), pct);
        else pbBossHp.setProgress(pct);

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
        cancelPendingUiCallbacks();

        int roll = random.nextInt(101); // 0..100
        bossService.performAttack(boss, effectivePp, successRatePct, roll)
                .addOnSuccessListener(hit -> {
                    tvCombatLog.setText(hit ? "Hit!" : "Miss!");

                    if (hit) {
                        // Overlay hurt (stops/hides idle underneath), then return to idle/death
                        playOverlayAnim(R.drawable.boss_hurt_anim, () -> {
                            if (boss.isDefeated()) {
                                tvCombatLog.setText("Boss defeated!");
                                long deathMs = playBossAnim(R.drawable.boss_death_anim);
                                endBattleRunnable = this::endBattle;
                                ivBoss.postDelayed(endBattleRunnable, Math.max(250, deathMs + 30));
                            } else {
                                playBossAnim(R.drawable.boss_idle_anim);
                                if (boss.getAttacksLeft() > 0) btnAttack.setEnabled(true);
                            }
                        });

                    } else {
                        // MISS on boss (no overlay), then idle
                        long missMs = playBossAnim(R.drawable.boss_miss_anim);
                        resumeAfterAnimRunnable = () -> {
                            playBossAnim(R.drawable.boss_idle_anim);

                            if (boss.getAttacksLeft() <= 0) {
                                tvCombatLog.setText("No attempts left.");
                                endBattle();
                            } else {
                                btnAttack.setEnabled(true);
                            }
                        };
                        ivBoss.postDelayed(resumeAfterAnimRunnable, Math.max(250, missMs + 30));
                    }

                    bindUi(true);

                    // If no attempts left after this action, end battle (don’t interrupt animation)
                    if (boss.getAttacksLeft() <= 0 && !boss.isDefeated()) {
                        endBattleRunnable = () -> {
                            if (!boss.isDefeated()) endBattle();
                        };
                        ivBoss.postDelayed(endBattleRunnable, 650);
                    }
                })
                .addOnFailureListener(e -> {
                    btnAttack.setEnabled(true);
                    Toast.makeText(this, "Attack failed", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Plays an overlay AnimationDrawable on ivHurt while hiding/stopping ivBoss underneath.
     * This fixes the "idle still visible during hurt/miss" issue when frames have transparency.
     */
    private void playOverlayAnim(@DrawableRes int overlayAnimRes, @Nullable Runnable onOverlayFinished) {
        if (ivHurt == null) {
            // fallback: just play on boss directly
            long ms = playBossAnim(overlayAnimRes);
            resumeAfterAnimRunnable = onOverlayFinished;
            ivBoss.postDelayed(resumeAfterAnimRunnable, Math.max(250, ms + 30));
            return;
        }

        // stop idle under it + hide
        stopBossAnim();
        ivBoss.setVisibility(View.INVISIBLE);

        ivHurt.setVisibility(View.VISIBLE);
        ivHurt.setImageResource(overlayAnimRes);

        long overlayMs = startAndGetAnimDuration(ivHurt);

        hideOverlayRunnable = () -> {
            ivHurt.setVisibility(View.GONE);
            ivBoss.setVisibility(View.VISIBLE);
            if (onOverlayFinished != null) onOverlayFinished.run();
        };
        ivHurt.postDelayed(hideOverlayRunnable, Math.max(250, overlayMs + 30));
    }

    private long playBossAnim(@DrawableRes int animRes) {
        ivBoss.setImageResource(animRes);
        bossAnim = null;

        Drawable d = ivBoss.getDrawable();
        if (d instanceof AnimationDrawable) {
            bossAnim = (AnimationDrawable) d;
            bossAnim.stop();
            bossAnim.start();
            return sumAnimDuration(bossAnim);
        }
        return 0L;
    }

    private void stopBossAnim() {
        if (bossAnim != null) {
            bossAnim.stop();
        }
    }

    private long startAndGetAnimDuration(ImageView iv) {
        Drawable d = iv.getDrawable();
        if (d instanceof AnimationDrawable) {
            AnimationDrawable ad = (AnimationDrawable) d;
            ad.stop();
            ad.start();
            return sumAnimDuration(ad);
        }
        return 0L;
    }

    private long sumAnimDuration(AnimationDrawable ad) {
        long sum = 0L;
        for (int i = 0; i < ad.getNumberOfFrames(); i++) {
            sum += ad.getDuration(i);
        }
        return sum;
    }

    private void cancelPendingUiCallbacks() {
        if (resumeAfterAnimRunnable != null) {
            ivBoss.removeCallbacks(resumeAfterAnimRunnable);
            resumeAfterAnimRunnable = null;
        }
        if (endBattleRunnable != null) {
            ivBoss.removeCallbacks(endBattleRunnable);
            endBattleRunnable = null;
        }
        if (hideOverlayRunnable != null && ivHurt != null) {
            ivHurt.removeCallbacks(hideOverlayRunnable);
            hideOverlayRunnable = null;
        }
    }

    private void endBattle() {
        btnAttack.setEnabled(false);
        tvCombatLog.setText("Resolving rewards...");

        bossService.resolveBattleRewards(boss)
                .addOnSuccessListener(result -> {
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
        android.content.Intent i = new android.content.Intent(this, FightResultActivity.class);
        i.putExtra("bossDefeated", r.bossDefeated);
        i.putExtra("coinsEarned", r.coinsEarned);
        i.putExtra("droppedItemName", r.droppedItemName);
        i.putExtra("isWeapon", r.isWeapon);
        startActivity(i);
        finish();
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
