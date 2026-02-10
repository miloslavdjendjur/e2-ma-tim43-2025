package com.example.ui.category;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CategoryActivity extends AppCompatActivity {

    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        RecyclerView rv = findViewById(R.id.rvCategories);
        FloatingActionButton fab = findViewById(R.id.fabAddCategory);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategoryAdapter(category -> {
            viewModel.deleteCategory(category.getId());
        });
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        viewModel.getCategories().observe(this, categories -> {
            adapter.setCategories(categories);
        });

        viewModel.getStatusMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.fetchCategories();

        fab.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_category, null);

        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etColor = view.findViewById(R.id.etCategoryColor);

        builder.setView(view)
                .setTitle("Nova Kategorija")
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String color = etColor.getText().toString().trim();

                    if (!name.isEmpty() && color.startsWith("#")) {
                        viewModel.addNewCategory(name, color);
                    } else {
                        Toast.makeText(this, "Unesite ispravne podatke!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Odustani", null)
                .create()
                .show();
    }
}