package com.example.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.data.model.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference tasksRef = db.collection("tasks");

    private String getUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void addTask(Task task, OnTaskActionEventListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        task.setUserId(uid);

        // default statuses
        if (task.getType() == null || task.getType().isEmpty()) {
            task.setType(Task.TYPE_SINGLE);
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus(Task.STATUS_ACTIVE);
        }

        // Ensure map exists for recurring (optional but nice)
        if (Task.TYPE_RECURRING.equals(task.getType()) && task.getOccurrenceStatuses() == null) {
            task.setOccurrenceStatuses(new HashMap<>());
        }

        tasksRef.add(task)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    tasksRef.document(id).update("id", id)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Task saved successfully!"))
                            .addOnFailureListener(e -> listener.onError("Save error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Save error: " + e.getMessage()));
    }

    public void getTasks(OnTasksLoadedListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onLoaded(Collections.emptyList());
            return;
        }

        tasksRef.whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Task> tasks = queryDocumentSnapshots.toObjects(Task.class);
                    listener.onLoaded(tasks);
                })
                .addOnFailureListener(e -> {
                    Log.e("TaskRepository", "getTasks failed", e);
                    listener.onLoaded(Collections.emptyList());
                });
    }

    public void getTaskById(@NonNull String taskId, OnTaskLoadedListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onLoaded(null);
            return;
        }

        tasksRef.document(taskId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        listener.onLoaded(null);
                        return;
                    }
                    Task t = snapshot.toObject(Task.class);
                    if (t != null && uid.equals(t.getUserId())) listener.onLoaded(t);
                    else listener.onLoaded(null);
                })
                .addOnFailureListener(e -> {
                    Log.e("TaskRepository", "getTaskById failed", e);
                    listener.onLoaded(null);
                });
    }

    /**
     * IMPORTANT: use merge so you don't wipe occurrenceStatuses when editing base fields.
     */
    public void updateTask(@NonNull Task task, OnTaskActionEventListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }
        if (task.getId() == null || task.getId().trim().isEmpty()) {
            listener.onError("Task id is missing.");
            return;
        }

        task.setUserId(uid);

        tasksRef.document(task.getId())
                .set(task, SetOptions.merge())
                .addOnSuccessListener(aVoid -> listener.onSuccess("Task updated successfully!"))
                .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
    }

    public void deleteTask(@NonNull String taskId, OnTaskActionEventListener listener) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        tasksRef.document(taskId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        listener.onError("Task not found.");
                        return;
                    }
                    Task t = snapshot.toObject(Task.class);
                    if (t == null || t.getUserId() == null || !uid.equals(t.getUserId())) {
                        listener.onError("You don't have permission to delete this task.");
                        return;
                    }

                    tasksRef.document(taskId)
                            .delete()
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Task deleted."))
                            .addOnFailureListener(e -> listener.onError("Delete error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Delete error: " + e.getMessage()));
    }

    public void updateTaskStatus(String taskId, String newStatus, OnTaskActionEventListener listener) {
        tasksRef.document(taskId).update("status", newStatus)
                .addOnSuccessListener(aVoid -> listener.onSuccess("Status updated: " + newStatus))
                .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
    }

    /**
     * For recurring tasks: update status for a specific date occurrence (yyyy-MM-dd).
     * Stored in: occurrenceStatuses.{dateKey} = newStatus
     */
    public void updateTaskOccurrenceStatus(
            @NonNull String taskId,
            @NonNull String dateKey,
            @NonNull String newStatus,
            OnTaskActionEventListener listener
    ) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        tasksRef.document(taskId).get()
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

                    String fieldPath = "occurrenceStatuses." + dateKey;
                    Map<String, Object> update = new HashMap<>();
                    update.put(fieldPath, newStatus);

                    tasksRef.document(taskId)
                            .update(update)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Status updated: " + newStatus))
                            .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
    }

    /**
     * Spec: delete one occurrence => remove future occurrences, keep past occurrences visible.
     * Implementation: set endDate to cut-off timestamp.
     */
    public void truncateRecurringFromDate(
            @NonNull String taskId,
            @NonNull Timestamp newEndDate,
            OnTaskActionEventListener listener
    ) {
        String uid = getUserId();
        if (uid == null) {
            listener.onError("User not logged in.");
            return;
        }

        tasksRef.document(taskId).get()
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

                    tasksRef.document(taskId)
                            .update("endDate", newEndDate)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Future occurrences removed."))
                            .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
    }

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
