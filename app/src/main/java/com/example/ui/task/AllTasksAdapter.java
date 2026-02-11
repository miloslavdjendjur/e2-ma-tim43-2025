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

    public interface OnTaskClick { void onClick(Task t); }
    public interface OnTaskLongClick { void onLongClick(Task t); }
    public interface OnQuickDoneToggle { void onToggle(Task t, boolean isDone); }
    public interface OnStatusClick { void onClick(Task t); }

    private final OnTaskClick onTaskClick;
    private final OnTaskLongClick onTaskLongClick;
    private final OnQuickDoneToggle onQuickDoneToggle;
    private final OnStatusClick onStatusClick;

    private List<Task> tasks = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private String dateKeyForRecurring = null;

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

    public void setData(List<Task> tasks, List<Category> categories, String dateKeyForRecurring) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        this.categories = categories != null ? categories : new ArrayList<>();
        this.dateKeyForRecurring = dateKeyForRecurring;
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
        Task t = tasks.get(position);

        h.tvTitle.setText(t.getName() != null ? t.getName() : "");

        // vreme prikaza
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = "--:--";
        if (Task.TYPE_SINGLE.equals(t.getType()) && t.getExecutionTime() != null) {
            time = timeFmt.format(t.getExecutionTime().toDate());
        } else if (Task.TYPE_RECURRING.equals(t.getType()) && t.getStartDate() != null) {
            time = timeFmt.format(t.getStartDate().toDate());
        }
        h.tvTime.setText(time);

        // status prikaza
        String status = Task.STATUS_ACTIVE;
        if (Task.TYPE_RECURRING.equals(t.getType())) {
            String occ = t.getOccurrenceStatusForDateKey(dateKeyForRecurring);
            status = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
        } else {
            if (t.getStatus() != null && !t.getStatus().isEmpty()) status = t.getStatus();
        }
        h.tvStatus.setText(status);

        // checkbox (quick done)
        h.cbDone.setOnCheckedChangeListener(null);
        h.cbDone.setChecked(Task.STATUS_DONE.equals(status));
        h.cbDone.setOnClickListener(v -> {
            if (onQuickDoneToggle != null) onQuickDoneToggle.onToggle(t, h.cbDone.isChecked());
        });

        // status click => full picker
        h.tvStatus.setOnClickListener(v -> {
            if (onStatusClick != null) onStatusClick.onClick(t);
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
            if (onTaskClick != null) onTaskClick.onClick(t);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (onTaskLongClick != null) onTaskLongClick.onLongClick(t);
            return true;
        });
    }

    @Override
    public int getItemCount() { return tasks.size(); }

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
