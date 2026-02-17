package com.example.ui.alliance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MemberContributionAdapter extends RecyclerView.Adapter<MemberContributionAdapter.VH> {

    public static class Row {
        public final String uid;
        public final String name;
        public final long hpDealt;

        public Row(String uid, String name, long hpDealt) {
            this.uid = uid;
            this.name = name;
            this.hpDealt = hpDealt;
        }
    }

    private final List<Row> items = new ArrayList<>();
    private long maxHp = 1;

    public void setData(List<Row> rows) {
        items.clear();
        if (rows != null) items.addAll(rows);

        // sort desc by contribution
        Collections.sort(items, (a, b) -> Long.compare(b.hpDealt, a.hpDealt));

        maxHp = 1;
        for (Row r : items) maxHp = Math.max(maxHp, r.hpDealt);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member_contribution, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = items.get(position);
        h.tvName.setText(r.name != null ? r.name : r.uid);
        h.tvValue.setText(String.valueOf(r.hpDealt));

        int pct = (maxHp <= 0) ? 0 : (int) Math.round((r.hpDealt * 100.0) / maxHp);
        h.pbBar.setProgress(Math.max(0, Math.min(100, pct)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvValue;
        ProgressBar pbBar;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvValue = itemView.findViewById(R.id.tvValue);
            pbBar = itemView.findViewById(R.id.pbBar);
        }
    }
}
