package com.example.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.data.model.Task;
import com.example.data.service.TaskService;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TaskDetailFragment extends Fragment {

    public static final String ARG_TASK_ID = "taskId";
    public static final String ARG_DATE_KEY = "dateKey"; // yyyy-MM-dd

    private final TaskService taskService = new TaskService();

    private TextView tvName, tvDesc, tvType, tvTimeOrStart, tvEnd, tvInterval, tvXp;
    private Spinner spinnerStatus;
    private Button btnSaveStatus, btnEdit, btnDelete;

    private String taskId;
    private String dateKey;
    private Task loadedTask;

    public TaskDetailFragment() {
        super(R.layout.fragment_task_detail);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvName = view.findViewById(R.id.tvDetailName);
        tvDesc = view.findViewById(R.id.tvDetailDesc);
        tvType = view.findViewById(R.id.tvDetailType);
        tvTimeOrStart = view.findViewById(R.id.tvDetailTimeOrStart);
        tvEnd = view.findViewById(R.id.tvDetailEnd);
        tvInterval = view.findViewById(R.id.tvDetailInterval);
        tvXp = view.findViewById(R.id.tvDetailXp);

        spinnerStatus = view.findViewById(R.id.spinnerDetailStatus);
        btnSaveStatus = view.findViewById(R.id.btnSaveStatus);
        btnEdit = view.findViewById(R.id.btnDetailEdit);
        btnDelete = view.findViewById(R.id.btnDetailDelete);

        Bundle args = getArguments();
        if (args != null) {
            taskId = args.getString(ARG_TASK_ID);
            dateKey = args.getString(ARG_DATE_KEY);
        }

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{Task.STATUS_ACTIVE, Task.STATUS_DONE, Task.STATUS_PAUSED, Task.STATUS_CANCELED}
        );
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        btnSaveStatus.setOnClickListener(v -> saveStatus());
        btnEdit.setOnClickListener(v -> openEdit());
        btnDelete.setOnClickListener(v -> confirmDelete());

        loadTask();
    }

    private void loadTask() {
        if (taskId == null || taskId.isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing.", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        taskService.getTaskById(taskId, task -> {
            if (!isAdded()) return;

            if (task == null) {
                Toast.makeText(requireContext(), "Task not found.", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            loadedTask = task;
            bind(task);
        });
    }

    private void bind(Task task) {
        tvName.setText(task.getName() != null ? task.getName() : "");
        tvDesc.setText(task.getDescription() != null ? task.getDescription() : "");
        tvType.setText(task.getType() != null ? task.getType() : "");

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        if (Task.TYPE_SINGLE.equals(task.getType()) && task.getExecutionTime() != null) {
            tvTimeOrStart.setText("Time: " + sdf.format(task.getExecutionTime().toDate()));
        } else if (Task.TYPE_RECURRING.equals(task.getType()) && task.getStartDate() != null) {
            tvTimeOrStart.setText("Start: " + sdf.format(task.getStartDate().toDate()));
        } else {
            tvTimeOrStart.setText("Time: -");
        }

        if (task.getEndDate() != null) tvEnd.setText("End: " + sdf.format(task.getEndDate().toDate()));
        else tvEnd.setText("End: -");

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            tvInterval.setText("Repeat: every " + task.getInterval() + " " + (task.getUnit() != null ? task.getUnit() : ""));
        } else {
            tvInterval.setText("Repeat: -");
        }

        tvXp.setText("XP: " + task.getTotalXp());

        // current status: single vs recurring-date
        String current = Task.STATUS_ACTIVE;
        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String occ = task.getOccurrenceStatusForDateKey(dateKey);
            current = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
        } else {
            if (task.getStatus() != null && !task.getStatus().isEmpty()) current = task.getStatus();
        }

        String[] vals = {Task.STATUS_ACTIVE, Task.STATUS_DONE, Task.STATUS_PAUSED, Task.STATUS_CANCELED};
        int idx = 0;
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(current)) idx = i;
        spinnerStatus.setSelection(idx);

        boolean isFinished = false;

        if (Task.TYPE_SINGLE.equals(task.getType())) {
            isFinished = Task.STATUS_DONE.equals(task.getStatus());
        } else if (Task.TYPE_RECURRING.equals(task.getType())) {
            if (dateKey != null) {
                String occStatus = task.getOccurrenceStatusForDateKey(dateKey);
                isFinished = Task.STATUS_DONE.equals(occStatus);
            }
        }

        if (isFinished) {
            btnDelete.setEnabled(false);
            btnDelete.setAlpha(0.4f);
        } else {
            btnDelete.setEnabled(true);
            btnDelete.setAlpha(1f);
        }
    }

    private void saveStatus() {
        if (loadedTask == null) return;

        String newStatus = spinnerStatus.getSelectedItem().toString();

        if (Task.TYPE_RECURRING.equals(loadedTask.getType())) {
            if (dateKey == null || dateKey.isEmpty()) {
                Toast.makeText(requireContext(), "Missing date key for recurring task.", Toast.LENGTH_SHORT).show();
                return;
            }

            taskService.updateTaskOccurrenceStatus(taskId, dateKey, newStatus, new TaskService.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    if ("LEVEL_UP".equals(message)) {
                        Intent i = new Intent(getContext(), com.example.ui.boss.BossPrepActivity.class);
                        startActivity(i);
                    } else {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String error) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            taskService.updateTaskStatus(taskId, newStatus, new TaskService.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    if ("LEVEL_UP".equals(message)) {
                        Intent i = new Intent(getContext(), com.example.ui.boss.BossPrepActivity.class);
                        startActivity(i);
                    } else {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String error) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openEdit() {
        if (taskId == null || taskId.isEmpty()) return;

        Bundle b = new Bundle();
        b.putString(CreateTaskFragment.ARG_TASK_ID, taskId);

        NavController nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.createTaskFragment, b);
    }

    private void confirmDelete() {
        if (loadedTask == null) return;

        // RULE: Nije moguće obrisati završene zadatke
        if (Task.TYPE_SINGLE.equals(loadedTask.getType())) {
            if (Task.STATUS_DONE.equals(loadedTask.getStatus())) {
                Toast.makeText(requireContext(), "Finished tasks cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete task")
                    .setMessage("Delete this task?")
                    .setPositiveButton("Delete", (d, w) -> doDelete())
                    .setNegativeButton("Cancel", null)
                    .show();

            return;
        }

        // RECURRING
        if (Task.TYPE_RECURRING.equals(loadedTask.getType())) {
            if (dateKey != null) {
                String occStatus = loadedTask.getOccurrenceStatusForDateKey(dateKey);
                if (Task.STATUS_DONE.equals(occStatus)) {
                    Toast.makeText(requireContext(), "Finished occurrences cannot be deleted.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete recurring task")
                    .setItems(new CharSequence[]{
                            "Delete this occurrence + future occurrences",
                            "Delete entire series"
                    }, (dialog, which) -> {
                        if (which == 0) {
                            truncateFromDetailDateKey();
                        } else {
                            // dodatna zaštita: ako bilo koje ponavljanje ima DONE → ne briši seriju
                            if (loadedTask.getOccurrenceStatuses() != null) {
                                for (String s : loadedTask.getOccurrenceStatuses().values()) {
                                    if (Task.STATUS_DONE.equals(s)) {
                                        Toast.makeText(requireContext(), "Cannot delete series with finished occurrences.", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }
                            }
                            doDelete();
                        }
                    })
                    .show();
        }
    }

    private void truncateFromDetailDateKey() {
        if (dateKey == null || dateKey.isEmpty()) {
            Toast.makeText(requireContext(), "Missing date key.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar c = Calendar.getInstance();
        try {
            String[] parts = dateKey.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);

            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.MILLISECOND, -1);

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Bad date key.", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp newEnd = new Timestamp(c.getTime());
        taskService.truncateRecurringFromDate(taskId, newEnd, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(TaskDetailFragment.this).popBackStack();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doDelete() {
        taskService.deleteTask(taskId, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(TaskDetailFragment.this).popBackStack();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
