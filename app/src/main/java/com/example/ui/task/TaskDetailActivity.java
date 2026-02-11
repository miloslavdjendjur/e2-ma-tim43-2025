package com.example.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.data.model.Task;
import com.example.data.repo.TaskRepository;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TaskDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "EXTRA_TASK_ID";
    public static final String EXTRA_DATE_KEY = "EXTRA_DATE_KEY"; // yyyy-MM-dd (for recurring)

    private final TaskRepository taskRepo = new TaskRepository();

    private TextView tvName, tvDesc, tvType, tvTimeOrStart, tvEnd, tvInterval, tvXp;
    private Spinner spinnerStatus;
    private Button btnSaveStatus, btnEdit, btnDelete;

    private String taskId;
    private String dateKey;
    private Task loadedTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        tvName = findViewById(R.id.tvDetailName);
        tvDesc = findViewById(R.id.tvDetailDesc);
        tvType = findViewById(R.id.tvDetailType);
        tvTimeOrStart = findViewById(R.id.tvDetailTimeOrStart);
        tvEnd = findViewById(R.id.tvDetailEnd);
        tvInterval = findViewById(R.id.tvDetailInterval);
        tvXp = findViewById(R.id.tvDetailXp);

        spinnerStatus = findViewById(R.id.spinnerDetailStatus);
        btnSaveStatus = findViewById(R.id.btnSaveStatus);
        btnEdit = findViewById(R.id.btnDetailEdit);
        btnDelete = findViewById(R.id.btnDetailDelete);

        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        dateKey = getIntent().getStringExtra(EXTRA_DATE_KEY);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this,
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
        if (taskId == null) {
            Toast.makeText(this, "Task id missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taskRepo.getTaskById(taskId, task -> {
            if (task == null) {
                Toast.makeText(this, "Task not found.", Toast.LENGTH_SHORT).show();
                finish();
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
            btnDelete.setAlpha(0.4f); // vizuelno sivo
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
                Toast.makeText(this, "Missing date key for recurring task.", Toast.LENGTH_SHORT).show();
                return;
            }
            taskRepo.updateTaskOccurrenceStatus(taskId, dateKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    loadTask();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(TaskDetailActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            taskRepo.updateTaskStatus(taskId, newStatus, new TaskRepository.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    loadTask();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(TaskDetailActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openEdit() {
        Intent i = new Intent(this, CreateTaskActivity.class);
        i.putExtra(CreateTaskActivity.EXTRA_TASK_ID, taskId);
        startActivity(i);
    }

    private void confirmDelete() {

        if (loadedTask == null) return;

        // 🔒 RULE: Nije moguće obrisati završene zadatke

        if (Task.TYPE_SINGLE.equals(loadedTask.getType())) {

            if (Task.STATUS_DONE.equals(loadedTask.getStatus())) {
                Toast.makeText(this, "Finished tasks cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }

            new AlertDialog.Builder(this)
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
                    Toast.makeText(this, "Finished occurrences cannot be deleted.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            new AlertDialog.Builder(this)
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
                                        Toast.makeText(this, "Cannot delete series with finished occurrences.", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Missing date key.", Toast.LENGTH_SHORT).show();
            return;
        }

        // convert yyyy-MM-dd -> endDate = last millisecond of previous day
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
            Toast.makeText(this, "Bad date key.", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp newEnd = new Timestamp(c.getTime());
        taskRepo.truncateRecurringFromDate(taskId, newEnd, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TaskDetailActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doDelete() {
        taskRepo.deleteTask(taskId, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TaskDetailActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
