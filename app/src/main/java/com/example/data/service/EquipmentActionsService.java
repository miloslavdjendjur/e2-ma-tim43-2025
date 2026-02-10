package com.example.data.service;

import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.PotionType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.EquipmentRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

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
            boolean perm = (type == PotionType.PERM_PP5 || type == PotionType.PERM_PP10);
            int ppDelta = 0;
            if (perm) {
                int pp = ds.getLong("pp") != null ? ds.getLong("pp").intValue() : 0;
                double pct = (type == PotionType.PERM_PP5) ? 0.05 : 0.10;
                ppDelta = (int)Math.round(pp * pct);
            }
            var db = repo.getUser().getResult().getReference().getFirestore();
            var userRef = ds.getReference();
            var batch = db.batch();
            batch.update(userRef, "coins", coins - price);
            if (perm) {
                int newPp = (ds.getLong("pp") != null ? ds.getLong("pp").intValue() : 0) + ppDelta;
                batch.update(userRef, "pp", newPp);
            }
            var potRef = db.collection("users").document(userRef.getId()).collection("equipment_potions").document(type.name());
            batch.set(potRef, new java.util.HashMap<String, Object>(){{
                put("id", type.name());
                put("type", type.name());
                put("count", FieldValue.increment(1));
            }}, com.google.firebase.firestore.SetOptions.merge());
            return batch.commit();
        });
    }

    public Task<Void> activateOneShotPotion(PotionType type) {
        return repo.setPotionPendingUse(type, true);
    }

    public Task<Void> buyClothes(ClothesType type, int userLevel) {
        int price;
        if(type == ClothesType.BOOTS)
        {
            price = EquipmentService.priceBoots(userLevel);
        } else if (type == ClothesType.GLOVES) {
            price = EquipmentService.priceGloves(userLevel);
        } else if (type == ClothesType.SHIELD){
            price = EquipmentService.priceShield(userLevel);
        }  else {
        throw new IllegalArgumentException("Nepoznat tip odeće: " + type);
        }
        return repo.getUser().onSuccessTask(ds -> {
            int coins = ds.getLong("coins") != null ? ds.getLong("coins").intValue() : 0;
            if (coins < price) throw new IllegalStateException("Nedovoljno novčića");
            var db = ds.getReference().getFirestore();
            var batch = db.batch();
            batch.update(ds.getReference(), "coins", coins - price);

            return batch.commit();
        });
    }

    public Task<Void> equipClothes(ClothesType type) {

        int addPercent = (type == ClothesType.BOOTS) ? 40 : 10;
        return repo.equipClothes(type, addPercent);
    }

    public Task<Void> upgradeWeapon(WeaponType type, int currentLevel) {
        final int price = EquipmentService.priceWeaponUpgrade(currentLevel + 1);

        return repo.getUser().onSuccessTask(ds -> {
            FirebaseFirestore db = ds.getReference().getFirestore();

            return db.runTransaction(tr -> {
                        DocumentSnapshot fresh = tr.get(ds.getReference());
                        Long coinsL = fresh.getLong("coins");
                        int coins = (coinsL != null) ? coinsL.intValue() : 0;

                        if (coins < price) {
                            throw new FirebaseFirestoreException(
                                    "Nedovoljno novčića",
                                    FirebaseFirestoreException.Code.ABORTED
                            );
                        }

                        tr.update(ds.getReference(), "coins", coins - price);
                        return null;
                    })

                    .onSuccessTask(v -> repo.upsertWeapon(type, 1, 0.0001));
        });
    }

    public Task<Void> afterBattleConsume() {
        return repo.consumeOneShotPotionsIfAny().onSuccessTask(v -> repo.consumeAllActiveClothesOneUse());
    }
}
