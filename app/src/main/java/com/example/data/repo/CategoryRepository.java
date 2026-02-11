package com.example.data.repo;
import com.example.data.model.Category;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

public class CategoryRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference categoriesRef = db.collection("categories");

    public void getAllCategories(OnCategoriesLoadedListener listener) {
        categoriesRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Category> categories = task.getResult().toObjects(Category.class);
                listener.onLoaded(categories);
            }
        });
    }

    public void addCategory(Category category, OnCategoryActionEventListener listener) {
        categoriesRef.whereEqualTo("colorHex", category.getColorHex()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult() != null && task.getResult().isEmpty()) {
                            categoriesRef.add(category)
                                    .addOnSuccessListener(doc -> {
                                        String id = doc.getId();
                                        categoriesRef.document(id).update("id", id);
                                        listener.onSuccess("Kategorija dodata!");
                                    })
                                    .addOnFailureListener(e -> listener.onError("Greška: " + e.getMessage()));
                        } else {
                            listener.onError("Ova boja je već zauzeta!");
                        }
                    } else {
                        listener.onError("Greška u bazi: " + task.getException().getMessage());
                    }
                });
    }

    public void deleteCategory(String categoryId, OnCategoryActionEventListener listener) {
        categoriesRef.document(categoryId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess("Kategorija obrisana."))
                .addOnFailureListener(e -> listener.onError("Greška pri brisanju."));
    }

    public interface OnCategoriesLoadedListener {
        void onLoaded(List<Category> categories);
    }

    public interface OnCategoryActionEventListener {
        void onSuccess(String message);
        void onError(String error);
    }
}