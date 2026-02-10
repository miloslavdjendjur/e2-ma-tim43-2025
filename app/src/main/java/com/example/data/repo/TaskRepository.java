package com.example.data.repo;

import android.util.Log;

import com.example.data.model.Task;
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
            listener.onError("Korisnik nije ulogovan.");
            return;
        }

        task.setUserId(uid);
        task.setStatus("aktivan");

        tasksRef.add(task)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    tasksRef.document(id).update("id", id);
                    listener.onSuccess("Zadatak uspešno sačuvan!");
                })
                .addOnFailureListener(e -> listener.onError("Greška pri čuvanju: " + e.getMessage()));
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

    public void updateTaskStatus(String taskId, String newStatus, OnTaskActionEventListener listener) {
        tasksRef.document(taskId).update("status", newStatus)
                .addOnSuccessListener(aVoid -> listener.onSuccess("Status ažuriran: " + newStatus))
                .addOnFailureListener(e -> listener.onError("Greška: " + e.getMessage()));
    }

    public interface OnTasksLoadedListener {
        void onLoaded(List<Task> tasks);
    }

    public interface OnTaskActionEventListener {
        void onSuccess(String message);
        void onError(String error);
    }
}
