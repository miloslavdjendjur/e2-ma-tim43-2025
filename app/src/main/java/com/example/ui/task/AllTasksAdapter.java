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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AllTasksAdapter extends RecyclerView.Adapter<AllTasksAdapter.VH> {

    public static class DisplayItem {
        public final Task task;
        public final String occurrenceDateKey; // recurring only (yyyy-MM-dd) OR single derived dateKey (optional)

        public DisplayItem(Task task, String occurrenceDateKey) {
            this.task = task;
            this.occurrenceDateKey = occurrenceDateKey;
        }
    }

    public interface OnTaskClick { void onClick(Task t, String occurrenceKey); }
    public interface OnTaskLongClick { void onLongClick(View anchor, Task t, String occurrenceKey); }
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

        // ---- Time label: dd.MM • HH:mm (za recurring koristi occKey + startDate time-of-day) ----
        String datePart = "";
        String timePart = "--:--";

        if (Task.TYPE_SINGLE.equals(t.getType())) {
            if (t.getExecutionTime() != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(t.getExecutionTime().toDate());
                datePart = new SimpleDateFormat("dd.MM", Locale.getDefault()).format(c.getTime());
                timePart = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.getTime());
            }
        } else if (Task.TYPE_RECURRING.equals(t.getType())) {
            if (occKey != null) {
                datePart = toDdMm(occKey);
            }
            if (t.getStartDate() != null) {
                timePart = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(t.getStartDate().toDate());
            }
        }

        if (!datePart.isEmpty()) h.tvTime.setText(datePart + " • " + timePart);
        else h.tvTime.setText(timePart);

        // ---- Status ----
        String status = Task.STATUS_ACTIVE;
        boolean isRecurring = Task.TYPE_RECURRING.equals(t.getType());

        if (isRecurring) {
            if (occKey != null) {
                String occ = t.getOccurrenceStatusForDateKey(occKey);
                status = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
            }
        } else {
            if (t.getStatus() != null && !t.getStatus().isEmpty()) {
                status = t.getStatus();
            }
        }

        // Label
        String typeLabel = isRecurring ? "Recurring" : "One-time";
        h.tvStatus.setText(typeLabel + " • " + status);

        // ---- checkbox quick done ----
        h.cbDone.setOnCheckedChangeListener(null);
        boolean done = Task.STATUS_DONE.equals(status);
        h.cbDone.setChecked(done);

        h.cbDone.setOnClickListener(v -> {
            if (onQuickDoneToggle != null) onQuickDoneToggle.onToggle(t, occKey, h.cbDone.isChecked());
        });

        // ---- status click (picker) ----
        h.tvStatus.setOnClickListener(v -> {
            if (onStatusClick != null) onStatusClick.onClick(t, occKey);
        });

        // ---- category color ----
        h.viewColor.setBackgroundColor(Color.GRAY);
        for (Category c : categories) {
            if (c.getId() != null && c.getId().equals(t.getCategoryId())) {
                try {
                    h.viewColor.setBackgroundColor(Color.parseColor(c.getColorHex()));
                } catch (Exception ignored) {}
                break;
            }
        }

        // vizuelno priguši DONE
        float alpha = done ? 0.45f : 1f;
        h.itemView.setAlpha(alpha);

        h.itemView.setOnClickListener(v -> {
            if (onTaskClick != null) onTaskClick.onClick(t, occKey);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (onTaskLongClick != null) onTaskLongClick.onLongClick(h.itemView, t, occKey);
            return true;
        });
    }


    @Override
    public int getItemCount() { return items.size(); }

    private String toDdMm(String yyyyMmDd) {
        try {
            String[] p = yyyyMmDd.split("-");
            int m = Integer.parseInt(p[1]);
            int d = Integer.parseInt(p[2]);
            return String.format(Locale.getDefault(), "%02d.%02d", d, m);
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

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
