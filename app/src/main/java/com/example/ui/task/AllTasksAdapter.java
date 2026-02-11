package com.example.ui.task;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.myapplication.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AllTasksAdapter extends RecyclerView.Adapter<AllTasksAdapter.VH> {

    public static class DisplayItem {
        public final Task task;
        public final String occurrenceDateKey; // for recurring only (yyyy-MM-dd), null for single

        public DisplayItem(Task task, String occurrenceDateKey) {
            this.task = task;
            this.occurrenceDateKey = occurrenceDateKey;
        }
    }

    public interface OnTaskClick { void onClick(Task t, String occurrenceKey); }
    public interface OnTaskLongClick { void onLongClick(Task t, String occurrenceKey); }
    public interface OnQuickDoneToggle { void onToggle(Task t, String occurrenceKey, boolean isDone); }
    public interface OnStatusClick { void onClick(Task t, String occurrenceKey); }

    private final OnTaskClick onTaskClick;
    private final OnTaskLongClick onTaskLongClick;
    private final OnQuickDoneToggle onQuickDoneToggle;
    private final OnStatusClick onStatusClick;

    private List<DisplayItem> items = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();

    public AllTasksAdapter(
            OnTaskClick onTaskClick,
            OnTaskLongClick onTaskLongClick,
            OnQuickDoneToggle onQuickDoneToggle,
            OnStatusClick onStatusClick
    ) {
        this.onTaskClick = onTaskClick;
        this.onTaskLongClick = onTaskLongClick;
        this.onQuickDoneToggle = onQuickDoneToggle;
        this.onStatusClick = onStatusClick;
    }

    public void setData(List<DisplayItem> items, List<Category> categories) {
        this.items = items != null ? items : new ArrayList<>();
        this.categories = categories != null ? categories : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DisplayItem item = items.get(position);
        Task t = item.task;
        String occKey = item.occurrenceDateKey;

        h.tvTitle.setText(t.getName() != null ? t.getName() : "");

        // vreme prikaza
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = "--:--";
        if (Task.TYPE_SINGLE.equals(t.getType()) && t.getExecutionTime() != null) {
            time = timeFmt.format(t.getExecutionTime().toDate());
        } else if (Task.TYPE_RECURRING.equals(t.getType()) && t.getStartDate() != null) {
            // prikazujemo vreme iz startDate (sat:min) jer je to “time of day” za recurring
            time = timeFmt.format(t.getStartDate().toDate());
        }
        h.tvTime.setText(time);

        // status prikaza
        String status = Task.STATUS_ACTIVE;
        if (Task.TYPE_RECURRING.equals(t.getType())) {
            if (occKey != null) {
                String occ = t.getOccurrenceStatusForDateKey(occKey);
                status = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
            } else {
                status = Task.STATUS_ACTIVE;
            }
        } else {
            if (t.getStatus() != null && !t.getStatus().isEmpty()) status = t.getStatus();
        }
        h.tvStatus.setText(status);

        // checkbox (quick done)
        h.cbDone.setOnCheckedChangeListener(null);
        h.cbDone.setChecked(Task.STATUS_DONE.equals(status));
        h.cbDone.setOnClickListener(v -> {
            if (onQuickDoneToggle != null) onQuickDoneToggle.onToggle(t, occKey, h.cbDone.isChecked());
        });

        // status click => full picker
        h.tvStatus.setOnClickListener(v -> {
            if (onStatusClick != null) onStatusClick.onClick(t, occKey);
        });

        // boja kategorije
        h.viewColor.setBackgroundColor(Color.GRAY);
        for (Category c : categories) {
            if (c.getId() != null && c.getId().equals(t.getCategoryId())) {
                try {
                    h.viewColor.setBackgroundColor(Color.parseColor(c.getColorHex()));
                } catch (Exception ignored) {}
                break;
            }
        }

        h.itemView.setOnClickListener(v -> {
            if (onTaskClick != null) onTaskClick.onClick(t, occKey);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (onTaskLongClick != null) onTaskLongClick.onLongClick(t, occKey);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View viewColor;
        TextView tvTitle, tvTime, tvStatus;
        CheckBox cbDone;

        VH(@NonNull View itemView) {
            super(itemView);
            viewColor = itemView.findViewById(R.id.viewCategoryColorStrip);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTime = itemView.findViewById(R.id.tvTaskTime);
            tvStatus = itemView.findViewById(R.id.tvTaskStatus);
            cbDone = itemView.findViewById(R.id.cbDone);
        }
    }
}
