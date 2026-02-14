package com.example.ui.boss;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;

public class FightResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fight_result);

        boolean bossDefeated = getIntent().getBooleanExtra("bossDefeated", false);
        int coins = getIntent().getIntExtra("coinsEarned", 0);
        String drop = getIntent().getStringExtra("droppedItemName");
        boolean isWeapon = getIntent().getBooleanExtra("isWeapon", false);

        TextView tvTitle = findViewById(R.id.tvResultTitle);
        ImageView ivChest = findViewById(R.id.ivChest);
        TextView tvCoins = findViewById(R.id.tvCoins);
        TextView tvDrop = findViewById(R.id.tvDrop);
        Button btnOk = findViewById(R.id.btnOk);

        tvTitle.setText(bossDefeated ? "Victory!" : "Battle ended");

        // chest open “feel” (simple scale pop)
        ivChest.setScaleX(0.92f);
        ivChest.setScaleY(0.92f);
        ivChest.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220).start();

        // (placeholder) swap closed->open after a beat
        ivChest.postDelayed(() -> ivChest.setImageResource(R.drawable.chest_open_placeholder), 220);

        tvCoins.setText("Coins: " + coins);

        if (drop != null && !drop.isEmpty()) {
            tvDrop.setText("Drop: " + drop + (isWeapon ? " (weapon)" : " (clothes)"));
        } else {
            tvDrop.setText("Drop: none");
        }

        btnOk.setOnClickListener(v -> finish());
    }
}
