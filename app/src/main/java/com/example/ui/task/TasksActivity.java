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

    private String selectedDateKey() {
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
                // quick checkbox done/active
                (task, isDone) -> {
                    String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;
                    updateStatus(task, newStatus);
                },
                // popup actions
                new TaskAdapter.OnTaskActionsListener() {
                    @Override
                    public void onView(Task task) {
                        openDetail(task);
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
            allCategories = (categories != null) ? categories : new ArrayList<>();
            taskRepo.getTasks(tasks -> {
                allTasks = (tasks != null) ? tasks : new ArrayList<>();
                filterTasksByDate(selectedYear, selectedMonth, selectedDayOfMonth);
            });
        });
    }

    private void openDetail(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(this, "Task id missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, TaskDetailActivity.class);
        i.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());
        i.putExtra(TaskDetailActivity.EXTRA_DATE_KEY, selectedDateKey()); // important for recurring
        startActivity(i);
    }

    private void updateStatus(Task task, String newStatus) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(this, "Task id missing - can't update status.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String dateKey = selectedDateKey();
            taskRepo.updateTaskOccurrenceStatus(task.getId(), dateKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
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
        } else {
            taskRepo.updateTaskStatus(task.getId(), newStatus, new TaskRepository.OnTaskActionEventListener() {
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
    }

    private void openEditTask(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(this, "Task id missing - can't edit.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, CreateTaskActivity.class);
        i.putExtra(CreateTaskActivity.EXTRA_TASK_ID, task.getId());
        startActivity(i);
    }

    private void confirmDelete(Task task) {

        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(this, "Task id missing - can't delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔒 RULE: Nije moguće obrisati završene zadatke

        // SINGLE
        if (Task.TYPE_SINGLE.equals(task.getType())) {
            if (Task.STATUS_DONE.equals(task.getStatus())) {
                Toast.makeText(this, "Finished tasks cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // RECURRING (provera za konkretno ponavljanje u kalendaru)
        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String dateKey = selectedDateKey(); // već imaš ovu metodu

            String occStatus = task.getOccurrenceStatusForDateKey(dateKey);
            if (Task.STATUS_DONE.equals(occStatus)) {
                Toast.makeText(this, "Finished occurrences cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Ako nije finished → dozvoli delete
        if (Task.TYPE_RECURRING.equals(task.getType())) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete recurring task")
                    .setMessage("What do you want to delete?")
                    .setPositiveButton("This occurrence + future", (d, w) -> truncateRecurringFromSelectedDate(task))
                    .setNeutralButton("Entire series", (d, w) -> deleteWholeDocument(task))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Delete task")
                    .setMessage("Are you sure you want to delete: \"" + task.getName() + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteTask(task.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }


    private void truncateRecurringFromSelectedDate(Task task) {
        // endDate = last millisecond of PREVIOUS day (so selected date + future disappear)
        Calendar cut = Calendar.getInstance();
        cut.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0);
        cut.set(Calendar.MILLISECOND, 0);
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
                Toast.makeText(TasksActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterTasksByDate(int year, int month, int dayOfMonth) {
        List<Task> filteredList = new ArrayList<>();

        Calendar targetCal = Calendar.getInstance();
        targetCal.set(year, month, dayOfMonth, 0, 0, 0);
        targetCal.set(Calendar.MILLISECOND, 0);

        for (Task task : allTasks) {

            // SINGLE
            if (Task.TYPE_SINGLE.equals(task.getType()) && task.getExecutionTime() != null) {
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTime(task.getExecutionTime().toDate());
                if (isSameDay(targetCal, taskCal)) filteredList.add(task);
                continue;
            }

            // RECURRING
            if (Task.TYPE_RECURRING.equals(task.getType()) && task.getStartDate() != null) {
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
                int interval = Math.max(task.getInterval(), 1);
                String unit = (task.getUnit() != null) ? task.getUnit() : "Day";

                Calendar cur = (Calendar) startCal.clone();
                for (int i = 0; i < 700; i++) {
                    if (isSameDay(targetCal, cur)) { match = true; break; }
                    if (cur.after(targetCal)) break;

                    if (unit.contains("Day")) cur.add(Calendar.DAY_OF_YEAR, interval);
                    else if (unit.contains("Week")) cur.add(Calendar.WEEK_OF_YEAR, interval);
                    else break;
                }

                if (match) filteredList.add(task);
            }
        }

        adapter.setSelectedDateKey(selectedDateKey());
        adapter.setData(filteredList, allCategories);
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
