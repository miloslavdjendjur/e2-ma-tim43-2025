package com.example.ui.equipment;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.EquipmentRepository;
import com.example.data.service.EquipmentActionsService;
import com.example.data.service.EquipmentActionsService;
import com.example.data.model.equipment.*;
import com.example.data.service.SpecialMissionService;
import com.example.myapplication.R;

public class EquipmentStoreActivity extends AppCompatActivity {

    private final EquipmentActionsService actions = new EquipmentActionsService();
    private final EquipmentRepository repo = new EquipmentRepository();
    private final SpecialMissionService specialMissionService = new SpecialMissionService();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equipment_store);

        Button btnBuyP20 = findViewById(R.id.btnBuyP20);
        Button btnBuyP40 = findViewById(R.id.btnBuyP40);
        Button btnBuyPerm5 = findViewById(R.id.btnBuyPerm5);
        Button btnBuyPerm10= findViewById(R.id.btnBuyPerm10);

        Button btnBuyGloves = findViewById(R.id.btnBuyGloves);
        Button btnBuyShield = findViewById(R.id.btnBuyShield);
        Button btnBuyBoots  = findViewById(R.id.btnBuyBoots);

        Button btnUpgradeSword = findViewById(R.id.btnUpgradeSword);
        Button btnUpgradeBow   = findViewById(R.id.btnUpgradeBow);

        repo.getUser().addOnSuccessListener(ds -> {
            int level = ds.getLong("level") != null ? ds.getLong("level").intValue() : 1;

            btnBuyP20.setOnClickListener(v -> actions.buyPotion(PotionType.ONE_SHOT_PP20, level)
                    .addOnSuccessListener(x -> toast("Potion acquired +20% Power"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyP40.setOnClickListener(v -> actions.buyPotion(PotionType.ONE_SHOT_PP40, level)
                    .addOnSuccessListener(x -> toast("Potion acquired +40% Power"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyPerm5.setOnClickListener(v -> actions.buyPotion(PotionType.PERM_PP5, level)
                    .addOnSuccessListener(x -> toast("+5% Permanent power applied"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyPerm10.setOnClickListener(v -> actions.buyPotion(PotionType.PERM_PP10, level)
                    .addOnSuccessListener(x -> toast("+10% Permanent power applied"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyGloves.setOnClickListener(v -> actions.buyClothes(ClothesType.GLOVES, level)
                    .addOnSuccessListener(x -> toast("Gloves acquired"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyShield.setOnClickListener(v -> actions.buyClothes(ClothesType.SHIELD, level)
                    .addOnSuccessListener(x -> toast("Shield acquired"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnBuyBoots.setOnClickListener(v -> actions.buyClothes(ClothesType.BOOTS, level)
                    .addOnSuccessListener(x -> toast("Boots acquired"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnUpgradeSword.setOnClickListener(v -> actions.upgradeWeapon(WeaponType.SWORD, level)
                    .addOnSuccessListener(x -> toast("Sword upgraded +prob"))
                    .addOnFailureListener(e -> toast(e.getMessage())));

            btnUpgradeBow.setOnClickListener(v -> actions.upgradeWeapon(WeaponType.BOW, level)
                    .addOnSuccessListener(x -> toast("Bow upgraded +prob"))
                    .addOnFailureListener(e -> toast(e.getMessage())));
        });
    }

    private void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
