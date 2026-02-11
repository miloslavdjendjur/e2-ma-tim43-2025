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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        holder.cbDone.setChecked(task.getStatus().equals("done"));

        // Postavljanje dinamičkog vremena iz baze [cite: 178, 180, 226]
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String formattedTime = "--:--";

        if ("SINGLE".equals(task.getType()) && task.getExecutionTime() != null) {
            formattedTime = sdf.format(task.getExecutionTime().toDate());
        } else if ("RECURRING".equals(task.getType()) && task.getStartDate() != null) {
            formattedTime = sdf.format(task.getStartDate().toDate());
        }
        holder.tvTime.setText(formattedTime);

        // Postavljanje boje kategorije [cite: 173, 223]
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
        TextView tvTitle, tvTime;
        CheckBox cbDone;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            // Povezivanje UI elemenata definisanih u item_task.xml [cite: 142, 180]
            viewColor = itemView.findViewById(R.id.viewCategoryColorStrip);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTime = itemView.findViewById(R.id.tvTaskTime);
            cbDone = itemView.findViewById(R.id.cbDone);
        }
    }
}