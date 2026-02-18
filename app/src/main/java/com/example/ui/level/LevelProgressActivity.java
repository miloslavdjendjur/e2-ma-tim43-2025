package com.example.ui.level;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.TitleBook;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.data.service.LevelingService;
import com.example.myapplication.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LevelProgressActivity extends AppCompatActivity {

    private TextView tvLevel, tvTitle, tvXp, tvNext, tvPp;
    private ProgressBar progress;
    private Button btnDebugAddXp;

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

        // DEMO: dodaj 120 XP koristeći istu logiku kao i TaskService
        btnDebugAddXp.setOnClickListener(v -> addXpDemo(120));
    }

    private void load() {
        repo.getCurrentUser().addOnSuccessListener(this::applyUser);
    }

    private void applyUser(DocumentSnapshot ds) {
        User u = ds.toObject(User.class);
        if (u == null) return;

        int threshold = LevelingService.getThresholdForLevel(u.level);

        tvLevel.setText("Level " + u.level);
        tvTitle.setText(TitleBook.titleFor(u.level));
        tvXp.setText("XP: " + u.xp);
        tvNext.setText("Next level: " + threshold);
        tvPp.setText("PP: " + u.pp);

        int prog = (threshold == 0) ? 0 : (int) Math.max(0, Math.min(100, (u.xp * 100L / threshold)));
        progress.setProgress(prog);
    }

    /**
     * DEMO: Simulira dodavanje XP-a (kao da je završen task)
     * Ali radi direktno na bazi (bez task check-a), čisto za testiranje Levelinga.
     */
    private void addXpDemo(int gained) {
        repo.getCurrentUser().addOnSuccessListener(ds -> {
            User u = ds.toObject(User.class);
            if (u == null) return;

            // Koristimo servisnu logiku!
            boolean leveledUp = LevelingService.addXp(u, gained);

            // Čuvamo nazad u bazu
            FirebaseFirestore.getInstance()
                    .collection("users").document(u.uid)
                    .set(u) // .set overwrite-uje, ili koristi .update ako želiš parcijalno
                    .addOnSuccessListener(v -> {
                        load();
                        if (leveledUp) {
                            Toast.makeText(this, "Level Up! Novi nivo: " + u.level, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Dodato " + gained + " XP", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}