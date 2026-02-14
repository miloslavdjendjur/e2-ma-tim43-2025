package com.example.data.repo;

import com.example.data.model.boss.Boss;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class BossRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String getUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return null;
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public Task<DocumentSnapshot> getCurrentBoss() {
        String uid = getUserId();
        if (uid == null) return Tasks.forException(new IllegalStateException("User not logged in"));
        return db.collection("users").document(uid)
                .collection("boss_state").document("current")
                .get();
    }

    public Task<Void> saveBoss(Boss boss) {
        String uid = getUserId();
        if (uid == null) return Tasks.forException(new IllegalStateException("User not logged in"));
        return db.collection("users").document(uid)
                .collection("boss_state").document("current")
                .set(boss);
    }
}
