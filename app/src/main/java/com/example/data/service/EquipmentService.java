package com.example.data.service;

import androidx.annotation.NonNull;

import com.example.data.repo.EquipmentRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class EquipmentService {

    private final EquipmentRepository repo = new EquipmentRepository();

    public static int bossRewardForLevel(int level) {
        if (level <= 1) return 200;
        double r = 200.0;
        for (int i = 2; i <= level; i++) r *= 1.2;
        return (int)Math.round(r);
    }

    public static int pricePotionOneShot20(int level) { // 50%
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 0.5);
    }
    public static int pricePotionOneShot40(int level) { // 70%
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 0.7);
    }
    public static int pricePotionPerm5(int level) { // 200%
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 2.0);
    }
    public static int pricePotionPerm10(int level) { // 1000%
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 10.0);
    }

    public static int priceGloves(int level) {
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 0.6);
    }
    public static int priceShield(int level) {
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 0.6);
    }
    public static int priceBoots(int level) {
        int prev = Math.max(1, level - 1);
        return (int)Math.round(bossRewardForLevel(prev) * 0.8);
    }

    public static int priceWeaponUpgrade(int levelNextBoss) {
        return (int)Math.round(bossRewardForLevel(levelNextBoss) * 0.6);
    }
    public static class EffectiveCombatStats {
        public final int effectivePp;
        public final int hitBonusPct;
        public final int extraTryPct;
        public EffectiveCombatStats(int pp, int hit, int extra){ this.effectivePp=pp; this.hitBonusPct=hit; this.extraTryPct=extra; }
    }

    private static int roundInt(double d){ return (int)Math.round(d); }

    public Task<EffectiveCombatStats> computeEffectiveStats(@NonNull DocumentSnapshot userDoc) {
        final long basePp = (userDoc.getLong("pp") != null) ? userDoc.getLong("pp") : 0L;
        // final int level = (userDoc.getLong("level") != null) ? userDoc.getLong("level").intValue() : 1; // trenutno ne koristimo

        // pokupi tri kolekcije paralelno
        Task<QuerySnapshot> tWeapons = repo.getWeapons();
        Task<QuerySnapshot> tClothes = repo.getClothes();
        Task<QuerySnapshot> tPotions = repo.getPotions();

        return Tasks.whenAllSuccess(tWeapons, tClothes, tPotions)
                .continueWith(task -> {
                    @SuppressWarnings("unchecked")
                    QuerySnapshot weaponsSnap = (QuerySnapshot) task.getResult().get(0);
                    QuerySnapshot clothesSnap = (QuerySnapshot) task.getResult().get(1);
                    QuerySnapshot potionsSnap = (QuerySnapshot) task.getResult().get(2);

                    double ppMul = 1.0;
                    for (QueryDocumentSnapshot d : weaponsSnap) {
                        String type = d.getString("type");
                        if ("SWORD".equals(type)) {
                            ppMul *= 1.05;
                        }
                    }

                    int glovesPct = 0, shieldPct = 0, bootsPct = 0;
                    for (QueryDocumentSnapshot d : clothesSnap) {
                        Boolean active = d.getBoolean("active");
                        Long usesLeft = d.getLong("usesLeft");
                        if (active == null || !active) continue;
                        if (usesLeft == null || usesLeft <= 0) continue;

                        String type = d.getString("type");
                        int stacked = (d.getLong("stackedPercent") != null) ? d.getLong("stackedPercent").intValue() : 0;
                        if ("GLOVES".equals(type)) glovesPct += stacked;
                        if ("SHIELD".equals(type)) shieldPct += stacked;
                        if ("BOOTS".equals(type))  bootsPct  += stacked;
                    }

                    int oneShotBonusPct = 0;
                    for (QueryDocumentSnapshot d : potionsSnap) {
                        Boolean pending = d.getBoolean("pendingUse");
                        if (pending != null && pending) {
                            String type = d.getString("type");
                            if ("ONE_SHOT_PP20".equals(type)) oneShotBonusPct += 20;
                            if ("ONE_SHOT_PP40".equals(type)) oneShotBonusPct += 40;
                        }
                    }

                    double pp = basePp;
                    pp *= ppMul;
                    pp *= (1.0 + glovesPct / 100.0);
                    pp *= (1.0 + oneShotBonusPct / 100.0);

                    return new EffectiveCombatStats((int)Math.round(pp), shieldPct, bootsPct);
                });
    }

    public EquipmentRepository repo() { return repo; }
}
