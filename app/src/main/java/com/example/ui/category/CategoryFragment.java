package com.example.ui.category;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

import yuku.ambilwarna.AmbilWarnaDialog;

public class CategoryFragment extends Fragment {

    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;

    // default (tvoj theme vibe)
    private String selectedColorHex = "#84dcc6";

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

        viewModel.getCategories().observe(getViewLifecycleOwner(), adapter::setCategories);

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.fetchCategories();
        fab.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void showAddCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_category, null);

        EditText etName = dialogView.findViewById(R.id.etCategoryName);
        EditText etColor = dialogView.findViewById(R.id.etCategoryColor);
        View preview = dialogView.findViewById(R.id.viewColorPreview);
        Button btnPick = dialogView.findViewById(R.id.btnPickColor);

        // init
        etColor.setText(selectedColorHex);
        try {
            preview.setBackgroundColor(Color.parseColor(selectedColorHex));
        } catch (Exception ignored) {}

        btnPick.setOnClickListener(v -> openColorPicker(etColor, preview));
        preview.setOnClickListener(v -> openColorPicker(etColor, preview));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("New category")
                .setView(dialogView)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                // stavljamo dummy, pa override da dialog ne nestane na invalid input
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String color = etColor.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter the category name.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // normalizuj: ako user ukuca bez #
                if (!color.startsWith("#")) color = "#" + color;

                // basic validacija HEX formata
                if (!isValidHexColor(color)) {
                    Toast.makeText(requireContext(), "Please enter a valid hex code (e.g. #FF5733).", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedColorHex = color;
                viewModel.addNewCategory(name, color);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void openColorPicker(EditText etColor, View preview) {
        int initialColor;
        try {
            initialColor = Color.parseColor(selectedColorHex);
        } catch (Exception e) {
            initialColor = Color.parseColor("#84dcc6");
        }

        new AmbilWarnaDialog(requireContext(), initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                selectedColorHex = String.format("#%06X", (0xFFFFFF & color));
                etColor.setText(selectedColorHex);
                preview.setBackgroundColor(color);
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // no-op
            }
        }).show();
    }

    private boolean isValidHexColor(String color) {
        if (color == null) return false;
        // #RGB ili #RRGGBB
        return color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }
}
