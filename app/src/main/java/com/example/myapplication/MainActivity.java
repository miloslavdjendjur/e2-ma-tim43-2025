package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.data.repo.AuthRepository;
import com.example.ui.auth.LoginActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.google.firebase.auth.FirebaseUser u =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        if (u == null) {
            startActivity(new Intent(this, com.example.ui.auth.LoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Logout
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            new AuthRepository().logout();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        // Profile
        Button btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, com.example.ui.profile.ProfileActivity.class)));

        // Level
        Button btnLevel = findViewById(R.id.btnLevel);
        btnLevel.setOnClickListener(v ->
                startActivity(new Intent(this, com.example.ui.level.LevelProgressActivity.class)));

        // Categories
        Button btnCategories = findViewById(R.id.btnCategories);
        btnCategories.setOnClickListener(v ->
                startActivity(new Intent(this, com.example.ui.category.CategoryActivity.class)));

        // Create Task
        Button btnCreateTask = findViewById(R.id.btnCreateTask);
        btnCreateTask.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.ui.task.CreateTaskActivity.class);
            startActivity(intent);
        });

        // View Calendar Tasks (kalendar stranica)
        Button btnViewTasks = findViewById(R.id.btnViewTasks);
        btnViewTasks.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.ui.task.TasksActivity.class);
            startActivity(intent);
        });

        // All Tasks (lista sa filterom)
        Button btnAllTasks = findViewById(R.id.btnAllTasks);
        btnAllTasks.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, com.example.ui.task.AllTasksActivity.class);
            startActivity(i);
        });

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
