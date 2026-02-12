package com.example.data.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.data.model.Task;
import com.example.data.model.User;
import com.example.data.repo.TaskRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Business layer for tasks.
 *
 * - Implements spec 2.1 XP awarding with quotas.
 * - Uses LevelingService to update User profile on task completion.
 */
public class TaskService {

    private final TaskRepository repo;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference tasksRef = db.collection("tasks");
    private final CollectionReference usersRef = db.collection("users");
    private final CollectionReference xpEventsRef = db.collection("xp_events");

    // ---- Quota keys (based on spec) ----
    private static final String QUOTA_VE_NORMAL = "VE_NORMAL";                   // Very easy (1) + Normal (1)
    private static final String QUOTA_EASY_IMPORTANT = "EASY_IMPORTANT";         // Easy (3) + Important (3)
    private static final String QUOTA_HARD_EXT_IMPORTANT = "HARD_EXT_IMPORTANT"; // Hard (7) + Extremely important (10)
    private static final String QUOTA_EXTREMELY_HARD = "EXTREMELY_HARD";         // Difficulty = 20
    private static final String QUOTA_SPECIAL = "SPECIAL";                       // Importance = 100

    public TaskService() {
        this(new TaskRepository());
    }

    public TaskService(@NonNull TaskRepository repo) {
        this.repo = repo;
    }

    // ---------------- Auth ----------------

    @Nullable
    private String getUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ---------------- Simple CRUD delegation ----------------

    public void addTask(@NonNull Task task, @NonNull OnTaskActionEventListener listener) {
        repo.addTask(task, new TaskRepository.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) { listener.onSuccess(message); }
            @Override public void onError(String error) { listener.onError(error); }
        });
    }

    public void updateTask(@NonNull Task task, @NonNull OnTaskActionEventListener listener) {
        repo.updateTask(task, new TaskRepository.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) { listener.onSuccess(message); }
            @Override public void onError(String error) { listener.onError(error); }
        });
    }

    public void deleteTask(@NonNull String taskId, @NonNull OnTaskActionEventListener listener) {
        repo.deleteTask(taskId, new TaskRepository.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) { listener.onSuccess(message); }
            @Override public void onError(String error) { listener.onError(error); }
        });
    }

    public void truncateRecurringFromDate(@NonNull String taskId, @NonNull Timestamp newEndDate, @NonNull OnTaskActionEventListener listener) {
        repo.truncateRecurringFromDate(taskId, newEndDate, new TaskRepository.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) { listener.onSuccess(message); }
            @Override public void onError(String error) { listener.onError(error); }
        });
    }

    public void getTasks(@NonNull OnTasksLoadedListener listener) {
        repo.getTasks(listener::onLoaded);
    }

    public void getTaskById(@NonNull String taskId, @NonNull OnTaskLoadedListener listener) {
        repo.getTaskById(taskId, listener::onLoaded);
    }

    // ---------------- XP quota logic (spec 2.1) ----------------

    @Nullable
    private String quotaKeyForTask(@NonNull Task t) {
        // Special overrides everything (spec: max 1 monthly)
        if (t.getImportanceXp() == 100) return QUOTA_SPECIAL;

        // Extremely hard by difficulty (spec: max 1 weekly)
        if (t.getDifficultyXp() == 20) return QUOTA_EXTREMELY_HARD;

        // Combinations
        if (t.getDifficultyXp() == 1 && t.getImportanceXp() == 1) return QUOTA_VE_NORMAL;
        if (t.getDifficultyXp() == 3 && t.getImportanceXp() == 3) return QUOTA_EASY_IMPORTANT;
        if (t.getDifficultyXp() == 7 && t.getImportanceXp() == 10) return QUOTA_HARD_EXT_IMPORTANT;

        return null;
    }

    private int quotaLimitForKey(@NonNull String quotaKey) {
        switch (quotaKey) {
            case QUOTA_VE_NORMAL:
            case QUOTA_EASY_IMPORTANT:
                return 5; // daily
            case QUOTA_HARD_EXT_IMPORTANT:
                return 2; // daily
            case QUOTA_EXTREMELY_HARD:
            case QUOTA_SPECIAL:
                return 1; // weekly / monthly
            default:
                return Integer.MAX_VALUE;
        }
    }

    private boolean isDailyQuota(@NonNull String quotaKey) {
        return QUOTA_VE_NORMAL.equals(quotaKey)
                || QUOTA_EASY_IMPORTANT.equals(quotaKey)
                || QUOTA_HARD_EXT_IMPORTANT.equals(quotaKey);
    }

    private boolean isWeeklyQuota(@NonNull String quotaKey) {
        return QUOTA_EXTREMELY_HARD.equals(quotaKey);
    }

    private boolean isMonthlyQuota(@NonNull String quotaKey) {
        return QUOTA_SPECIAL.equals(quotaKey);
    }

    @Nullable
    private String dateKeyFromTimestamp(@Nullable Timestamp ts) {
        if (ts == null) return null;
        Calendar c = Calendar.getInstance();
        c.setTime(ts.toDate());
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
    }

    private String weekKeyFromDateKey(@NonNull String dateKey) {
        try {
            String[] parts = dateKey.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);

            Calendar c = Calendar.getInstance();
            c.setFirstDayOfWeek(Calendar.MONDAY);
            c.setMinimalDaysInFirstWeek(4);
            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);

            int week = c.get(Calendar.WEEK_OF_YEAR);
            int weekYear = c.getWeekYear();
            return String.format(Locale.US, "%04d-W%02d", weekYear, week);
        } catch (Exception e) {
            return dateKey;
        }
    }

    private String monthKeyFromDateKey(@NonNull String dateKey) {
        if (dateKey.length() >= 7) return dateKey.substring(0, 7); // yyyy-MM
        return dateKey;
    }

    private String periodKeyForQuota(@NonNull String quotaKey, @NonNull String dateKey) {
        if (isDailyQuota(quotaKey)) return dateKey;
        if (isWeeklyQuota(quotaKey)) return weekKeyFromDateKey(dateKey);
        if (isMonthlyQuota(quotaKey)) return monthKeyFromDateKey(dateKey);
        return dateKey;
    }

    /**
     * CORE LOGIC:
     * 1. Checks quotas.
     * 2. Runs a Transaction to update User (add XP, Level Up) and save XP Event.
     */
    private void applyCompletionXpIfNeeded(
            @NonNull String uid,
            @NonNull Task task,
            @NonNull String taskId,
            @Nullable String occurrenceDateKey,
            @NonNull Map<String, Object> taskUpdates,
            @NonNull OnTaskActionEventListener listener
    ) {
        // 1. Determine Date & Quota Keys
        String dateKey;
        if (occurrenceDateKey != null && !occurrenceDateKey.isEmpty()) {
            dateKey = occurrenceDateKey;
        } else {
            String fromExec = dateKeyFromTimestamp(task.getExecutionTime());
            dateKey = (fromExec != null) ? fromExec : dateKeyFromTimestamp(Timestamp.now());
        }

        final int xpPotential = Math.max(0, task.getTotalXp());
        final String quotaKey = quotaKeyForTask(task);
        final String periodKey = (quotaKey != null) ? periodKeyForQuota(quotaKey, dateKey) : dateKey;

        // 2. Helper to run transaction after quota check
        Runnable runTransaction = () -> {
            db.runTransaction(transaction -> {
                // A. Read User
                DocumentReference userDoc = usersRef.document(uid);
                DocumentSnapshot userSnap = transaction.get(userDoc);
                User user = userSnap.toObject(User.class);
                if (user == null) {
                    // Fallback if user doesn't exist yet (should not happen in prod)
                    user = new User();
                    user.uid = uid;
                }

                // B. Check Quota limit (Double check inside if needed, but we do pre-check below)
                // Note: Counting docs inside transaction is hard. We rely on the pre-check.
                // Assuming we are allowed to award XP here.

                // C. Calculate Leveling using LevelingService
                boolean leveledUp = LevelingService.addXp(user, xpPotential);

                // D. Write updates
                transaction.set(userDoc, user); // Saves updated XP, Level, PP, Title
                transaction.update(tasksRef.document(taskId), taskUpdates); // Updates task status

                // E. Write XP Event
                DocumentReference newEventRef = xpEventsRef.document();
                Map<String, Object> evt = new HashMap<>();
                evt.put("userId", uid);
                evt.put("taskId", taskId);
                evt.put("dateKey", dateKey);
                evt.put("quotaKey", (quotaKey != null) ? quotaKey : "NONE");
                evt.put("periodKey", periodKey);
                evt.put("xpAdded", xpPotential);
                evt.put("awarded", true);
                evt.put("createdAt", Timestamp.now());
                if (occurrenceDateKey != null) evt.put("occurrenceDateKey", occurrenceDateKey);

                transaction.set(newEventRef, evt);

                return leveledUp; // Result of transaction
            }).addOnSuccessListener(leveledUp -> {
                String msg = "Status updated: done (+" + xpPotential + " XP)";
                if (leveledUp) msg += " LEVEL UP!";
                listener.onSuccess(msg);
            }).addOnFailureListener(e -> listener.onError("Transaction failed: " + e.getMessage()));
        };

        // 3. Helper for Quota Limit Reached (No XP)
        Runnable runNoXpUpdate = () -> {
            WriteBatch batch = db.batch();
            batch.update(tasksRef.document(taskId), taskUpdates);

            // Log failed event (awarded=false)
            Map<String, Object> evt = new HashMap<>();
            evt.put("userId", uid);
            evt.put("taskId", taskId);
            evt.put("dateKey", dateKey);
            evt.put("quotaKey", quotaKey);
            evt.put("periodKey", periodKey);
            evt.put("xpAdded", 0);
            evt.put("awarded", false); // Quota hit
            evt.put("createdAt", Timestamp.now());
            batch.set(xpEventsRef.document(), evt);

            batch.commit()
                    .addOnSuccessListener(aVoid -> listener.onSuccess("Status updated: done (Quota limit reached, +0 XP)"))
                    .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
        };

        // 4. Check Quota Logic
        if (quotaKey == null) {
            // No quota limits (e.g. unique combinations), always award
            runTransaction.run();
        } else {
            // Check count in Firestore
            int limit = quotaLimitForKey(quotaKey);
            xpEventsRef
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("quotaKey", quotaKey)
                    .whereEqualTo("periodKey", periodKey)
                    .whereEqualTo("awarded", true)
                    .get()
                    .addOnSuccessListener(qs -> {
                        int currentCount = (qs != null) ? qs.size() : 0;
                        if (currentCount < limit) {
                            runTransaction.run();
                        } else {
                            runNoXpUpdate.run();
                        }
                    })
                    .addOnFailureListener(e -> listener.onError("Quota check error: " + e.getMessage()));
        }
    }

    // ---------------- Status update with XP processing ----------------

    public void updateTaskStatus(@NonNull String taskId, @NonNull String newStatus, @NonNull OnTaskActionEventListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        tasksRef.document(taskId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        listener.onError("Task not found.");
                        return;
                    }

                    Task t = snapshot.toObject(Task.class);
                    if (t == null || t.getUserId() == null || !uid.equals(t.getUserId())) {
                        listener.onError("You don't have permission to modify this task.");
                        return;
                    }

                    String prev = (t.getStatus() != null && !t.getStatus().isEmpty()) ? t.getStatus() : Task.STATUS_ACTIVE;
                    boolean isCompletion = Task.STATUS_DONE.equals(newStatus) && !Task.STATUS_DONE.equals(prev);

                    // XP processing (only once per task)
                    if (isCompletion && !t.isXpProcessed()) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", newStatus);
                        updates.put("xpProcessed", true);
                        applyCompletionXpIfNeeded(uid, t, taskId, null, updates, listener);
                        return;
                    }

                    // normal update (no XP processing or revert)
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", newStatus);
                    db.collection("tasks").document(taskId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Status updated: " + newStatus))
                            .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
    }

    public void updateTaskOccurrenceStatus(
            @NonNull String taskId,
            @NonNull String dateKey,
            @NonNull String newStatus,
            @NonNull OnTaskActionEventListener listener
    ) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        tasksRef.document(taskId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        listener.onError("Task not found.");
                        return;
                    }

                    Task t = snapshot.toObject(Task.class);
                    if (t == null || t.getUserId() == null || !uid.equals(t.getUserId())) {
                        listener.onError("You don't have permission to modify this task.");
                        return;
                    }

                    String prev = t.getOccurrenceStatusForDateKey(dateKey);
                    if (prev == null || prev.isEmpty()) prev = Task.STATUS_ACTIVE;
                    boolean isCompletion = Task.STATUS_DONE.equals(newStatus) && !Task.STATUS_DONE.equals(prev);

                    // XP processing (only once per occurrence)
                    if (isCompletion && !t.isOccurrenceXpProcessed(dateKey)) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("occurrenceStatuses." + dateKey, newStatus);
                        updates.put("occurrenceXpProcessed." + dateKey, true);
                        applyCompletionXpIfNeeded(uid, t, taskId, dateKey, updates, listener);
                        return;
                    }

                    // normal update
                    Map<String, Object> update = new HashMap<>();
                    update.put("occurrenceStatuses." + dateKey, newStatus);

                    tasksRef.document(taskId)
                            .update(update)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Status updated: " + newStatus))
                            .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
    }

    // ---- callbacks (service-level) ----

    public interface OnTasksLoadedListener {
        void onLoaded(List<Task> tasks);
    }

    public interface OnTaskLoadedListener {
        void onLoaded(Task task);
    }

    public interface OnTaskActionEventListener {
        void onSuccess(String message);
        void onError(String error);
    }
}