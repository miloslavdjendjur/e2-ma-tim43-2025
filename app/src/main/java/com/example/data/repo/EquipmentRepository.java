package com.example.data.repo;

import androidx.annotation.NonNull;
import com.example.data.model.equipment.type.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

public class EquipmentRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String uid() {
        var u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) throw new IllegalStateException("Not logged in");
        return u.getUid();
    }

    private CollectionReference potions() { return db.collection("users").document(uid()).collection("equipment_potions"); }
    private CollectionReference clothes() { return db.collection("users").document(uid()).collection("equipment_clothes"); }
    private CollectionReference weapons() { return db.collection("users").document(uid()).collection("equipment_weapons"); }
    private DocumentReference userDoc() { return db.collection("users").document(uid()); }

    // --- POTIONS ---

    public Task<Void> addPotion(PotionType type, int delta) {
        DocumentReference ref = potions().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            int count = (snap.exists() && snap.getLong("count") != null) ? snap.getLong("count").intValue() : 0;
            count += delta;
            Map<String,Object> data = new HashMap<>();
            data.put("id", type.name());
            data.put("type", type.name());
            data.put("count", Math.max(count, 0));
            tr.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    public Task<Void> setPotionPendingUse(PotionType type, boolean pending) { // Stari potpis metode
        DocumentReference ref = potions().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            Long count = snap.getLong("count");

            if (!snap.exists() || count == null || count < 1) {
                throw new FirebaseFirestoreException("Nemaš ovaj napitak u inventaru!", FirebaseFirestoreException.Code.ABORTED);
            }

            if (pending) {
                tr.update(ref, "pendingUse", true);
            } else {
                tr.update(ref, "pendingUse", false);
            }
            return null;
        });
    }

    // --- CLOTHES ---

    public Task<Void> addClothesStock(ClothesType type) {
        DocumentReference ref = clothes().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            int currentStock = 0;
            if (snap.exists() && snap.getLong("count") != null) {
                currentStock = snap.getLong("count").intValue();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", type.name());
            data.put("type", type.name());
            data.put("count", currentStock + 1);

            tr.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    public Task<Void> equipClothes(ClothesType type, int addPercent) {
        DocumentReference ref = clothes().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);

            int stock = 0;
            if (snap.exists() && snap.getLong("count") != null) {
                stock = snap.getLong("count").intValue();
            }

            if (stock <= 0) {
                throw new FirebaseFirestoreException("Nemaš ovu opremu u inventaru! Kupi je prvo.", FirebaseFirestoreException.Code.ABORTED);
            }
            int currentStacked = 0;
            if (snap.exists()) {
                if (snap.contains("stackedPercent") && snap.getLong("stackedPercent") != null)
                    currentStacked = snap.getLong("stackedPercent").intValue();
            }

            Map<String,Object> data = new HashMap<>();
            data.put("active", true);
            data.put("usesLeft", 2);
            data.put("stackedPercent", currentStacked + addPercent);
            data.put("count", stock - 1);

            tr.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    public Task<Void> consumeClothesUse(ClothesType type) {
        DocumentReference ref = clothes().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            if (!snap.exists()) return null;
            Long ul = snap.getLong("usesLeft");
            int newUses = Math.max(0, (ul != null ? ul.intValue() : 0) - 1);
            Map<String,Object> data = new HashMap<>();
            data.put("usesLeft", newUses);
            if (newUses == 0) {
                data.put("active", false);
                data.put("stackedPercent", 0);
            }
            tr.update(ref, data);
            return null;
        });
    }

    // --- WEAPONS ---

    public Task<Void> upsertWeapon(WeaponType type, int newLevelDelta, double probDelta) {
        DocumentReference ref = weapons().document(type.name());
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            int level = newLevelDelta;
            double prob = probDelta;
            if (snap.exists()) {
                Long lv = snap.getLong("level");
                Double pr = snap.getDouble("dropProbBonus");
                level = (lv != null ? lv.intValue() : 0) + newLevelDelta;
                prob = (pr != null ? pr : 0d) + probDelta;
            }
            Map<String,Object> data = new HashMap<>();
            data.put("id", type.name());
            data.put("type", type.name());
            data.put("level", Math.max(0, level));
            data.put("dropProbBonus", prob);
            tr.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    public Task<Void> addCoins(int delta) { return userDoc().update("coins", FieldValue.increment(delta)); }
    public Task<DocumentSnapshot> getUser() { return userDoc().get(); }
    public Task<QuerySnapshot> getWeapons(){ return weapons().get(); }
    public Task<QuerySnapshot> getClothes(){ return clothes().get(); }
    public Task<QuerySnapshot> getPotions(){ return potions().get(); }

    public Task<Void> consumeOneShotPotionsIfAny() {
        return potions().get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            QuerySnapshot snap = task.getResult();
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Boolean pending = d.getBoolean("pendingUse");
                Long count = d.getLong("count");
                if (Boolean.TRUE.equals(pending)) {
                    int newCount = Math.max(0, (count != null ? count.intValue() : 1) - 1);
                    batch.update(d.getReference(), "pendingUse", false);
                    batch.update(d.getReference(), "count", newCount);
                }
            }
            return batch.commit();
        });
    }

    public Task<Void> consumeAllActiveClothesOneUse() {
        return clothes().get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            QuerySnapshot snap = task.getResult();
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Boolean active = d.getBoolean("active");
                Long usesLeft = d.getLong("usesLeft");
                if (Boolean.TRUE.equals(active) && usesLeft != null && usesLeft > 0) {
                    int newUses = usesLeft.intValue() - 1;
                    batch.update(d.getReference(), "usesLeft", newUses);
                    if (newUses == 0) {
                        batch.update(d.getReference(), "active", false);
                        batch.update(d.getReference(), "stackedPercent", 0);
                    }
                }
            }
            return batch.commit();
        });
    }
}