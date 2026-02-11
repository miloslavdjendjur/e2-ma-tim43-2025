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

    private int selectedYear;
    private int selectedMonth;      // 0-11
    private int selectedDayOfMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        rvTasks = findViewById(R.id.rvTasks);
        CalendarView calendarView = findViewById(R.id.calendarView);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter((task, isDone) -> {
            String newStatus = isDone ? "urađen" : "aktivan";
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

            Toast.makeText(this,
                    "Tražim: " + dayOfMonth + "." + (month + 1) + "." + year,
                    Toast.LENGTH_SHORT).show();

            filterTasksByDate(year, month, dayOfMonth);
        });

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kad se vratiš iz CreateTaskActivity, osveži podatke
        loadData();
    }

    private void loadData() {
        catRepo.getAllCategories(categories -> {
            this.allCategories = categories;

            taskRepo.getTasks(tasks -> {
                this.allTasks = tasks;

                Log.d("TasksActivity", "Loaded tasks: " + tasks.size());

                // Prikaži zadatke za trenutno selektovan datum
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

        String targetDateStr = year + "-" + month + "-" + dayOfMonth;

        for (Task task : allTasks) {
            if (task.getExecutionTime() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(task.getExecutionTime().toDate());

                int taskYear = cal.get(Calendar.YEAR);
                int taskMonth = cal.get(Calendar.MONTH);
                int taskDay = cal.get(Calendar.DAY_OF_MONTH);

                String taskDateStr = taskYear + "-" + taskMonth + "-" + taskDay;

                if (targetDateStr.equals(taskDateStr)) {
                    filteredList.add(task);
                }
            }
        }

        adapter.setData(filteredList, allCategories);

        if (filteredList.isEmpty()) {

            Toast.makeText(this, "Nema zadataka za: " + dayOfMonth + "." + (month + 1) + ".", Toast.LENGTH_SHORT).show();
        }
    }
}
