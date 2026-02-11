package com.example.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AllTasksActivity extends AppCompatActivity {

    private RecyclerView rvAllTasks;
    private Button btnFilterSingle, btnFilterRecurring;

    private final TaskRepository taskRepo = new TaskRepository();
    private final CategoryRepository catRepo = new CategoryRepository();

    private List<Task> allTasks = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();

    private AllTasksAdapter adapter;

    private enum FilterMode { SINGLE, RECURRING }
    private FilterMode currentFilter = FilterMode.SINGLE;

    private String todayDateKey() {
        Calendar c = Calendar.getInstance();
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    private Calendar todayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_tasks);

        rvAllTasks = findViewById(R.id.rvAllTasks);
        btnFilterSingle = findViewById(R.id.btnFilterSingle);
        btnFilterRecurring = findViewById(R.id.btnFilterRecurring);

        rvAllTasks.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AllTasksAdapter(
                // open detail
                (task) -> openDetail(task),
                // on long press
                (task) -> openDetail(task),
                // quick done/active toggle
                (task, isDone) -> {
                    String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;

                    if (Task.TYPE_RECURRING.equals(task.getType())) {
                        // All tasks list nije vezan za datum; koristimo današnji dateKey (razumno i prolazi specifikaciju)
                        String dateKey = todayDateKey();
                        taskRepo.updateTaskOccurrenceStatus(task.getId(), dateKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) { loadData(); }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(AllTasksActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        taskRepo.updateTaskStatus(task.getId(), newStatus, new TaskRepository.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) { loadData(); }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(AllTasksActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                },
                // full status picker
                (task) -> showStatusPicker(task)
        );

        rvAllTasks.setAdapter(adapter);

        btnFilterSingle.setOnClickListener(v -> {
            currentFilter = FilterMode.SINGLE;
            render();
        });

        btnFilterRecurring.setOnClickListener(v -> {
            currentFilter = FilterMode.RECURRING;
            render();
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
            allCategories = categories != null ? categories : new ArrayList<>();

            taskRepo.getTasks(tasks -> {
                allTasks = tasks != null ? tasks : new ArrayList<>();
                render();
            });
        });
    }

    private void render() {
        // UI hint za filter dugmad
        btnFilterSingle.setEnabled(currentFilter != FilterMode.SINGLE);
        btnFilterRecurring.setEnabled(currentFilter != FilterMode.RECURRING);

        Calendar startToday = todayStart();
        List<Task> filtered = new ArrayList<>();

        for (Task t : allTasks) {
            if (currentFilter == FilterMode.SINGLE) {
                if (!Task.TYPE_SINGLE.equals(t.getType())) continue;
                if (t.getExecutionTime() == null) continue;

                Calendar exec = Calendar.getInstance();
                exec.setTime(t.getExecutionTime().toDate());

                // spec: u listi samo trenutni i buduci
                if (exec.before(startToday)) continue;

                filtered.add(t);

            } else {
                if (!Task.TYPE_RECURRING.equals(t.getType())) continue;
                if (t.getStartDate() == null) continue;

                Calendar start = Calendar.getInstance();
                start.setTime(t.getStartDate().toDate());
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);

                // ako jos nije krenuo, ok je (buduci)
                // ako je krenuo ranije, i dalje je ok (trenutni)
                // ali ako ima endDate i endDate < danas => ne prikazuj
                if (t.getEndDate() != null) {
                    Calendar end = Calendar.getInstance();
                    end.setTime(t.getEndDate().toDate());
                    if (end.before(startToday)) continue;
                }

                filtered.add(t);
            }
        }

        adapter.setData(filtered, allCategories, todayDateKey());
    }

    private void openDetail(Task task) {
        Intent i = new Intent(this, TaskDetailActivity.class);
        i.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());
        i.putExtra(TaskDetailActivity.EXTRA_DATE_KEY, todayDateKey()); // za recurring status u detail-u
        startActivity(i);
    }

    private void showStatusPicker(Task task) {
        String[] options = new String[]{
                Task.STATUS_ACTIVE,
                Task.STATUS_DONE,
                Task.STATUS_PAUSED,
                Task.STATUS_CANCELED
        };

        String current;
        String dateKey = todayDateKey();

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            String occ = task.getOccurrenceStatusForDateKey(dateKey);
            current = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
        } else {
            current = (task.getStatus() != null && !task.getStatus().isEmpty()) ? task.getStatus() : Task.STATUS_ACTIVE;
        }

        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) checked = i;
        }

        final int[] chosen = {checked};

        new AlertDialog.Builder(this)
                .setTitle("Set status")
                .setSingleChoiceItems(options, checked, (dialog, which) -> chosen[0] = which)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newStatus = options[chosen[0]];

                    if (Task.TYPE_RECURRING.equals(task.getType())) {
                        taskRepo.updateTaskOccurrenceStatus(task.getId(), dateKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) { loadData(); }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(AllTasksActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        taskRepo.updateTaskStatus(task.getId(), newStatus, new TaskRepository.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) { loadData(); }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(AllTasksActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
