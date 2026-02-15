package com.example.ui.boss;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.card.MaterialCardView;

public class FightResultActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvShakeHint;
    private ImageView ivChest;

    private MaterialCardView layoutRewards;
    private ImageView ivCoinIcon, ivDropIcon;
    private TextView tvCoins, tvDrop;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private boolean opened = false;
    private long lastShakeMs = 0;

    // --- Shake detection (linear acceleration) ---
    private final float[] gravity = new float[]{0f, 0f, 0f};
    private boolean gravityInitialized = false;

    private static final float ALPHA = 0.8f;                 // low-pass filter
    private static final float SHAKE_THRESHOLD_MS2 = 3.0f;   // emulator-friendly; raise if too sensitive (3.5-4.5)
    private static final long SHAKE_DEBOUNCE_MS = 900;

    private int coins = 0;
    private String drop = null;
    private boolean bossDefeated = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fight_result);

        bossDefeated = getIntent().getBooleanExtra("bossDefeated", false);
        coins = getIntent().getIntExtra("coinsEarned", 0);
        drop = getIntent().getStringExtra("droppedItemName");

        TextView tvTitle = findViewById(R.id.tvResultTitle);
        tvTitle.setText(bossDefeated ? "Victory!" : "Battle ended");

        tvShakeHint = findViewById(R.id.tvShakeHint);
        ivChest = findViewById(R.id.ivChest);

        layoutRewards = findViewById(R.id.layoutRewards);
        ivCoinIcon = findViewById(R.id.ivCoinsIcon);
        tvCoins = findViewById(R.id.tvCoins);

        ivDropIcon = findViewById(R.id.ivDropIcon);
        tvDrop = findViewById(R.id.tvDrop);

        Button btnOk = findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> goToProfile());

        // Initial UI state
        tvShakeHint.setText("Shake your phone to open the chest!");
        ivChest.setImageResource(R.drawable.chest_closed);
        layoutRewards.setVisibility(View.GONE);

        // Prepare rewards (texts are ready, but hidden until shake)
        ivCoinIcon.setImageResource(R.drawable.coin);
        ivDropIcon.setImageResource(R.drawable.drop);

        tvCoins.setText("Coins: " + coins);
        if (drop != null && !drop.trim().isEmpty()) tvDrop.setText("Drop: " + drop);
        else tvDrop.setText("Drop: none");

        // Sensor setup
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!opened && sensorManager != null && accelerometer != null) {
            // prevent auto-open on first couple events
            gravityInitialized = false;
            lastShakeMs = System.currentTimeMillis(); // cooldown
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (opened) return;

        // Initialize gravity baseline on first event -> avoids instant trigger
        if (!gravityInitialized) {
            gravity[0] = event.values[0];
            gravity[1] = event.values[1];
            gravity[2] = event.values[2];
            gravityInitialized = true;
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Low-pass filter (gravity)
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x;
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y;
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z;

        // High-pass (linear accel)
        float linX = x - gravity[0];
        float linY = y - gravity[1];
        float linZ = z - gravity[2];

        float linearMag = (float) Math.sqrt(linX * linX + linY * linY + linZ * linZ);

        long now = System.currentTimeMillis();
        if (linearMag > SHAKE_THRESHOLD_MS2 && (now - lastShakeMs) > SHAKE_DEBOUNCE_MS) {
            lastShakeMs = now;
            openChestAndRevealRewards();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void openChestAndRevealRewards() {
        opened = true;
        if (sensorManager != null) sensorManager.unregisterListener(this);

        tvShakeHint.setText("Opening...");

        // Play chest opening animation (oneshot)
        ivChest.setImageResource(R.drawable.chest_opening_anim);

        ivChest.post(() -> {
            long duration = 650; // fallback
            if (ivChest.getDrawable() instanceof AnimationDrawable) {
                AnimationDrawable ad = (AnimationDrawable) ivChest.getDrawable();
                ad.stop();
                ad.start();
                duration = sumAnimDuration(ad);
            }

            // After animation finishes -> show rewards
            ivChest.postDelayed(() -> {
                tvShakeHint.setText("Rewards unlocked!");
                revealRewards();
            }, Math.max(300, duration));
        });
    }

    private void revealRewards() {
        layoutRewards.setVisibility(View.VISIBLE);
        layoutRewards.setAlpha(0f);
        layoutRewards.setScaleX(0.96f);
        layoutRewards.setScaleY(0.96f);

        layoutRewards.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private long sumAnimDuration(AnimationDrawable ad) {
        long sum = 0;
        for (int i = 0; i < ad.getNumberOfFrames(); i++) {
            sum += ad.getDuration(i);
        }
        return sum;
    }

    private void goToProfile() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("openTab", "profile");
        startActivity(i);
        finish();
    }
}
