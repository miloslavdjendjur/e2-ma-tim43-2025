package com.example.ui.task;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.data.service.CategoryService;
import com.example.data.service.TaskService;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TasksFragment extends Fragment {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;

    private final TaskService taskService = new TaskService();
    private final CategoryService categoryService = new CategoryService();

    private List<Task> allTasks = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();

    private int selectedYear, selectedMonth, selectedDayOfMonth;

    public TasksFragment() {
        super(R.layout.fragment_tasks);
    }

    private String selectedDateKey() {
        Calendar c = Calendar.getInstance();
        c.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvTasks = view.findViewById(R.id.rvTasks);
        CalendarView calendarView = view.findViewById(R.id.calendarView);

        Button btnNewCategory = view.findViewById(R.id.btnNewCategory);
        Button btnNewTask = view.findViewById(R.id.btnNewTask);
        Button btnAllTasks = view.findViewById(R.id.btnAllTasks);

        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new TaskAdapter(
                (task, isDone) -> {
                    String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;
                    updateStatus(task, newStatus);
                },
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

        calendarView.setOnDateChangeListener((v, year, month, dayOfMonth) -> {
            selectedYear = year;
            selectedMonth = month;
            selectedDayOfMonth = dayOfMonth;
            filterTasksByDate(year, month, dayOfMonth);
        });

        btnAllTasks.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_tasks_to_allTasks);
        });

        btnNewTask.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_tasks_to_createTask);
        });

        btnNewCategory.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_tasks_to_category);
        });

        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        categoryService.getAllCategories(categories -> {
            allCategories = (categories != null) ? categories : new ArrayList<>();
            taskService.getTasks(tasks -> {
                allTasks = (tasks != null) ? tasks : new ArrayList<>();
                filterTasksByDate(selectedYear, selectedMonth, selectedDayOfMonth);
            });
        });
    }

    private void openDetail(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle b = new Bundle();
        b.putString(TaskDetailFragment.ARG_TASK_ID, task.getId());
        b.putString(TaskDetailFragment.ARG_DATE_KEY, selectedDateKey());

        NavHostFragment.findNavController(this).navigate(R.id.action_tasks_to_taskDetail, b);
    }

    private void updateStatus(Task task, String newStatus) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing - can't update status.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String dateKey = selectedDateKey();
            taskService.updateTaskOccurrenceStatus(task.getId(), dateKey, newStatus, new TaskService.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    loadData();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            taskService.updateTaskStatus(task.getId(), newStatus, new TaskService.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    loadData();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openEditTask(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing - can't edit.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle b = new Bundle();
        b.putString(CreateTaskFragment.ARG_TASK_ID, task.getId());
        NavHostFragment.findNavController(this).navigate(R.id.createTaskFragment, b);
    }

    private void confirmDelete(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing - can't delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Task.TYPE_SINGLE.equals(task.getType())) {
            if (Task.STATUS_DONE.equals(task.getStatus())) {
                Toast.makeText(requireContext(), "Finished tasks cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String dateKey = selectedDateKey();
            String occStatus = task.getOccurrenceStatusForDateKey(dateKey);
            if (Task.STATUS_DONE.equals(occStatus)) {
                Toast.makeText(requireContext(), "Finished occurrences cannot be deleted.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete recurring task")
                    .setMessage("What do you want to delete?")
                    .setPositiveButton("This occurrence + future", (d, w) -> truncateRecurringFromSelectedDate(task))
                    .setNeutralButton("Entire series", (d, w) -> deleteWholeDocument(task))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete task")
                    .setMessage("Are you sure you want to delete: \"" + task.getName() + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteTask(task.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void truncateRecurringFromSelectedDate(Task task) {
        Calendar cut = Calendar.getInstance();
        cut.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0);
        cut.set(Calendar.MILLISECOND, 0);
        cut.add(Calendar.MILLISECOND, -1);

        Timestamp newEnd = new Timestamp(cut.getTime());

        taskService.truncateRecurringFromDate(task.getId(), newEnd, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteWholeDocument(Task task) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete entire series")
                .setMessage("This will delete past and future occurrences. Continue?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTask(task.getId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTask(String taskId) {
        taskService.deleteTask(taskId, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
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
                continue;
            }

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
