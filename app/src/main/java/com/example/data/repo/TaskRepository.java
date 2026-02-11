package com.example.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.data.model.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.List;

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
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus(Task.STATUS_ACTIVE);
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
                .set(task)
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

                    tasksRef.document(taskId)
                            .update("occurrenceStatuses." + dateKey, newStatus)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Occurrence status updated: " + newStatus))
                            .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Update error: " + e.getMessage()));
    }

    public void truncateRecurringFromDate(@NonNull String taskId, @NonNull Timestamp newEndDate, OnTaskActionEventListener listener) {
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
