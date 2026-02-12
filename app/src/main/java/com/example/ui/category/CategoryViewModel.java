package com.example.ui.category;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.data.model.Category;
import com.example.data.repo.CategoryRepository; // Treba nam zbog interfejsa listenera
import com.example.data.service.CategoryService; // Koristimo Service

import java.util.List;

public class CategoryViewModel extends ViewModel {

    // Koristimo Service
    private final CategoryService service = new CategoryService();

    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public void fetchCategories() {
        service.getAllCategories(categories::setValue);
    }

    public void addNewCategory(String name, String color) {
        // Sva logika oko provere duplikata i validacije je sada u servisu
        service.addCategory(name, color, new CategoryRepository.OnCategoryActionEventListener() {
            @Override
            public void onSuccess(String message) {
                statusMessage.setValue(message);
                fetchCategories(); // osveži listu
            }

            @Override
            public void onError(String error) {
                statusMessage.setValue(error);
            }
        });
    }

    public void deleteCategory(String categoryId) {
        service.deleteCategory(categoryId, new CategoryRepository.OnCategoryActionEventListener() {
            @Override
            public void onSuccess(String message) {
                statusMessage.setValue(message);
                fetchCategories();
            }

            @Override
            public void onError(String error) {
                statusMessage.setValue(error);
            }
        });
    }
}