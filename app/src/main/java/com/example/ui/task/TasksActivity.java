package com.example.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.data.repo.CategoryRepository;
import com.example.data.repo.TaskRepository;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TasksActivity extends AppCompatActivity {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;

    private final TaskRepository taskRepo = new TaskRepository();
    private final CategoryRepository catRepo = new CategoryRepository();

    private List<Task> allTasks = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();

    private int selectedYear, selectedMonth, selectedDayOfMonth;

    private String getSelectedDateKey() {
        Calendar c = Calendar.getInstance();
        c.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        rvTasks = findViewById(R.id.rvTasks);
        CalendarView calendarView = findViewById(R.id.calendarView);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TaskAdapter(
                (task, isDone) -> {
                    String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;

                    // ✅ SINGLE vs RECURRING status upis
                    if (Task.TYPE_RECURRING.equals(task.getType())) {
                        String dateKey = getSelectedDateKey();
                        taskRepo.updateTaskOccurrenceStatus(task.getId(), dateKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) {
                                loadData();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(TasksActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        updateStatus(task, newStatus);
                    }
                },
                new TaskAdapter.OnTaskActionsListener() {
                    @Override
                    public void onView(Task task) {
                        openTaskDetail(task);
                    }

                    @Override
                    public void onEdit(Task task) {
                        openEditTask(task);
                    }

                    @Override
                    public void onDelete(Task task) {
                        confirmDelete(task);
                    }
                }
        );

        rvTasks.setAdapter(adapter);

        Calendar today = Calendar.getInstance();
        selectedYear = today.get(Calendar.YEAR);
        selectedMonth = today.get(Calendar.MONTH);
        selectedDayOfMonth = today.get(Calendar.DAY_OF_MONTH);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedYear = year;
            selectedMonth = month;
            selectedDayOfMonth = dayOfMonth;
            filterTasksByDate(year, month, dayOfMonth);
        });

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        catRepo.getAllCategories(categories -> {
            this.allCategories = categories != null ? categories : new ArrayList<>();
            taskRepo.getTasks(tasks -> {
                this.allTasks = tasks != null ? tasks : new ArrayList<>();
                filterTasksByDate(selectedYear, selectedMonth, selectedDayOfMonth);
            });
        });
    }

    private void openTaskDetail(Task task) {
        Intent i = new Intent(this, TaskDetailActivity.class);
        i.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());
        i.putExtra(TaskDetailActivity.EXTRA_DATE_KEY, getSelectedDateKey()); // bitno za recurring
        startActivity(i);
    }

    private void updateStatus(Task task, String newStatus) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(this, "Task id missing - can't update status.", Toast.LENGTH_SHORT).show();
            return;
        }

        taskRepo.updateTaskStatus(task.getId(), newStatus, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                loadData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TasksActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openEditTask(Task task) {
        Intent i = new Intent(this, CreateTaskActivity.class);
        i.putExtra(CreateTaskActivity.EXTRA_TASK_ID, task.getId());
        startActivity(i);
    }

    private void confirmDelete(Task task) {
        if (Task.TYPE_SINGLE.equals(task.getType())) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete task")
                    .setMessage("Delete this task?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteTask(task.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // RECURRING
        new AlertDialog.Builder(this)
                .setTitle("Delete recurring task")
                .setItems(new CharSequence[]{
                        "Delete this occurrence + future occurrences",
                        "Delete entire series"
                }, (dialog, which) -> {
                    if (which == 0) {
                        truncateRecurringFromSelectedDate(task);
                    } else {
                        deleteWholeDocument(task);
                    }
                })
                .show();
    }

    private void truncateRecurringFromSelectedDate(Task task) {
        Calendar cut = Calendar.getInstance();
        cut.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0);
        cut.set(Calendar.MILLISECOND, 0);

        // endDate = 1ms pre izabranog dana
        cut.add(Calendar.MILLISECOND, -1);
        Timestamp newEnd = new Timestamp(cut.getTime());

        taskRepo.truncateRecurringFromDate(task.getId(), newEnd, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(TasksActivity.this, message, Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TasksActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteWholeDocument(Task task) {
        new AlertDialog.Builder(this)
                .setTitle("Delete entire series")
                .setMessage("This will delete past and future occurrences. Continue?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTask(task.getId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTask(String taskId) {
        taskRepo.deleteTask(taskId, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(TasksActivity.this, message, Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TasksActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterTasksByDate(int year, int month, int dayOfMonth) {
        List<Task> filteredList = new ArrayList<>();

        Calendar targetCal = Calendar.getInstance();
        targetCal.set(year, month, dayOfMonth, 0, 0, 0);
        targetCal.set(Calendar.MILLISECOND, 0);

        for (Task task : allTasks) {

            if (Task.TYPE_SINGLE.equals(task.getType()) && task.getExecutionTime() != null) {
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTime(task.getExecutionTime().toDate());
                if (isSameDay(targetCal, taskCal)) filteredList.add(task);

            } else if (Task.TYPE_RECURRING.equals(task.getType()) && task.getStartDate() != null) {

                Calendar startCal = Calendar.getInstance();
                startCal.setTime(task.getStartDate().toDate());
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);

                Calendar endCal = null;
                if (task.getEndDate() != null) {
                    endCal = Calendar.getInstance();
                    endCal.setTime(task.getEndDate().toDate());
                }

                if (targetCal.before(startCal)) continue;
                if (endCal != null && targetCal.after(endCal)) continue;

                boolean match = false;
                int interval = task.getInterval();
                String unit = task.getUnit() != null ? task.getUnit() : "Day";

                Calendar currentOccurrence = (Calendar) startCal.clone();
                for (int i = 0; i < 500; i++) {
                    if (isSameDay(targetCal, currentOccurrence)) {
                        match = true;
                        break;
                    }
                    if (currentOccurrence.after(targetCal)) break;

                    if (unit.contains("Day")) currentOccurrence.add(Calendar.DAY_OF_YEAR, interval);
                    else if (unit.contains("Week")) currentOccurrence.add(Calendar.WEEK_OF_YEAR, interval);
                    else break;
                }
                if (match) filteredList.add(task);
            }
        }

        adapter.setSelectedDateKey(getSelectedDateKey());
        adapter.setData(filteredList, allCategories);
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
