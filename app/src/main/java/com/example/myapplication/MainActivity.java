package com.example.myapplication;

import android.content.Context;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.data.service.AllianceService;
import com.example.ui.auth.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        Intent serviceIntent = new Intent(this, AllianceService.class);
        startService(serviceIntent);

        bottomNav = findViewById(R.id.bottom_nav);

        NavHostFragment navHost =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        if (navHost == null) return;

        NavController navController = navHost.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Provera kada je aplikacija već otvorena u pozadini
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "profile".equals(intent.getStringExtra("openTab"))) {
            if (bottomNav != null) {
                // Označava profil u donjoj navigaciji, što menja fragment
                bottomNav.setSelectedItemId(R.id.nav_profile);
            }
        }
    }

    public static void navigateToProfile(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.putExtra("openTab", "profile");
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }
}