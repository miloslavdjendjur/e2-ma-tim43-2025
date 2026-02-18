package com.example.data.repo;

import androidx.annotation.NonNull;

import com.example.data.model.Category;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.List;

public class CategoryRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference categoriesRef = db.collection("categories");

    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    // Samo dohvata podatke, nema logike
    public void getAllCategories(OnCategoriesLoadedListener listener) {
        String uid = currentUid();
        if (uid == null) {
            listener.onLoaded(Collections.emptyList());
            return;
        }

        // VAŽNO: čitaš samo svoje kategorije (rules traže resource.data.userId == uid)
        categoriesRef.whereEqualTo("userId", uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<Category> categories = task.getResult().toObjects(Category.class);
                listener.onLoaded(categories);
            } else {
                listener.onLoaded(Collections.emptyList());
            }
        });
    }

    // Pomoćna metoda za Service da proveri da li boja postoji
    public void findCategoryByColor(String hex, OnCategoriesLoadedListener listener) {
        String uid = currentUid();
        if (uid == null) {
            listener.onLoaded(Collections.emptyList());
            return;
        }

        // VAŽNO: provera duplikata boje samo unutar tvojih kategorija
        categoriesRef
                .whereEqualTo("userId", uid)
                .whereEqualTo("colorHex", hex)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<Category> categories = task.getResult().toObjects(Category.class);
                        listener.onLoaded(categories);
                    } else {
                        listener.onLoaded(Collections.emptyList());
                    }
                });
    }

    // Samo upisuje u bazu
    public void addCategory(Category category, OnCategoryActionEventListener listener) {
        String uid = currentUid();
        if (uid == null) {
            listener.onError("Not logged in.");
            return;
        }

        // VAŽNO: upisujemo userId da bi rules pustio create/read
        category.setUserId(uid);

        categoriesRef.add(category)
                .addOnSuccessListener(doc -> {
                    String id = doc.getId();
                    categoriesRef.document(id).update("id", id)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Category added!"))
                            .addOnFailureListener(e -> listener.onError("Error while updating ID: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Error: " + e.getMessage()));
    }

    public void deleteCategory(String categoryId, OnCategoryActionEventListener listener) {
        categoriesRef.document(categoryId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess("Category deleted."))
                .addOnFailureListener(e -> listener.onError("Error while deleting."));
    }

    // Interfejsi
    public interface OnCategoriesLoadedListener {
        void onLoaded(List<Category> categories);
    }

    public interface OnCategoryActionEventListener {
        void onSuccess(String message);
        void onError(String error);
    }
}
