package com.example.ui.shop;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView; // <--- OVO JE FALILO
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import java.util.List;

public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {

    private List<ShopItem> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ShopItem item);
    }

    public ShopAdapter(List<ShopItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void updateList(List<ShopItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shop_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShopItem item = items.get(position);

        holder.tvTitle.setText(item.title);
        holder.tvDesc.setText(item.description);

        holder.ivIcon.setImageResource(item.imageResId);

        if (item.isShopItem) {
            holder.tvInfo.setText("Cena: " + item.price + " 💰");
            holder.btnAction.setText("Kupi");
            holder.btnAction.setEnabled(true);
            holder.btnAction.setAlpha(1.0f);
            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50")); // Zeleno
        } else {
            holder.tvInfo.setText("Poseduješ: " + item.count);

            if ("WEAPON".equals(item.typeCategory)) {
                holder.btnAction.setText("Trajno");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setAlpha(0.6f);
                holder.btnAction.setBackgroundColor(Color.GRAY);
            } else {
                holder.btnAction.setText("Aktiviraj");
                if (item.count > 0) {
                    holder.btnAction.setEnabled(true);
                    holder.btnAction.setAlpha(1.0f);
                    holder.btnAction.setBackgroundColor(Color.parseColor("#2196F3")); // Plavo
                } else {
                    holder.btnAction.setEnabled(false);
                    holder.btnAction.setAlpha(0.5f);
                    holder.btnAction.setBackgroundColor(Color.GRAY);
                }
            }
        }

        holder.btnAction.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvInfo;
        ImageView ivIcon;
        Button btnAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}