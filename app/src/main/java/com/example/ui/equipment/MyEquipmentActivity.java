package com.example.ui.equipment;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.service.EquipmentActionsService;
import com.example.data.model.equipment.*;
import com.example.data.service.EquipmentService.EffectiveCombatStats;
import com.example.data.service.EquipmentService;
import com.example.data.repo.EquipmentRepository;
import com.example.myapplication.R;

public class MyEquipmentActivity extends AppCompatActivity {

    private final EquipmentActionsService actions = new EquipmentActionsService();
    private final EquipmentRepository repo  = new EquipmentRepository();
    private final EquipmentService service  = new EquipmentService();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_equipment);

        Button btnUseP20 = findViewById(R.id.btnUseP20);
        Button btnUseP40 = findViewById(R.id.btnUseP40);
        Button btnEquipGloves = findViewById(R.id.btnEquipGloves);
        Button btnEquipShield = findViewById(R.id.btnEquipShield);
        Button btnEquipBoots  = findViewById(R.id.btnEquipBoots);
        Button btnStats       = findViewById(R.id.btnStats);
        Button btnAfterBattle = findViewById(R.id.btnAfterBattle);

        btnUseP20.setOnClickListener(v -> actions.activateOneShotPotion(PotionType.ONE_SHOT_PP20)
                .addOnSuccessListener(x -> toast("Napitak +20% spreman (potrošiće se u prvoj sledećoj borbi)."))
                .addOnFailureListener(e -> toast(e.getMessage())));

        btnUseP40.setOnClickListener(v -> actions.activateOneShotPotion(PotionType.ONE_SHOT_PP40)
                .addOnSuccessListener(x -> toast("Napitak +40% spreman."))
                .addOnFailureListener(e -> toast(e.getMessage())));

        btnEquipGloves.setOnClickListener(v -> actions.equipClothes(ClothesType.GLOVES)
                .addOnSuccessListener(x -> toast("Rukavice aktivirane (+10% PP, 2 borbe)."))
                .addOnFailureListener(e -> toast(e.getMessage())));

        btnEquipShield.setOnClickListener(v -> actions.equipClothes(ClothesType.SHIELD)
                .addOnSuccessListener(x -> toast("Štit aktiviran (+10% hit, 2 borbe)."))
                .addOnFailureListener(e -> toast(e.getMessage())));

        btnEquipBoots.setOnClickListener(v -> actions.equipClothes(ClothesType.BOOTS)
                .addOnSuccessListener(x -> toast("Čizme aktivirane (+40% extra try, 2 borbe)."))
                .addOnFailureListener(e -> toast(e.getMessage())));

        btnStats.setOnClickListener(v -> repo.getUser().addOnSuccessListener(ds ->
                service.computeEffectiveStats(ds)
                        .addOnSuccessListener(s -> toast("PP=" + s.effectivePp + ", hit+=" + s.hitBonusPct + "%, extra+=" + s.extraTryPct + "%"))
                        .addOnFailureListener(e -> toast(e.getMessage()))
        ));

        btnAfterBattle.setOnClickListener(v ->
                actions.afterBattleConsume()
                        .addOnSuccessListener(x -> toast("Potrošeno: jednokratni napici i -1 uses za odeću."))
                        .addOnFailureListener(e -> toast(e.getMessage()))
        );
    }

    private void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
