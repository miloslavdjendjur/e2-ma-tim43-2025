package com.example.data.service;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.data.model.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
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
    private static final long LIMIT_BOSS_HIT = 10;    // -2 HPxw
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

            tr.set(missionRef, mission);

            // BITNO: ne inicijalizujemo progress za sve članove (vođa ne sme da piše u tuđe doc-ove)
            return null;
        });
    }

    /**
     * bilo kakva kupovina u prodavnici (max 5) => -2 HP
     * Pozovi ovo nakon uspešne kupovine.
     */
    public void onAnyShopPurchase() {
        String uid = currentUid();
        if (uid == null) return;

        resolveMyAllianceId(uid)
                .addOnSuccessListener(allianceId -> {
                    if (allianceId != null && !allianceId.isEmpty()) {
                        applyCappedDamage(allianceId, uid, "purchaseCount", LIMIT_PURCHASE, 2L, true);
                    }
                    Log.d("SM", "allianceId=" + allianceId);
                });
    }

    /**
     * uspešan udarac u regularnoj borbi (max 10) => -2 HP
     */
    public void onRegularBossHitSuccess() {
        String uid = currentUid();
        if (uid == null) return;

        resolveMyAllianceId(uid)
                .addOnSuccessListener(allianceId -> {
                    if (allianceId != null && !allianceId.isEmpty()) {
                        applyCappedDamage(allianceId, uid, "bossHitCount", LIMIT_BOSS_HIT, 2L, true);
                    }

                });
    }

    /**
     * Task completed => easy bucket (-1) ili other bucket (-4)
     * easy+normal se računa kao 2 puta (cap “units”).
     */
    public void onTaskCompleted(@NonNull String uid, @NonNull Task t) {
        resolveMyAllianceId(uid)
                .addOnSuccessListener(allianceId -> {
                    if (allianceId == null || allianceId.isEmpty()) return;
                    applyTaskCompletion(allianceId, uid, t);
                });
        Log.d("SM", "onTaskCompleted called uid=" + uid);

    }

    /**
     * poslata poruka u savezu (računa se na nivou dana) => -4 HP / dan
     * allianceId ovde već imaš u AllianceChatFragment-u (bundle).
     */
    public void onAllianceMessageSent(@NonNull String allianceId, @NonNull String uid) {
        applyMessageDay(allianceId, uid);
    }

    // --------- Internals ---------

    private com.google.android.gms.tasks.Task<String> resolveMyAllianceId(@NonNull String uid) {
        return db.collection(COL_USERS).document(uid).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) return null;
                    return task.getResult().getString("allianceId");
                });
    }

    private void applyTaskCompletion(@NonNull String allianceId, @NonNull String uid, @NonNull Task t) {
        int diff = t.getDifficultyXp();   // 1,3,7,20
        int imp = t.getImportanceXp();    // 1,3,10,100

        // Spec: “veoma lak, lak, normalan ili važan” => -1 HP (max 10)
        // - interpretacija po vašem modelu:
        //   difficulty 1/3 OR importance 1/3 ulazi u easy bucket
        boolean easyBucket = (diff == 1 || diff == 3 || imp == 1 || imp == 3);

        // Spec: “ako je zadatak lak i normalan, računa se kao 2 puta”
        boolean easyNormalDouble = (diff == 3 && imp == 1);

        if (easyBucket) {
            int units = easyNormalDouble ? 2 : 1;
            applyEasyTaskUnits(allianceId, uid, units);
        } else {
            // Spec: “ostali zadaci” (max 6) => -4 HP
            applyCappedDamage(allianceId, uid, "otherTaskCount", LIMIT_OTHER_TASK, 4L, true);
        }
    }

    private void applyEasyTaskUnits(@NonNull String allianceId, @NonNull String uid, int units) {
        if (units <= 0) return;

        DocumentReference missionRef = missionRef(allianceId);
        DocumentReference progRef = missionRef.collection(SUB_PROGRESS).document(uid);

        db.runTransaction(tr -> {
            DocumentSnapshot mSnap = tr.get(missionRef);
            validateMissionActive(mSnap);

            DocumentSnapshot pSnap = tr.get(progRef);
            long currentUnits = safeLong(pSnap.getLong("easyTaskCount"));

            long remaining = LIMIT_EASY_TASK - currentUnits;
            if (remaining <= 0) return null;

            long allowed = Math.min(remaining, units);
            long hpDelta = allowed; // 1 HP per unit

            long hpLeft = safeLong(mSnap.getLong("bossHpRemaining"));
            long newHp = Math.max(0, hpLeft - hpDelta);

            tr.update(missionRef, "bossHpRemaining", newHp);
            tr.set(progRef, merge(
                    inc("easyTaskCount", allowed),
                    inc("specialTasksCompleted", 1)
            ), SetOptions.merge());

            if (newHp == 0) finishMission(tr, missionRef);

            return null;
        });
    }

    private void applyMessageDay(@NonNull String allianceId, @NonNull String uid) {
        DocumentReference missionRef = missionRef(allianceId);
        DocumentReference progRef = missionRef.collection(SUB_PROGRESS).document(uid);

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

            if (newHp == 0) finishMission(tr, missionRef);

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

            if (newHp == 0) finishMission(tr, missionRef);

            return null;
        });
    }

    private void finishMission(@NonNull com.google.firebase.firestore.Transaction tr, @NonNull DocumentReference missionRef) {
        tr.update(missionRef, "defeated", true);
        tr.update(missionRef, "active", false);
    }

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

    private static Map<String, Object> defaultProgress() {
        Map<String, Object> p = new HashMap<>();
        p.put("purchaseCount", 0L);
        p.put("bossHitCount", 0L);
        p.put("easyTaskCount", 0L);
        p.put("otherTaskCount", 0L);
        p.put("messageDaysCount", 0L);
        p.put("specialTasksCompleted", 0L);
        return p;
    }

    private static Map<String, Object> inc(String field, long by) {
        Map<String, Object> m = new HashMap<>();
        m.put(field, FieldValue.increment(by));
        return m;
    }

    private static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> out = new HashMap<>();
        if (a != null) out.putAll(a);
        if (b != null) out.putAll(b);
        return out;
    }

    private static long safeLong(@Nullable Long v) {
        return v != null ? v : 0L;
    }

    private static FirebaseFirestoreException abort(String msg) {
        // FirebaseFirestoreException je runtime exception => ok je bacati u transakciji
        return new FirebaseFirestoreException(msg, FirebaseFirestoreException.Code.ABORTED);
    }

    private static <T> com.google.android.gms.tasks.Task<T> fail(String msg) {
        TaskCompletionSource<T> tcs = new TaskCompletionSource<>();
        tcs.setException(new IllegalStateException(msg));
        return tcs.getTask();
    }

    @Nullable
    private static String currentUid() {
        return FirebaseAuth.getInstance().getUid();
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }
}
