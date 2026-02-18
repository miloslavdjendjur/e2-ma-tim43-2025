package com.example.ui.shop;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.EquipmentRepository;
import com.example.data.service.EquipmentActionsService;
import com.example.data.service.EquipmentService;
import com.example.myapplication.R;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.ui.boss.BossPrepActivity;

import java.util.ArrayList;
import java.util.List;

public class ShopFragment extends Fragment {

    private RecyclerView rvShop;
    private ShopAdapter adapter;
    private TextView tvCoinsDisplay;
    private TabLayout tabLayout;

    private Button btnBackToBoss;

    private final EquipmentActionsService actions = new EquipmentActionsService();
    private final EquipmentRepository repo = new EquipmentRepository();

    private int currentLevel = 1;
    private boolean hasSword = false;
    private boolean hasBow = false;

    public ShopFragment() {
        super(R.layout.fragment_shop);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvShop = view.findViewById(R.id.rvShop);
        tvCoinsDisplay = view.findViewById(R.id.tvCoinsDisplay);
        tabLayout = view.findViewById(R.id.tabLayout);

        btnBackToBoss = view.findViewById(R.id.btnBackToBoss);

        rvShop.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new ShopAdapter(new ArrayList<>(), this::handleItemClick);
        rvShop.setAdapter(adapter);

        int startTab = 0;
        boolean isFromBossPrep = false;
        if (getArguments() != null) {
            startTab = getArguments().getInt("tab_index", 0);
            isFromBossPrep = getArguments().getBoolean("is_from_boss_prep", false);
        }

        if (isFromBossPrep) {
            btnBackToBoss.setVisibility(View.VISIBLE);
            btnBackToBoss.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), BossPrepActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        } else {
            btnBackToBoss.setVisibility(View.GONE);
        }

        TabLayout.Tab tab = tabLayout.getTabAt(startTab);
        if (tab != null) tab.select();


        loadUserDataAndRefresh(startTab);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) loadShopItems();
                else loadInventoryItems();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadUserDataAndRefresh(int tabIndex) {
        repo.getUser().addOnSuccessListener(ds -> {
            if (ds.exists()) {
                currentLevel = ds.getLong("level") != null ? ds.getLong("level").intValue() : 1;
                long coins = ds.getLong("coins") != null ? ds.getLong("coins") : 0;
                tvCoinsDisplay.setText("💰 " + coins);

                repo.getWeapons().addOnSuccessListener(weapons -> {
                    hasSword = false;
                    hasBow = false;
                    for (QueryDocumentSnapshot doc : weapons) {
                        String type = doc.getString("type");
                        int level = doc.getLong("level") != null ? doc.getLong("level").intValue() : 0;
                        if ("SWORD".equals(type) && level > 0) hasSword = true;
                        if ("BOW".equals(type) && level > 0) hasBow = true;
                    }
                    if (tabIndex == 0) loadShopItems();
                    else loadInventoryItems();
                });
            }
        });
    }

    private int getIconForType(String category, Object typeEnum) {
        if ("POTION".equals(category)) {
            return R.drawable.potion;
        }
        else if ("CLOTHES".equals(category)) {
            ClothesType ct = (ClothesType) typeEnum;
            if (ct == ClothesType.GLOVES) return R.drawable.gloves;
            if (ct == ClothesType.BOOTS) return R.drawable.boots;
            if (ct == ClothesType.SHIELD) return R.drawable.sheild;
        }
        else if ("WEAPON".equals(category)) {
            WeaponType wt = (WeaponType) typeEnum;
            if (wt == WeaponType.SWORD) return R.drawable.sword;
            if (wt == WeaponType.BOW) return R.drawable.bow;
        }
        return R.drawable.ic_launcher_foreground;
    }

    private void loadShopItems() {
        List<ShopItem> list = new ArrayList<>();

        list.add(ShopItem.createForShop("Potion (+20%)", "One time use +20% Power",
                EquipmentService.pricePotionOneShot20(currentLevel), PotionType.ONE_SHOT_PP20, "POTION",
                getIconForType("POTION", PotionType.ONE_SHOT_PP20)));

        list.add(ShopItem.createForShop("Potion (+40%)", "One time use +40% Power",
                EquipmentService.pricePotionOneShot40(currentLevel), PotionType.ONE_SHOT_PP40, "POTION",
                getIconForType("POTION", PotionType.ONE_SHOT_PP40)));

        list.add(ShopItem.createForShop("Elixir (+5%)", " +5% Power",
                EquipmentService.pricePotionPerm5(currentLevel), PotionType.PERM_PP5, "POTION",
                getIconForType("POTION", PotionType.PERM_PP5)));

        list.add(ShopItem.createForShop("Elixir (+10%)", " +10% Power",
                EquipmentService.pricePotionPerm10(currentLevel), PotionType.PERM_PP10, "POTION",
                getIconForType("POTION", PotionType.PERM_PP10)));

        list.add(ShopItem.createForShop("Gloves", "+10% Power (active for 2 fights)",
                EquipmentService.priceGloves(currentLevel), ClothesType.GLOVES, "CLOTHES",
                getIconForType("CLOTHES", ClothesType.GLOVES)));

        list.add(ShopItem.createForShop("Shield", "+10% Hit Chance",
                EquipmentService.priceShield(currentLevel), ClothesType.SHIELD, "CLOTHES",
                getIconForType("CLOTHES", ClothesType.SHIELD)));

        list.add(ShopItem.createForShop("Boots", "+40% Extra attack",
                EquipmentService.priceBoots(currentLevel), ClothesType.BOOTS, "CLOTHES",
                getIconForType("CLOTHES", ClothesType.BOOTS)));

        if (hasSword) {
            list.add(ShopItem.createForShop("Sword Sharpness", "Sword sharpness increased (+Bonus)",
                    EquipmentService.priceWeaponUpgrade(currentLevel), WeaponType.SWORD, "WEAPON",
                    getIconForType("WEAPON", WeaponType.SWORD)));
        }
        if (hasBow) {
            list.add(ShopItem.createForShop("Bow & arrows", "Bow upgrade (+Bonus)",
                    EquipmentService.priceWeaponUpgrade(currentLevel), WeaponType.BOW, "WEAPON",
                    getIconForType("WEAPON", WeaponType.BOW)));
        }

        adapter.updateList(list);
    }

    private void loadInventoryItems() {
        List<ShopItem> list = new ArrayList<>();

        repo.getPotions().addOnSuccessListener(potions -> {
            for (QueryDocumentSnapshot doc : potions) {
                String typeStr = doc.getString("type");
                int count = doc.getLong("count") != null ? doc.getLong("count").intValue() : 0;
                boolean isPending = Boolean.TRUE.equals(doc.getBoolean("pendingUse"));

                try {
                    PotionType pt = PotionType.valueOf(typeStr);
                    String desc = pt.name().contains("PERM") ? "Permanent bonus" : "For next fight";

                    if (count > 0 || isPending) {
                        list.add(ShopItem.createForInventory(formatPotionName(pt), desc, count, pt, "POTION",
                                getIconForType("POTION", pt), isPending));
                    }
                } catch (Exception e) {}
            }

            repo.getClothes().addOnSuccessListener(clothes -> {
                for (QueryDocumentSnapshot doc : clothes) {
                    String typeStr = doc.getString("type");
                    int count = doc.getLong("count") != null ? doc.getLong("count").intValue() : 0;
                    boolean active = Boolean.TRUE.equals(doc.getBoolean("active"));

                    try {
                        ClothesType ct = ClothesType.valueOf(typeStr);
                        if (count > 0 || active) {
                            list.add(ShopItem.createForInventory(formatClothesName(ct), "Active for 2 fights", count, ct, "CLOTHES",
                                    getIconForType("CLOTHES", ct), active));
                        }
                    } catch (Exception e) {}
                }

                repo.getWeapons().addOnSuccessListener(weapons -> {
                    for(QueryDocumentSnapshot doc : weapons) {
                        String typeStr = doc.getString("type");
                        int level = doc.getLong("level") != null ? doc.getLong("level").intValue() : 0;
                        if (level > 0) {
                            String name = typeStr.equals("SWORD") ? "Sword" : "Bow & arrows";
                            WeaponType wt = typeStr.equals("SWORD") ? WeaponType.SWORD : WeaponType.BOW;
                            list.add(ShopItem.createForInventory(name, "Level: " + level, 1, null, "WEAPON",
                                    getIconForType("WEAPON", wt), false));
                        }
                    }
                    adapter.updateList(list);
                });
            });
        });
    }

    private String formatPotionName(PotionType pt) {
        switch (pt) {
            case ONE_SHOT_PP20: return "Potion (+20%)";
            case ONE_SHOT_PP40: return "Potion (+40%)";
            case PERM_PP5: return "Elixir (+5%)";
            case PERM_PP10: return "Elixir (+10%)";
            default: return pt.name();
        }
    }

    private String formatClothesName(ClothesType ct) {
        switch (ct) {
            case GLOVES: return "Gloves";
            case SHIELD: return "Shield";
            case BOOTS: return "Boots";
            default: return ct.name();
        }
    }

    private void handleItemClick(ShopItem item) {
        if (item.isShopItem) {
            if ("POTION".equals(item.typeCategory)) {
                actions.buyPotion((PotionType) item.typeEnum, currentLevel)
                        .addOnSuccessListener(v -> { toast("Shopping success!"); loadUserDataAndRefresh(0); })
                        .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
            } else if ("CLOTHES".equals(item.typeCategory)) {
                actions.buyClothes((ClothesType) item.typeEnum, currentLevel)
                        .addOnSuccessListener(v -> { toast("Shopping success!"); loadUserDataAndRefresh(0); })
                        .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
            } else if ("WEAPON".equals(item.typeCategory)) {
                actions.upgradeWeapon((WeaponType) item.typeEnum, currentLevel)
                        .addOnSuccessListener(v -> { toast("Shopping success!"); loadUserDataAndRefresh(0); })
                        .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
            }
        } else {
            if ("POTION".equals(item.typeCategory)) {
                PotionType pt = (PotionType) item.typeEnum;
                if (pt == PotionType.PERM_PP5 || pt == PotionType.PERM_PP10) {
                    actions.activatePermanentPotion(pt)
                            .addOnSuccessListener(v -> { toast("Activated!"); loadInventoryItems(); })
                            .addOnFailureListener(e -> toast(e.getMessage()));
                } else {
                    actions.activateOneShotPotion(pt)
                            .addOnSuccessListener(v -> { toast("Ready for combat!"); loadInventoryItems(); })
                            .addOnFailureListener(e -> toast(e.getMessage()));
                }
            } else if ("CLOTHES".equals(item.typeCategory)) {
                actions.equipClothes((ClothesType) item.typeEnum)
                        .addOnSuccessListener(v -> { toast("Activated!"); loadInventoryItems(); })
                        .addOnFailureListener(e -> toast(e.getMessage()));
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}