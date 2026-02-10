package com.example.ui.category;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.data.model.Category;
import com.example.data.repo.CategoryRepository;
import java.util.List;

public class CategoryViewModel extends ViewModel {
    private final CategoryRepository repository = new CategoryRepository();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public void fetchCategories() {
        repository.getAllCategories(categories::setValue);
    }

    public void addNewCategory(String name, String color) {
        // Generišemo ID
        Category newCat = new Category(null, name, color);
        repository.addCategory(newCat, new CategoryRepository.OnCategoryActionEventListener() {
            @Override
            public void onSuccess(String message) {
                statusMessage.setValue(message);
                fetchCategories(); // refresh
            }

            @Override
            public void onError(String error) {
                statusMessage.setValue(error);
            }
        });
    }

    public void deleteCategory(String categoryId) {
        repository.deleteCategory(categoryId, new CategoryRepository.OnCategoryActionEventListener() {
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