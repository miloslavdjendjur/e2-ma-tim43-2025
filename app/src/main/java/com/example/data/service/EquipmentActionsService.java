package com.example.data.service;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.EquipmentRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.util.HashMap;

public class EquipmentActionsService {
    private final EquipmentRepository repo = new EquipmentRepository();

    public Task<Void> buyPotion(PotionType type, int userLevel) {
        int price;
        if (type == PotionType.ONE_SHOT_PP20) {
            price = EquipmentService.pricePotionOneShot20(userLevel);
        } else if (type == PotionType.ONE_SHOT_PP40) {
            price = EquipmentService.pricePotionOneShot40(userLevel);
        } else if (type == PotionType.PERM_PP5) {
            price = EquipmentService.pricePotionPerm5(userLevel);
        } else if (type == PotionType.PERM_PP10) {
            price = EquipmentService.pricePotionPerm10(userLevel);
        } else {
            throw new IllegalArgumentException("Nepoznat tip napitka: " + type);
        }

        return repo.getUser().onSuccessTask(ds -> {
            int coins = ds.getLong("coins") != null ? ds.getLong("coins").intValue() : 0;
            if (coins < price) throw new IllegalStateException("Nedovoljno novčića");

            // IZMENA: Ovde više NE menjamo PP odmah. Samo kupujemo (Inventory +1).
            var db = ds.getReference().getFirestore();
            var userRef = ds.getReference();
            var batch = db.batch();

            // 1. Skini novac
            batch.update(userRef, "coins", coins - price);

            // 2. Dodaj u inventar (collection: equipment_potions)
            var potRef = db.collection("users").document(userRef.getId())
                    .collection("equipment_potions").document(type.name());

            batch.set(potRef, new HashMap<String, Object>(){{
                put("id", type.name());
                put("type", type.name());
                put("count", FieldValue.increment(1));
            }}, com.google.firebase.firestore.SetOptions.merge());

            return batch.commit();
        });
    }

    // NOVA METODA: Služi da se trajni napitak iskoristi (popije)
    public Task<Void> activatePermanentPotion(PotionType type) {
        if (type != PotionType.PERM_PP5 && type != PotionType.PERM_PP10) {
            throw new IllegalArgumentException("Ovo nije trajni napitak!");
        }

        return repo.getUser().onSuccessTask(userSnap -> {
            DocumentReference userRef = userSnap.getReference();
            DocumentReference potRef = userRef.collection("equipment_potions").document(type.name());
            FirebaseFirestore db = userRef.getFirestore();

            return db.runTransaction(tr -> {
                DocumentSnapshot potSnap = tr.get(potRef);
                Long count = potSnap.getLong("count");
                if (count == null || count < 1) {
                    throw new FirebaseFirestoreException("Nemaš ovaj napitak!", FirebaseFirestoreException.Code.ABORTED);
                }

                // Izračunaj povećanje PP-a
                int currentPP = userSnap.getLong("pp") != null ? userSnap.getLong("pp").intValue() : 0;
                double pct = (type == PotionType.PERM_PP5) ? 0.05 : 0.10;
                int ppDelta = (int)Math.round(currentPP * pct);
                // Minimum 1 PP ako je procenat mali, da ne bude 0
                if (ppDelta == 0 && currentPP > 0) ppDelta = 1;

                // Ažuriraj bazu: Smanji count, Povećaj PP
                tr.update(potRef, "count", count - 1);
                tr.update(userRef, "pp", currentPP + ppDelta);

                return null;
            });
        });
    }

    public Task<Void> activateOneShotPotion(PotionType type) {
        return repo.setPotionPendingUse(type, true);
    }

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
            return batch.commit();
        });
    }

    public Task<Void> equipClothes(ClothesType type) {
        int addPercent = (type == ClothesType.BOOTS) ? 40 : 10;
        return repo.equipClothes(type, addPercent);
    }

    public Task<Void> upgradeWeapon(WeaponType type, int currentLevel) {
        // ISPRAVKA: Cena se gleda prema PRETHODNOM bossu
        final int price = EquipmentService.priceWeaponUpgrade(Math.max(1, currentLevel - 1));

        return repo.getUser().onSuccessTask(ds -> {
            FirebaseFirestore db = ds.getReference().getFirestore();
            return db.runTransaction(tr -> {
                DocumentSnapshot fresh = tr.get(ds.getReference());
                Long coinsL = fresh.getLong("coins");
                int coins = (coinsL != null) ? coinsL.intValue() : 0;
                if (coins < price) {
                    throw new FirebaseFirestoreException("Nedovoljno novčića", FirebaseFirestoreException.Code.ABORTED);
                }
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