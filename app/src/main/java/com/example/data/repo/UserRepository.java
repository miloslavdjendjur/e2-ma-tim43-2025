package com.example.data.repo;

import com.example.data.model.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<DocumentSnapshot> getUser(String uid) {
        return db.collection("users").document(uid).get();
    }

    public Task<Void> setActive(String uid) {
        return db.collection("users").document(uid).update("active", true);
    }

    public Task<Void> updateLastLogin(String uid) {
        return db.collection("users").document(uid).update("lastLogin", FieldValue.serverTimestamp());
    }

    public Task<Void> resetVerificationWindow(String uid) {
        return db.collection("users").document(uid).update("createdAt", FieldValue.serverTimestamp());
    }

    public Task<Void> reserveUsernameAndCreateUser(User u) {
        String uid = u.uid;
        String username = u.username;

        DocumentReference unameRef = db.collection("usernames").document(username);
        DocumentReference userRef  = db.collection("users").document(uid);

        return db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(unameRef);
            if (snap.exists()) {
                throw new FirebaseFirestoreException("Username already exists",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS);
            }

            Map<String,Object> unameDoc = new HashMap<>();
            unameDoc.put("uid", uid);
            tr.set(unameRef, unameDoc);

            Map<String,Object> userDoc = new HashMap<>();
            userDoc.put("uid", u.uid);
            userDoc.put("email", u.email);
            userDoc.put("username", u.username);           // ne menja se
            userDoc.put("avatarIndex", u.avatarIndex);     // 0..4
            userDoc.put("active", false);                  // start: false
            userDoc.put("level", u.level);                 // 1
            userDoc.put("title", u.title);                 // "Rookie"
            userDoc.put("xp", u.xp);                       // 0
            userDoc.put("pp", u.pp);                       // 0
            userDoc.put("coins", u.coins);                 // 0
            userDoc.put("badges", u.badges);               // 0
            userDoc.put("qrId", u.qrId);                   // npr. uid
            userDoc.put("createdAt", FieldValue.serverTimestamp()); // za 24h prozor
            tr.set(userRef, userDoc);

            return null;
        });
    }

    public Task<DocumentSnapshot> getCurrentUser() {
        String uid = getCurrentUid();
        if (uid == null) throw new IllegalStateException("Not logged in");
        return db.collection("users").document(uid).get();
    }

    private String getCurrentUid() {
        return com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

}
