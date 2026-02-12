package com.example.data.service;

import androidx.annotation.NonNull;

import com.example.data.model.Category;
import com.example.data.repo.CategoryRepository;

import java.util.List;

public class CategoryService {

    private final CategoryRepository repository;

    public CategoryService() {
        this.repository = new CategoryRepository();
    }

    public void getAllCategories(CategoryRepository.OnCategoriesLoadedListener listener) {
        repository.getAllCategories(listener);
    }

    public void addCategory(String name, String colorHex, CategoryRepository.OnCategoryActionEventListener listener) {
        // 1. Validacija unosa
        if (name == null || name.trim().isEmpty()) {
            listener.onError("Naziv kategorije ne sme biti prazan.");
            return;
        }
        if (colorHex == null || !colorHex.startsWith("#") || colorHex.length() < 4) {
            listener.onError("Neispravan format boje (mora biti HEX, npr. #FF0000).");
            return;
        }

        // 2. Provera da li boja već postoji (Poslovna logika)
        repository.findCategoryByColor(colorHex, existingCategories -> {
            if (existingCategories != null && !existingCategories.isEmpty()) {
                listener.onError("Kategorija sa ovom bojom već postoji!");
            } else {
                // 3. Ako je sve ok, kreiraj objekat i zovi repo
                Category newCategory = new Category(null, name.trim(), colorHex.trim());
                repository.addCategory(newCategory, listener);
            }
        });
    }

    public void deleteCategory(String categoryId, CategoryRepository.OnCategoryActionEventListener listener) {
        if (categoryId == null || categoryId.isEmpty()) {
            listener.onError("ID kategorije nedostaje.");
            return;
        }
        // Ovde bi se mogla dodati provera: "Da li postoje taskovi sa ovom kategorijom?" pre brisanja.
        // Za sada samo brišemo.
        repository.deleteCategory(categoryId, listener);
    }
}