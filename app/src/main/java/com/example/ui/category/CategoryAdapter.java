package com.example.ui.category;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R; // Proveri da li je tvoj paket MyApplication ili mzapplication
import com.example.data.model.Category;
import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    private List<Category> categories = new ArrayList<>();
    private final OnCategoryDeleteListener deleteListener;

    public interface OnCategoryDeleteListener {
        void onDelete(Category category);
    }

    public CategoryAdapter(OnCategoryDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.tvName.setText(category.getName());

        // Postavljanje boje iz HEX stringa [cite: 61, 125]
        try {
            holder.vColor.setBackgroundColor(Color.parseColor(category.getColorHex()));
        } catch (Exception e) {
            holder.vColor.setBackgroundColor(Color.GRAY); // Default ako je loš HEX
        }

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(category));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        View vColor;
        TextView tvName;
        ImageButton btnDelete;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            vColor = itemView.findViewById(R.id.viewCategoryColor);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            btnDelete = itemView.findViewById(R.id.btnDeleteCategory);
        }
    }
}