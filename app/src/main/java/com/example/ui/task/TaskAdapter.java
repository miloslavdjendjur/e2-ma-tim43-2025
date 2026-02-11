package com.example.ui.task;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.data.model.Task;
import com.example.data.model.Category;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();

    private final OnTaskStatusChangeListener statusListener;
    private final OnTaskActionsListener actionsListener;

    public interface OnTaskStatusChangeListener {
        void onStatusChange(Task task, boolean isDone);
    }

    public interface OnTaskActionsListener {
        void onEdit(Task task);
        void onDelete(Task task);
    }

    public TaskAdapter(OnTaskStatusChangeListener statusListener, OnTaskActionsListener actionsListener) {
        this.statusListener = statusListener;
        this.actionsListener = actionsListener;
    }

    public void setData(List<Task> tasks, List<Category> categories) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        this.categories = categories != null ? categories : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);

        holder.tvTitle.setText(task.getName());
        holder.cbDone.setChecked("done".equals(task.getStatus()));

        // Vreme (SINGLE -> executionTime, RECURRING -> startDate)
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String formattedTime = "--:--";

        if ("SINGLE".equals(task.getType()) && task.getExecutionTime() != null) {
            formattedTime = sdf.format(task.getExecutionTime().toDate());
        } else if ("RECURRING".equals(task.getType()) && task.getStartDate() != null) {
            formattedTime = sdf.format(task.getStartDate().toDate());
        }
        holder.tvTime.setText(formattedTime);

        // Boja kategorije
        holder.viewColor.setBackgroundColor(Color.GRAY);
        for (Category c : categories) {
            if (c.getId() != null && c.getId().equals(task.getCategoryId())) {
                try {
                    holder.viewColor.setBackgroundColor(Color.parseColor(c.getColorHex()));
                } catch (Exception e) {
                    holder.viewColor.setBackgroundColor(Color.GRAY);
                }
                break;
            }
        }

        holder.cbDone.setOnClickListener(v -> {
            if (statusListener != null) {
                statusListener.onStatusChange(task, holder.cbDone.isChecked());
            }
        });

        // Tap = Edit
        holder.itemView.setOnClickListener(v -> {
            if (actionsListener != null) actionsListener.onEdit(task);
        });

        // Long press = menu (Edit/Delete)
        holder.itemView.setOnLongClickListener(v -> {
            showPopupMenu(holder, task);
            return true;
        });
    }

    private void showPopupMenu(TaskViewHolder holder, Task task) {
        if (actionsListener == null) return;

        PopupMenu popup = new PopupMenu(holder.itemView.getContext(), holder.itemView);
        popup.getMenu().add(0, 1, 0, "Edit");
        popup.getMenu().add(0, 2, 1, "Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                actionsListener.onEdit(task);
                return true;
            } else if (item.getItemId() == 2) {
                actionsListener.onDelete(task);
                return true;
            }
            return false;
        });

        popup.show();
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        View viewColor;
        TextView tvTitle, tvTime;
        CheckBox cbDone;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            viewColor = itemView.findViewById(R.id.viewCategoryColorStrip);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTime = itemView.findViewById(R.id.tvTaskTime);
            cbDone = itemView.findViewById(R.id.cbDone);
        }
    }
}
