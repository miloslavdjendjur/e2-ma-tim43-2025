package com.example.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CategoryFragment extends Fragment {

    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;

    public CategoryFragment() {
        super(R.layout.fragment_categories);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvCategories);
        FloatingActionButton fab = view.findViewById(R.id.fabAddCategory);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new CategoryAdapter(category -> viewModel.deleteCategory(category.getId()));
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            adapter.setCategories(categories);
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.fetchCategories();

        fab.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null);

        EditText etName = dialogView.findViewById(R.id.etCategoryName);
        EditText etColor = dialogView.findViewById(R.id.etCategoryColor);

        builder.setView(dialogView)
                .setTitle("Nova Kategorija")
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String color = etColor.getText().toString().trim();

                    if (!name.isEmpty() && color.startsWith("#")) {
                        viewModel.addNewCategory(name, color);
                    } else {
                        Toast.makeText(requireContext(), "Unesite ispravne podatke!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Odustani", null)
                .create()
                .show();
    }
}
