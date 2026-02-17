package com.example.data.service;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.data.model.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 7.3 Specijalna misija saveza
 *
 * Firestore:
 * alliances/{allianceId}/special_mission/current
 * alliances/{allianceId}/special_mission/current/progress/{uid}
 * alliances/{allianceId}/special_mission/current/messageDays/{uid_yyyyMMdd} (dedupe marker)
 */
public class SpecialMissionService {

    private static final String COL_USERS = "users";
    private static final String COL_ALLIANCES = "alliances";

    private static final String SUB_SPECIAL = "special_mission";
    private static final String DOC_CURRENT = "current";
    private static final String SUB_PROGRESS = "progress";
    private static final String SUB_MESSAGE_DAYS = "messageDays";

    // Limiti iz specifikacije
    private static final long LIMIT_PURCHASE = 5;     // -2 HP
    private static final long LIMIT_BOSS_HIT = 10;    // -2 HP
    private static final long LIMIT_EASY_TASK = 10;   // -1 HP (easy+normal = 2 puta)
    private static final long LIMIT_OTHER_TASK = 6;   // -4 HP
    // MESSAGE_DAY: -4 HP per day (dedupe preko doc-a)

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Leader pokreće misiju. Ako već postoji aktivna, baca error.
     * Boss HP = 100 * broj članova saveza.
     */
    public com.google.android.gms.tasks.Task<Void> startMissionLeaderOnly(@NonNull String allianceId) {
        String uid = currentUid();
        if (uid == null) return fail("Not logged in.");

        DocumentReference allianceRef = db.collection(COL_ALLIANCES).document(allianceId);
        DocumentReference missionRef = allianceRef.collection(SUB_SPECIAL).document(DOC_CURRENT);

        return db.runTransaction(tr -> {
            DocumentSnapshot allianceSnap = tr.get(allianceRef);
            if (!allianceSnap.exists()) throw abort("Alliance not found.");

            String leaderUid = allianceSnap.getString("leaderUid");
            if (leaderUid == null || !leaderUid.equals(uid)) {
                throw abort("Samo vođa saveza može da pokrene misiju.");
            }

            DocumentSnapshot missionSnap = tr.get(missionRef);
            if (missionSnap.exists()) {
                Boolean active = missionSnap.getBoolean("active");
                if (active != null && active) {
                    throw abort("Savez već ima aktivnu specijalnu misiju.");
                }
            }

            @SuppressWarnings("unchecked")
            List<String> members = (List<String>) allianceSnap.get("members");
            long membersCount = (members != null) ? members.size() : 1L;
            if (membersCount <= 0) membersCount = 1L;

            long hpTotal = 100L * membersCount;

            Timestamp now = Timestamp.now();
            Timestamp endAt = new Timestamp(new Date(now.toDate().getTime() + 14L * 24L * 60L * 60L * 1000L));

            Map<String, Object> mission = new HashMap<>();
            mission.put("active", true);
            mission.put("defeated", false);
            mission.put("startAt", now);
            mission.put("endAt", endAt);
            mission.put("bossHpTotal", hpTotal);
            mission.put("bossHpRemaining", hpTotal);
            mission.put("membersCount", membersCount);
            mission.put("rewardsGranted", false);

            tr.set(missionRef, mission);

            // init progress docs
            if (members != null) {
                for (String m : members) {
                    DocumentReference pRef = missionRef.collection(SUB_PROGRESS).document(m);
                    Map<String, Object> p = new HashMap<>();
                    p.put("purchaseCount", 0L);
                    p.put("bossHitCount", 0L);
                    p.put("easyTaskCount", 0L);
                    p.put("otherTaskCount", 0L);
                    p.put("messageDaysCount", 0L);
                    p.put("specialTasksCompleted", 0L);
                    tr.set(pRef, p, SetOptions.merge());
                }
            }

            return null;
        });
    }

    // ------------------- EVENT HOOKS -------------------

    /** Kupovina u prodavnici (max 5) -2 HP */
    public void onAnyShopPurchase() {
        String uid = currentUid();
        if (uid == null) return;

        resolveAllianceId(uid)
                .addOnSuccessListener(allianceId -> applyCappedDamage(allianceId, uid, "purchaseCount", LIMIT_PURCHASE, 2L, true))
                .addOnFailureListener(e -> Log.e("SM", "shop purchase hook", e));
    }

    /** Uspešan udarac u regularnoj borbi sa bosom (max 10) -2 HP */
    public void onRegularBossHitSuccess() {
        String uid = currentUid();
        if (uid == null) return;

        resolveAllianceId(uid)
                .addOnSuccessListener(allianceId -> applyCappedDamage(allianceId, uid, "bossHitCount", LIMIT_BOSS_HIT, 2L, true))
                .addOnFailureListener(e -> Log.e("SM", "boss hit hook", e));
    }

    /**
     * Rešavanje taska:
     * - vrlo lak / lak / normal / važan: max 10 units, -1 HP per unit
     * - ako je lak ili normalan: računa se kao 2 units
     * - ostali taskovi: max 6, -4 HP
     */
    public void onTaskCompleted(@NonNull String uid, @NonNull Task task) {
        // uid prosleđen iz TaskService (sigurnije od currentUid)
        resolveAllianceId(uid)
                .addOnSuccessListener(allianceId -> {
                    int units = computeEasyUnits(task);
                    if (units > 0) {
                        applyEasyTaskUnits(allianceId, uid, units);
                        return;
                    }
                    // ostali
                    applyCappedDamage(allianceId, uid, "otherTaskCount", LIMIT_OTHER_TASK, 4L, true);
                })
                .addOnFailureListener(e -> Log.e("SM", "task hook", e));
    }

    /** Poruka u savezu (računa se na nivou dana) -4 HP za svaki dan */
    public void onAllianceMessageSent(@NonNull String allianceId, @NonNull String uid) {
        applyMessageDay(allianceId, uid);
    }

    // ------------------- APPLY LOGIC -------------------

    private void applyEasyTaskUnits(@NonNull String allianceId, @NonNull String uid, int units) {
        if (units <= 0) return;

        DocumentReference missionRef = missionRef(allianceId);
        DocumentReference progRef = missionRef.collection(SUB_PROGRESS).document(uid);
        DocumentReference allianceRef = db.collection(COL_ALLIANCES).document(allianceId);

        db.runTransaction(tr -> {
            DocumentSnapshot mSnap = tr.get(missionRef);
            validateMissionActive(mSnap);

            DocumentSnapshot pSnap = tr.get(progRef);
            long currentUnits = safeLong(pSnap.getLong("easyTaskCount"));

            long remaining = LIMIT_EASY_TASK - currentUnits;
            if (remaining <= 0) return null;

            long allowed = Math.min(remaining, units);
            long hpDelta = allowed; // 1 HP po unit-u

            long hpLeft = safeLong(mSnap.getLong("bossHpRemaining"));
            long newHp = Math.max(0, hpLeft - hpDelta);

            tr.update(missionRef, "bossHpRemaining", newHp);
            tr.set(progRef, merge(
                    inc("easyTaskCount", allowed),
                    inc("specialTasksCompleted", 1)
            ), SetOptions.merge());

            if (newHp == 0) finishMissionAndGrantRewards(tr, allianceRef, missionRef);

            return null;
        });
    }

    private void applyMessageDay(@NonNull String allianceId, @NonNull String uid) {
        DocumentReference missionRef = missionRef(allianceId);
        DocumentReference progRef = missionRef.collection(SUB_PROGRESS).document(uid);
        DocumentReference allianceRef = db.collection(COL_ALLIANCES).document(allianceId);

        String dayKey = todayKey();
        DocumentReference markerRef = missionRef.collection(SUB_MESSAGE_DAYS).document(uid + "_" + dayKey);

        db.runTransaction(tr -> {
            DocumentSnapshot mSnap = tr.get(missionRef);
            validateMissionActive(mSnap);

            DocumentSnapshot markerSnap = tr.get(markerRef);
            if (markerSnap.exists()) return null; // već računato danas

            long hpLeft = safeLong(mSnap.getLong("bossHpRemaining"));
            long newHp = Math.max(0, hpLeft - 4L);

            tr.set(markerRef, new HashMap<>()); // dedupe marker
            tr.update(missionRef, "bossHpRemaining", newHp);
            tr.set(progRef, merge(
                    inc("messageDaysCount", 1),
                    inc("specialTasksCompleted", 1)
            ), SetOptions.merge());

            if (newHp == 0) finishMissionAndGrantRewards(tr, allianceRef, missionRef);

            return null;
        });
    }

    private void applyCappedDamage(
            @NonNull String allianceId,
            @NonNull String uid,
            @NonNull String counterField,
            long limit,
            long hpDelta,
            boolean countAsSpecialTask
    ) {
        DocumentReference missionRef = missionRef(allianceId);
        DocumentReference progRef = missionRef.collection(SUB_PROGRESS).document(uid);
        DocumentReference allianceRef = db.collection(COL_ALLIANCES).document(allianceId);

        db.runTransaction(tr -> {
            DocumentSnapshot mSnap = tr.get(missionRef);
            validateMissionActive(mSnap);

            DocumentSnapshot pSnap = tr.get(progRef);
            long current = safeLong(pSnap.getLong(counterField));
            if (current >= limit) return null;

            long hpLeft = safeLong(mSnap.getLong("bossHpRemaining"));
            long newHp = Math.max(0, hpLeft - hpDelta);

            tr.update(missionRef, "bossHpRemaining", newHp);

            Map<String, Object> updates = new HashMap<>();
            updates.put(counterField, FieldValue.increment(1));
            if (countAsSpecialTask) updates.put("specialTasksCompleted", FieldValue.increment(1));
            tr.set(progRef, updates, SetOptions.merge());

            if (newHp == 0) finishMissionAndGrantRewards(tr, allianceRef, missionRef);

            return null;
        });
    }

    /**
     * Jedina “finish” metoda: završava misiju + dodeljuje reward.
     * Poziva se samo kad newHp postane 0, unutar iste transakcije.
     *
     * Idempotentno: rewardsGranted sprečava duplu dodelu.
     */
    private void finishMissionAndGrantRewards(
            @NonNull com.google.firebase.firestore.Transaction tr,
            @NonNull DocumentReference allianceRef,
            @NonNull DocumentReference missionRef
    ) throws FirebaseFirestoreException {

        // ---------- READ PHASE (must be first) ----------
        DocumentSnapshot missionSnap = tr.get(missionRef);
        if (missionSnap == null || !missionSnap.exists()) throw abort("Specijalna misija nije pokrenuta.");

        Boolean rewardsGranted = missionSnap.getBoolean("rewardsGranted");
        if (rewardsGranted != null && rewardsGranted) return;

        DocumentSnapshot allianceSnap = tr.get(allianceRef);
        if (allianceSnap == null || !allianceSnap.exists()) throw abort("Alliance not found.");

        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) allianceSnap.get("members");
        if (members == null || members.isEmpty()) throw abort("No members.");

        // Pre-read user snapshots (level) BEFORE any writes
        Map<String, Integer> userLevelMap = new HashMap<>();
        for (String uid : members) {
            DocumentReference userRef = db.collection(COL_USERS).document(uid);
            DocumentSnapshot uSnap = tr.get(userRef);

            int userLevel = 1;
            if (uSnap != null && uSnap.exists()) {
                Long lvl = uSnap.getLong("level");
                if (lvl != null && lvl > 0) userLevel = lvl.intValue();
            }
            userLevelMap.put(uid, userLevel);
        }

        // ---------- WRITE PHASE ----------
        tr.update(missionRef,
                "defeated", true,
                "active", false,
                "finishedAt", Timestamp.now(),
                "rewardsGranted", true,
                "bossHpRemaining", 0L // safe: ensure it's 0 when finishing
        );

        final String potionDocId = "ONE_SHOT_PP20";
        final String clothesDocId = "GLOVES";

        for (String uid : members) {
            DocumentReference userRef = db.collection(COL_USERS).document(uid);

            int nextBossLevel = Math.max(1, userLevelMap.get(uid));
            int baseCoins = calculateBaseCoinReward(nextBossLevel);
            long coinsReward = Math.round(baseCoins * 0.5d);

            // coins
            tr.update(userRef, "coins", FieldValue.increment(coinsReward));

            // potion count++
            DocumentReference potionRef = userRef.collection("equipment_potions").document(potionDocId);
            Map<String, Object> pot = new HashMap<>();
            pot.put("id", potionDocId);
            pot.put("type", potionDocId);
            pot.put("count", FieldValue.increment(1));
            tr.set(potionRef, pot, SetOptions.merge());

            // clothes count++ (matches EquipmentRepository logic)
            DocumentReference clothRef = userRef.collection("equipment_clothes").document(clothesDocId);
            Map<String, Object> cl = new HashMap<>();
            cl.put("id", clothesDocId);
            cl.put("type", clothesDocId);
            cl.put("count", FieldValue.increment(1));
            tr.set(clothRef, cl, SetOptions.merge());

            // badge counter
            tr.set(userRef, merge(inc("specialMissionWins", 1)), SetOptions.merge());
        }
    }


    // prvi boss (level 1) se pojavljuje kad user uđe u level 2,
    // naredni boss odgovara user level-u.
    private int nextBossLevelFromUserLevel(int userLevel) {
        return Math.max(1, userLevel);
    }

    // Ista formula kao u BossService.calculateBaseCoinReward
    private int calculateBaseCoinReward(int bossLevel) {
        if (bossLevel <= 1) return 200;
        long coins = 200;
        for (int i = 2; i <= bossLevel; i++) {
            coins = (coins * 120) / 100; // +20%
        }
        return (coins > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) coins;
    }

    // ------------------- DEBUG -------------------
    public com.google.android.gms.tasks.Task<Void> debugTriggerRewards(@NonNull String allianceId) {
        DocumentReference allianceRef = db.collection(COL_ALLIANCES).document(allianceId);
        DocumentReference missionRef = allianceRef.collection(SUB_SPECIAL).document(DOC_CURRENT);

        return db.runTransaction(tr -> {
            // Finish+rewards handles bossHpRemaining=0 inside (and is idempotent)
            finishMissionAndGrantRewards(tr, allianceRef, missionRef);
            return null;
        });
    }


    // ------------------- VALIDATION / HELPERS -------------------

    private void validateMissionActive(@Nullable DocumentSnapshot mSnap) throws FirebaseFirestoreException {
        if (mSnap == null || !mSnap.exists()) throw abort("Specijalna misija nije pokrenuta.");

        Boolean defeated = mSnap.getBoolean("defeated");
        if (defeated != null && defeated) throw abort("Misija je već završena.");

        Boolean active = mSnap.getBoolean("active");
        if (active == null || !active) throw abort("Specijalna misija nije aktivna.");

        Timestamp endAt = mSnap.getTimestamp("endAt");
        if (endAt != null && Timestamp.now().compareTo(endAt) > 0) throw abort("Isteklo vreme misije.");
    }

    private DocumentReference missionRef(@NonNull String allianceId) {
        return db.collection(COL_ALLIANCES)
                .document(allianceId)
                .collection(SUB_SPECIAL)
                .document(DOC_CURRENT);
    }

    private int computeEasyUnits(@NonNull Task task) {
        // very easy / easy / normal / important -> “easy bucket”
        // your task model uses difficultyXp & importanceXp
        int d = task.getDifficultyXp();
        int i = task.getImportanceXp();

        boolean isEasyBucket =
                d == 1 || d == 3 || d == 5 || i == 3; // very easy/easy/normal OR important

        if (!isEasyBucket) return 0;

        // if task is easy (3) OR normal (5) -> counts twice
        if (d == 3 || d == 5) return 2;
        return 1;
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private long safeLong(@Nullable Long v) {
        return v != null ? v : 0L;
    }

    private Map<String, Object> merge(Map<String, Object>... maps) {
        Map<String, Object> out = new HashMap<>();
        if (maps != null) {
            for (Map<String, Object> m : maps) if (m != null) out.putAll(m);
        }
        return out;
    }

    private Map<String, Object> inc(@NonNull String field, long by) {
        Map<String, Object> m = new HashMap<>();
        m.put(field, FieldValue.increment(by));
        return m;
    }

    private String currentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return null;
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private com.google.android.gms.tasks.Task<String> resolveAllianceId(@NonNull String uid) {
        return db.collection(COL_USERS).document(uid).get().continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            DocumentSnapshot snap = task.getResult();
            if (snap == null || !snap.exists()) throw abort("User not found.");
            String allianceId = snap.getString("allianceId");
            if (allianceId == null || allianceId.trim().isEmpty()) throw abort("User is not in alliance.");
            return allianceId;
        });
    }

    private com.google.android.gms.tasks.Task<Void> fail(@NonNull String msg) {
        com.google.android.gms.tasks.TaskCompletionSource<Void> tcs = new com.google.android.gms.tasks.TaskCompletionSource<>();
        tcs.setException(new FirebaseFirestoreException(msg, FirebaseFirestoreException.Code.ABORTED));
        return tcs.getTask();
    }

    private FirebaseFirestoreException abort(@NonNull String msg) {
        return new FirebaseFirestoreException(msg, FirebaseFirestoreException.Code.ABORTED);
    }
}
