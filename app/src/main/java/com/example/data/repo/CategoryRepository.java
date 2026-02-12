package com.example.data.repo;

import androidx.annotation.NonNull;

import com.example.data.model.Category;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.List;

public class CategoryRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference categoriesRef = db.collection("categories");

    // Samo dohvata podatke, nema logike
    public void getAllCategories(OnCategoriesLoadedListener listener) {
        categoriesRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Category> categories = task.getResult().toObjects(Category.class);
                listener.onLoaded(categories);
            } else {
                listener.onLoaded(Collections.emptyList());
            }
        });
    }

    // Pomoćna metoda za Service da proveri da li boja postoji
    public void findCategoryByColor(String hex, OnCategoriesLoadedListener listener) {
        categoriesRef.whereEqualTo("colorHex", hex).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Category> categories = task.getResult().toObjects(Category.class);
                        listener.onLoaded(categories);
                    } else {
                        listener.onLoaded(Collections.emptyList());
                    }
                });
    }

    // Samo upisuje u bazu
    public void addCategory(Category category, OnCategoryActionEventListener listener) {
        categoriesRef.add(category)
                .addOnSuccessListener(doc -> {
                    String id = doc.getId();
                    // Ažuriramo ID polje unutar dokumenta
                    categoriesRef.document(id).update("id", id)
                            .addOnSuccessListener(aVoid -> listener.onSuccess("Kategorija dodata!"))
                            .addOnFailureListener(e -> listener.onError("Greška pri ažuriranju ID-a: " + e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onError("Greška: " + e.getMessage()));
    }

    public void deleteCategory(String categoryId, OnCategoryActionEventListener listener) {
        categoriesRef.document(categoryId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess("Kategorija obrisana."))
                .addOnFailureListener(e -> listener.onError("Greška pri brisanju."));
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