//package com.example.ui.profile;
//
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.example.myapplication.R;
//import com.example.ui.shop.ShopItem;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class ProfileEquipmentAdapter extends RecyclerView.Adapter<ProfileEquipmentAdapter.ViewHolder> {
//
//    private List<ShopItem> items = new ArrayList<>();
//
//    public void setItems(List<ShopItem> items) {
//        this.items = items;
//        notifyDataSetChanged();
//    }
//
//    @NonNull
//    @Override
//    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_equipment, parent, false);
//        return new ViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
//        ShopItem item = items.get(position);
//        holder.tvName.setText(item.title);
//        holder.tvDesc.setText(item.description);
//        holder.tvCount.setText("x" + item.count);
//        holder.ivIcon.setImageResource(item.imageResId);
//
//        // Ako je item aktivan (npr. odeća koja se nosi), možemo promeniti boju teksta ili slično
//        if (item.isActive) {
//            holder.tvName.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
//            holder.tvDesc.setText(item.description + " (AKTIVNO)");
//        }
//    }
//
//    @Override
//    public int getItemCount() {
//        return items.size();
//    }
//
//    static class ViewHolder extends RecyclerView.ViewHolder {
//        ImageView ivIcon;
//        TextView tvName, tvDesc, tvCount;
//
//        public ViewHolder(@NonNull View itemView) {
//            super(itemView);
//            ivIcon = itemView.findViewById(R.id.ivIcon);
//            tvName = itemView.findViewById(R.id.tvName);
//            tvDesc = itemView.findViewById(R.id.tvDesc);
//            tvCount = itemView.findViewById(R.id.tvCount);
//        }
//    }
//}