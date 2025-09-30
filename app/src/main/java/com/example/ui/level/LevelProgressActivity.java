package com.example.ui.level;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.data.service.LevelingService;
import com.example.data.model.TitleBook;
import com.example.myapplication.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LevelProgressActivity extends AppCompatActivity {

    private TextView tvLevel, tvTitle, tvXp, tvNext, tvPp;
    private ProgressBar progress;
    private Button btnDebugAddXp; // DEMO

    private final UserRepository repo = new UserRepository();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level_progress);

        tvLevel   = findViewById(R.id.tvLevel);
        tvTitle   = findViewById(R.id.tvTitle);
        tvXp      = findViewById(R.id.tvXp);
        tvNext    = findViewById(R.id.tvNext);
        tvPp      = findViewById(R.id.tvPp);
        progress  = findViewById(R.id.progressXp);
        btnDebugAddXp = findViewById(R.id.btnDebugAddXp);

        load();

        btnDebugAddXp.setOnClickListener(v -> addXpDemo(120)); // dodaj 120 XP
    }

    private void load() {
        repo.getCurrentUser().addOnSuccessListener(this::applyUser);
    }

    private void applyUser(DocumentSnapshot ds) {
        User u = ds.toObject(User.class);
        if (u == null) return;

        int threshold = 200;
        for (int i = 1; i < u.level; i++) {
            threshold = LevelingService.nextXpThreshold(threshold);
        }

        tvLevel.setText("Nivo " + u.level);
        tvTitle.setText(TitleBook.titleFor(u.level));
        tvXp.setText("XP: " + u.xp);
        tvNext.setText("Sledeći prag: " + threshold);
        tvPp.setText("PP: " + u.pp);

        int prog = (threshold == 0) ? 0 : (int) Math.max(0, Math.min(100, (u.xp * 100 / threshold)));
        progress.setProgress(prog);
    }

    /** DEMO: dodaj XP */
    private void addXpDemo(long gained) {
        repo.getCurrentUser().addOnSuccessListener(ds -> {
            User u = ds.toObject(User.class);
            if (u == null) return;

            int threshold = 200;
            for (int i = 1; i < u.level; i++) {
                threshold = LevelingService.nextXpThreshold(threshold);
            }

            long xp = u.xp + gained;

            while (xp >= threshold) {
                xp -= threshold;
                u.level++;

                if (u.pp == 0) {
                    u.pp = 40;
                } else {
                    u.pp = LevelingService.nextPp(u.pp);
                }

                u.title = TitleBook.titleFor(u.level);
                threshold = LevelingService.nextXpThreshold(threshold);

            }
            u.xp = xp;

            FirebaseFirestore.getInstance()
                    .collection("users").document(u.uid)
                    .update("xp", u.xp, "level", u.level, "pp", u.pp, "title", u.title)
                    .addOnSuccessListener(v -> load());
        });
    }
}
