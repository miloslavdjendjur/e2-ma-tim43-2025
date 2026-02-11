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

    private Calendar todayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private String dateKey(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    /**
     * Returns the next valid occurrence dateKey (yyyy-MM-dd) for a recurring task
     * where occurrence date >= todayStart and <= endDate (if endDate exists).
     * If no future occurrence exists -> null.
     */
    private String nextOccurrenceDateKey(Task t) {
        if (t == null) return null;
        if (!Task.TYPE_RECURRING.equals(t.getType())) return null;
        if (t.getStartDate() == null) return null;

        Calendar today = todayStart();

        Calendar start = Calendar.getInstance();
        start.setTime(t.getStartDate().toDate());
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = null;
        if (t.getEndDate() != null) {
            end = Calendar.getInstance();
            end.setTime(t.getEndDate().toDate());
            // endDate može imati vreme; ostavljamo ga kao granicu "do tog trenutka"
        }

        // Ako je već završeno pre danas
        if (end != null && end.before(today)) return null;

        int interval = Math.max(1, t.getInterval());
        String unit = (t.getUnit() != null) ? t.getUnit() : "Day";

        // Ako još nije krenulo, sledeći je start
        if (start.after(today)) {
            return dateKey(start);
        }

        // Krenulo je ranije: nađi sledeći occurrence >= danas
        Calendar cur = (Calendar) start.clone();

        // Bezbednosni limit da se ne zaglavi
        for (int i = 0; i < 2000; i++) {
            // ako je cur >= today -> to je next
            if (!cur.before(today)) {
                if (end != null && cur.after(end)) return null;
                return dateKey(cur);
            }

            if (unit.contains("Day")) cur.add(Calendar.DAY_OF_YEAR, interval);
            else if (unit.contains("Week")) cur.add(Calendar.WEEK_OF_YEAR, interval);
            else {
                // ako nema Month/Year u specu, ovo je dovoljno
                // ako ima, dodaj: Calendar.MONTH / Calendar.YEAR
                return null;
            }

            if (end != null && cur.after(end)) return null;
        }

        return null;
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
                // click
                (task, occKey) -> openDetail(task, occKey),
                // long press
                (task, occKey) -> openDetail(task, occKey),
                // quick done toggle
                (task, occKey, isDone) -> {
                    String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;

                    if (Task.TYPE_RECURRING.equals(task.getType())) {
                        if (occKey == null) {
                            Toast.makeText(this, "No valid next occurrence for this task.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        taskRepo.updateTaskOccurrenceStatus(task.getId(), occKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
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
                // status picker
                (task, occKey) -> showStatusPicker(task, occKey)
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
        btnFilterSingle.setEnabled(currentFilter != FilterMode.SINGLE);
        btnFilterRecurring.setEnabled(currentFilter != FilterMode.RECURRING);

        Calendar startToday = todayStart();
        List<AllTasksAdapter.DisplayItem> filtered = new ArrayList<>();

        for (Task t : allTasks) {

            if (currentFilter == FilterMode.SINGLE) {
                if (!Task.TYPE_SINGLE.equals(t.getType())) continue;
                if (t.getExecutionTime() == null) continue;

                Calendar exec = Calendar.getInstance();
                exec.setTime(t.getExecutionTime().toDate());

                // spec: u listi samo trenutni i buduci
                if (exec.before(startToday)) continue;

                filtered.add(new AllTasksAdapter.DisplayItem(t, null));

            } else {
                if (!Task.TYPE_RECURRING.equals(t.getType())) continue;
                if (t.getStartDate() == null) continue;

                // izbaci ako je end < danas (spec: u listi samo trenutni i buduci)
                if (t.getEndDate() != null) {
                    Calendar end = Calendar.getInstance();
                    end.setTime(t.getEndDate().toDate());
                    if (end.before(startToday)) continue;
                }

                String nextKey = nextOccurrenceDateKey(t);
                if (nextKey == null) continue; // nema više validnih ponavljanja

                filtered.add(new AllTasksAdapter.DisplayItem(t, nextKey));
            }
        }

        adapter.setData(filtered, allCategories);
    }

    private void openDetail(Task task, String occKey) {
        Intent i = new Intent(this, TaskDetailActivity.class);
        i.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getId());

        // za recurring šaljemo next occurrence dateKey, ne "danas"
        if (Task.TYPE_RECURRING.equals(task.getType()) && occKey != null) {
            i.putExtra(TaskDetailActivity.EXTRA_DATE_KEY, occKey);
        }

        startActivity(i);
    }

    private void showStatusPicker(Task task, String occKey) {
        String[] options = new String[]{
                Task.STATUS_ACTIVE,
                Task.STATUS_DONE,
                Task.STATUS_PAUSED,
                Task.STATUS_CANCELED
        };

        String current = Task.STATUS_ACTIVE;

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            if (occKey == null) {
                Toast.makeText(this, "No valid next occurrence for this task.", Toast.LENGTH_SHORT).show();
                return;
            }
            String occ = task.getOccurrenceStatusForDateKey(occKey);
            current = (occ != null && !occ.isEmpty()) ? occ : Task.STATUS_ACTIVE;
        } else {
            current = (task.getStatus() != null && !task.getStatus().isEmpty())
                    ? task.getStatus()
                    : Task.STATUS_ACTIVE;
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
                        taskRepo.updateTaskOccurrenceStatus(task.getId(), occKey, newStatus, new TaskRepository.OnTaskActionEventListener() {
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
