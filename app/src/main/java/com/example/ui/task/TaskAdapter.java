package com.example.ui.task;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.data.model.Task;
import com.example.data.model.Category;
import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private OnTaskStatusChangeListener listener;

    public interface OnTaskStatusChangeListener {
        void onStatusChange(Task task, boolean isDone);
    }

    public TaskAdapter(OnTaskStatusChangeListener listener) {
        this.listener = listener;
    }

    public void setData(List<Task> tasks, List<Category> categories) {
        this.tasks = tasks;
        this.categories = categories;
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
        holder.cbDone.setChecked(task.getStatus().equals("urađen"));

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

        holder.cbDone.setOnClickListener(v -> listener.onStatusChange(task, holder.cbDone.isChecked()));
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        View viewColor;
        TextView tvTitle;
        CheckBox cbDone;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            viewColor = itemView.findViewById(R.id.viewCategoryColorStrip);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            cbDone = itemView.findViewById(R.id.cbDone);
        }
    }
}