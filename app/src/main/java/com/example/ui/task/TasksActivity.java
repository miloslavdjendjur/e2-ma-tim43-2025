package com.example.ui.task;

import android.os.Bundle;
import android.util.Log;
import android.widget.CalendarView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.data.model.Category;
import com.example.myapplication.R;
import com.example.data.model.Task;
import com.example.data.repo.CategoryRepository;
import com.example.data.repo.TaskRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TasksActivity extends AppCompatActivity {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private final TaskRepository taskRepo = new TaskRepository();
    private final CategoryRepository catRepo = new CategoryRepository();
    private List<Task> allTasks = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();
    private int selectedYear, selectedMonth, selectedDayOfMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        rvTasks = findViewById(R.id.rvTasks);
        CalendarView calendarView = findViewById(R.id.calendarView);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter((task, isDone) -> {
            String newStatus = isDone ? "done" : "active";
            updateStatus(task, newStatus);
        });
        rvTasks.setAdapter(adapter);

        Calendar today = Calendar.getInstance();
        selectedYear = today.get(Calendar.YEAR);
        selectedMonth = today.get(Calendar.MONTH);
        selectedDayOfMonth = today.get(Calendar.DAY_OF_MONTH);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedYear = year;
            selectedMonth = month;
            selectedDayOfMonth = dayOfMonth;
            Toast.makeText(this, "Searching: " + dayOfMonth + "/" + (month + 1) + "/" + year, Toast.LENGTH_SHORT).show();
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
            this.allCategories = categories;
            taskRepo.getTasks(tasks -> {
                this.allTasks = tasks;
                filterTasksByDate(selectedYear, selectedMonth, selectedDayOfMonth);
            });
        });
    }

    private void updateStatus(Task task, String newStatus) {
        taskRepo.updateTaskStatus(task.getId(), newStatus, new TaskRepository.OnTaskActionEventListener() {
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
            if ("SINGLE".equals(task.getType()) && task.getExecutionTime() != null) {
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTime(task.getExecutionTime().toDate());
                if (isSameDay(targetCal, taskCal)) filteredList.add(task);
            }
            else if ("RECURRING".equals(task.getType()) && task.getStartDate() != null) {
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
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                }

                if (targetCal.before(startCal)) continue;
                if (endCal != null && targetCal.after(endCal)) continue;

                boolean match = false;
                int interval = task.getInterval();
                String unit = task.getUnit();

                Calendar currentOccurrence = (Calendar) startCal.clone();
                for (int i = 0; i < 500; i++) { // Limit to prevent infinite loops
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
        adapter.setData(filteredList, allCategories);
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}