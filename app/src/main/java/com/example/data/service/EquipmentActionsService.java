package com.example.data.service;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.EquipmentRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class EquipmentActionsService {
    private final EquipmentRepository repo = new EquipmentRepository();

    // --- POTIONS ---

    public Task<Void> buyPotion(PotionType type, int userLevel) {
        int price;
        if (type == PotionType.ONE_SHOT_PP20) price = EquipmentService.pricePotionOneShot20(userLevel);
        else if (type == PotionType.ONE_SHOT_PP40) price = EquipmentService.pricePotionOneShot40(userLevel);
        else if (type == PotionType.PERM_PP5) price = EquipmentService.pricePotionPerm5(userLevel);
        else if (type == PotionType.PERM_PP10) price = EquipmentService.pricePotionPerm10(userLevel);
        else throw new IllegalArgumentException("Nepoznat tip: " + type);

        return repo.getUser().onSuccessTask(ds -> {
            int coins = ds.getLong("coins") != null ? ds.getLong("coins").intValue() : 0;
            if (coins < price) throw new IllegalStateException("Nedovoljno novčića!");

            var batch = ds.getReference().getFirestore().batch();
            batch.update(ds.getReference(), "coins", coins - price);

            return batch.commit().onSuccessTask(v -> {
                return repo.addPotion(type, 1);
            });
        });
    }

    public Task<Void> activateOneShotPotion(PotionType type) {
        return repo.setPotionPendingUse(type, true);
    }

    public Task<Void> activatePermanentPotion(PotionType type) {
        if (type != PotionType.PERM_PP5 && type != PotionType.PERM_PP10)
            throw new IllegalArgumentException("Nije trajni napitak!");

        return repo.getUser().onSuccessTask(userSnap -> {
            DocumentReference potRef = userSnap.getReference().collection("equipment_potions").document(type.name());
            FirebaseFirestore db = userSnap.getReference().getFirestore();

            return db.runTransaction(tr -> {
                DocumentSnapshot potSnap = tr.get(potRef);
                Long count = potSnap.getLong("count");
                if (count == null || count < 1) throw new FirebaseFirestoreException("Nemaš napitak u inventaru!", FirebaseFirestoreException.Code.ABORTED);

                int currentPP = userSnap.getLong("pp") != null ? userSnap.getLong("pp").intValue() : 0;
                double pct = (type == PotionType.PERM_PP5) ? 0.05 : 0.10;
                int ppDelta = (int)Math.round(currentPP * pct);
                if (ppDelta == 0 && currentPP > 0) ppDelta = 1;

                tr.update(potRef, "count", count - 1);
                tr.update(userSnap.getReference(), "pp", currentPP + ppDelta);
                return null;
            });
        });
    }

    // --- CLOTHES ---

    public Task<Void> buyClothes(ClothesType type, int userLevel) {
        int price;
        if(type == ClothesType.BOOTS) price = EquipmentService.priceBoots(userLevel);
        else if (type == ClothesType.GLOVES) price = EquipmentService.priceGloves(userLevel);
        else price = EquipmentService.priceShield(userLevel);

        return repo.getUser().onSuccessTask(ds -> {
            int coins = ds.getLong("coins") != null ? ds.getLong("coins").intValue() : 0;
            if (coins < price) throw new IllegalStateException("Nedovoljno novčića");

            var batch = ds.getReference().getFirestore().batch();
            batch.update(ds.getReference(), "coins", coins - price);

            return batch.commit().onSuccessTask(v -> {
                return repo.addClothesStock(type);
            });
        });
    }

    public Task<Void> equipClothes(ClothesType type) {
        int addPercent = (type == ClothesType.BOOTS) ? 40 : 10;
        return repo.equipClothes(type, addPercent);
    }

    // --- WEAPONS ---

    public Task<Void> upgradeWeapon(WeaponType type, int currentLevel) {
        final int price = EquipmentService.priceWeaponUpgrade(Math.max(1, currentLevel - 1));
        return repo.getUser().onSuccessTask(ds -> {
            FirebaseFirestore db = ds.getReference().getFirestore();
            return db.runTransaction(tr -> {
                DocumentSnapshot fresh = tr.get(ds.getReference());
                Long coinsL = fresh.getLong("coins");
                int coins = (coinsL != null) ? coinsL.intValue() : 0;
                if (coins < price) throw new FirebaseFirestoreException("Nedovoljno novčića", FirebaseFirestoreException.Code.ABORTED);
                tr.update(ds.getReference(), "coins", coins - price);
                return null;
            }).onSuccessTask(v -> repo.upsertWeapon(type, 1, 0.0001));
        });
    }

    public Task<Void> afterBattleConsume() {
        return repo.consumeOneShotPotionsIfAny()
                .onSuccessTask(v -> repo.consumeAllActiveClothesOneUse());
    }
}