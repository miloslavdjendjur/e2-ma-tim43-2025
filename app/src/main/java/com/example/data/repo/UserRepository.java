package com.example.data.repo;

import com.example.data.model.Alliance;
import com.example.data.model.AllianceInvite;
import com.example.data.model.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // --- USER METODE ---

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
<<<<<<< Updated upstream
            userDoc.put("username", u.username);           // ne menja se
            userDoc.put("avatarIndex", u.avatarIndex);     // 0..4
            userDoc.put("active", false);                  // start: false
            userDoc.put("level", u.level);                 // 1
            userDoc.put("title", u.title);                 // "Novajlija"
            userDoc.put("xp", u.xp);                       // 0
            userDoc.put("pp", u.pp);                       // 0
            userDoc.put("coins", u.coins);                 // 0
            userDoc.put("badges", u.badges);               // 0
            userDoc.put("qrId", u.qrId);                   // npr. uid
            userDoc.put("createdAt", FieldValue.serverTimestamp()); // za 24h prozor
=======
            userDoc.put("username", u.username);
            userDoc.put("avatarIndex", u.avatarIndex);
            userDoc.put("active", false);
            userDoc.put("level", u.level);
            userDoc.put("title", u.title);
            userDoc.put("xp", u.xp);
            userDoc.put("pp", u.pp);
            userDoc.put("coins", u.coins);
            userDoc.put("badges", u.badges);
            userDoc.put("qrId", u.qrId);
            userDoc.put("createdAt", FieldValue.serverTimestamp());
>>>>>>> Stashed changes
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

    // --- FRIENDS METODE ---

    public Task<DocumentSnapshot> searchUserByUsername(String username) {
        return db.collection("usernames").document(username).get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        throw new FirebaseFirestoreException("Korisnik nije pronađen", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    String uid = task.getResult().getString("uid");
                    return getUser(uid);
                });
    }

    public Task<Void> addFriend(String myUid, User friend) {
        DocumentReference myFriendRef = db.collection("users").document(myUid)
                .collection("friends").document(friend.uid);

        Map<String, Object> friendData = new HashMap<>();
        friendData.put("uid", friend.uid);
        friendData.put("username", friend.username);
        friendData.put("avatarIndex", friend.avatarIndex);

        return myFriendRef.set(friendData);
    }

    public Query getFriendsQuery(String myUid) {
        return db.collection("users").document(myUid).collection("friends");
    }

    // --- ALLIANCE METODE ---

    public Task<Void> createAlliance(String name, User leader) {
        DocumentReference newAllianceRef = db.collection("alliances").document();
        String allianceId = newAllianceRef.getId();

        Alliance alliance = new Alliance(allianceId, name, leader.uid, java.util.Collections.singletonList(leader.uid));

        WriteBatch batch = db.batch();
        batch.set(newAllianceRef, alliance);
        batch.update(db.collection("users").document(leader.uid), "allianceId", allianceId);

        return batch.commit();
    }

    public Task<Void> inviteToAlliance(String targetUid, Alliance alliance, String myName) {
        // Koristimo alliance.id kao ID dokumenta pozivnice da bismo izbegli duplikate
        DocumentReference inviteRef = db.collection("users").document(targetUid)
                .collection("alliance_invites").document(alliance.id);

        AllianceInvite invite = new AllianceInvite(alliance.id, alliance.name, alliance.leaderUid, myName);
        return inviteRef.set(invite);
    }

    public Task<Void> respondToInvite(String myUid, AllianceInvite invite, boolean accept) {
        return db.runTransaction(transaction -> {
            DocumentReference inviteRef = db.collection("users").document(myUid)
                    .collection("alliance_invites").document(invite.id); // Koristi ID dokumenta!

            DocumentReference userRef = db.collection("users").document(myUid);

            // --- 1. ČITANJE (MORA BITI PRVO!) ---
            // Čitamo korisnika pre nego što bilo šta obrišemo ili upišemo
            DocumentSnapshot userSnap = transaction.get(userRef);

            // --- 2. PISANJE (TEK KAD SU SVA ČITANJA GOTOVA) ---

            // Sada možemo da obrišemo pozivnicu
            transaction.delete(inviteRef);

            if (accept) {
                // Logika za prelazak u novi savez
                String currentAllianceId = userSnap.getString("allianceId");

                // Ako je već u nekom savezu, izbaci ga iz starog
                if (currentAllianceId != null && !currentAllianceId.isEmpty()) {
                    DocumentReference oldAllianceRef = db.collection("alliances").document(currentAllianceId);
                    transaction.update(oldAllianceRef, "members", FieldValue.arrayRemove(myUid));
                }

                // Ubaci ga u novi savez
                DocumentReference newAllianceRef = db.collection("alliances").document(invite.allianceId);
                transaction.update(newAllianceRef, "members", FieldValue.arrayUnion(myUid));

                // Ažuriraj korisnika
                transaction.update(userRef, "allianceId", invite.allianceId);
            }

            return null;
        });
    }

    public Task<Void> disbandAlliance(String allianceId) {
        return db.collection("users").whereEqualTo("allianceId", allianceId).get()
                .continueWithTask(task -> {
                    WriteBatch batch = db.batch();

                    batch.delete(db.collection("alliances").document(allianceId));

                    for (DocumentSnapshot doc : task.getResult()) {
                        batch.update(doc.getReference(), "allianceId", null);
                    }

                    return batch.commit();
                });
    }

    public Task<Alliance> getAlliance(String allianceId) {
        return db.collection("alliances").document(allianceId).get()
                .continueWith(task -> task.getResult().toObject(Alliance.class));
    }

    public Query getInvitesQuery(String myUid) {
        return db.collection("users").document(myUid).collection("alliance_invites");
    }
}