package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.example.ui.home.HomeFragment;
import com.example.ui.profile.ProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private final Fragment home = new HomeFragment();
    private Fragment current = home;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, home)
                .commit();

        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchTo(home);
                return true;

            } else if (id == R.id.nav_profile) {
                // ➜ otvori ProfileActivity
                startActivity(new Intent(this, ProfileActivity.class));
                // ostavi selektovan Home tab (bolji UX po povratku)
                bottom.setSelectedItemId(R.id.nav_home);
                return false; // ne menjamo tab u ovoj aktivnosti

            } else if (id == R.id.nav_tasks) {
                // TODO: kad dodaš TasksFragment, prebaci se na njega
                // switchTo(tasks);
                return true;
            }
            return false;
        });
        bottom.setSelectedItemId(R.id.nav_home);
    }

    private void switchTo(Fragment target) {
        if (current == target) return;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, target)
                .commit();
        current = target;
    }
}