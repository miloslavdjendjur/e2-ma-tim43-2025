package com.example.data.repo;

import com.example.data.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.List;

public class TaskRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference tasksRef = db.collection("tasks");
    private final String userId;

    public TaskRepository() {
        // Svaki zadatak mora biti vezan za UID trenutno ulogovanog korisnika
        this.userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    // 2.1. Kreiranje zadatka [cite: 57, 58]
    public void addTask(Task task, OnTaskActionEventListener listener) {
        if (userId == null) {
            listener.onError("Korisnik nije ulogovan.");
            return;
        }

        task.setUserId(userId);
        task.setStatus("aktivan"); // Svaki kreiran zadatak je automatski aktivan

        tasksRef.add(task)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    tasksRef.document(id).update("id", id);
                    listener.onSuccess("Zadatak uspešno sačuvan!");
                })
                .addOnFailureListener(e -> listener.onError("Greška pri čuvanju: " + e.getMessage()));
    }

    // 2.2. Pregled svih zadataka [cite: 84, 85]
    public void getTasks(OnTasksLoadedListener listener) {
        if (userId == null) return;

        tasksRef.whereEqualTo("userId", userId)
                .orderBy("executionTime", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Task> tasks = queryDocumentSnapshots.toObjects(Task.class);
                    listener.onLoaded(tasks);
                });
    }

    // Metoda za promenu statusa (Urađen, Otkazan, Pauziran) [cite: 109]
    public void updateTaskStatus(String taskId, String newStatus, OnTaskActionEventListener listener) {
        // Ovde ćemo kasnije dodati logiku za dodelu XP-a ako je status "urađen"
        // Uzimajući u obzir kvote iz specifikacije [cite: 75, 76, 77, 78]
        tasksRef.document(taskId).update("status", newStatus)
                .addOnSuccessListener(aVoid -> listener.onSuccess("Status ažuriran: " + newStatus))
                .addOnFailureListener(e -> listener.onError("Greška: " + e.getMessage()));
    }

    // Interfejsi za komunikaciju sa ViewModel-om
    public interface OnTasksLoadedListener {
        void onLoaded(List<Task> tasks);
    }

    public interface OnTaskActionEventListener {
        void onSuccess(String message);
        void onError(String error);
    }
}