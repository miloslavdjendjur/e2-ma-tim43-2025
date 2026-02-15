package com.example.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AllTasksFragment extends Fragment {

    private RecyclerView rv;
    private AllTasksAdapter adapter;

    private MaterialButtonToggleGroup toggleType;
    private MaterialButtonToggleGroup toggleRange;

    private final TaskService taskService = new TaskService();
    private final CategoryService categoryService = new CategoryService();

    private List<Task> allTasks = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();

    private boolean showOneTime = true;
    private boolean showRecurring = true;
    private int rangeDays = 14; // default ±14

    public AllTasksFragment() {
        super(R.layout.fragment_all_tasks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rv = view.findViewById(R.id.rvAllTasks);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        toggleType = view.findViewById(R.id.toggleType);
        toggleRange = view.findViewById(R.id.toggleRange);

        adapter = new AllTasksAdapter(
                (task, occKey) -> openDetail(task, occKey),
                (anchor, task, occKey) -> showMenu(anchor, task, occKey),
                (task, occKey, isDone) -> quickToggleDone(task, occKey, isDone),
                (task, occKey) -> showStatusPicker(task, occKey)
        );
        rv.setAdapter(adapter);

        // defaults: oba tipa uključena
        toggleType.check(R.id.btnTypeOneTime);
        toggleType.check(R.id.btnTypeRecurring);

        // default range: ±14
        toggleRange.check(R.id.btnRange14);

        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            showOneTime = group.getCheckedButtonIds().contains(R.id.btnTypeOneTime);
            showRecurring = group.getCheckedButtonIds().contains(R.id.btnTypeRecurring);
            render();
        });

        toggleRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnRange7) rangeDays = 7;
            else if (checkedId == R.id.btnRange14) rangeDays = 14;
            else if (checkedId == R.id.btnRange30) rangeDays = 30;
            render();
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
                render();
            });
        });
    }

    private void render() {
        if (adapter == null) return;

        // ako korisnik ugasi oba tipa
        if (!showOneTime && !showRecurring) {
            adapter.setData(new ArrayList<>(), allCategories);
            return;
        }

        List<AllTasksAdapter.DisplayItem> items = new ArrayList<>();

        Calendar from = Calendar.getInstance();
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);
        from.add(Calendar.DAY_OF_YEAR, -rangeDays);

        Calendar to = Calendar.getInstance();
        to.set(Calendar.HOUR_OF_DAY, 0);
        to.set(Calendar.MINUTE, 0);
        to.set(Calendar.SECOND, 0);
        to.set(Calendar.MILLISECOND, 0);
        to.add(Calendar.DAY_OF_YEAR, rangeDays);

        for (Task t : allTasks) {
            if (Task.TYPE_SINGLE.equals(t.getType())) {
                if (showOneTime) items.add(new AllTasksAdapter.DisplayItem(t, null));
                continue;
            }

            if (Task.TYPE_RECURRING.equals(t.getType())) {
                if (showRecurring) items.addAll(expandRecurring(t, from, to));
            }
        }

        items.sort(Comparator.comparingLong(this::itemSortMillis).reversed());
        adapter.setData(items, allCategories);
    }

    private long itemSortMillis(AllTasksAdapter.DisplayItem item) {
        Task t = item.task;

        if (Task.TYPE_SINGLE.equals(t.getType()) && t.getExecutionTime() != null) {
            return t.getExecutionTime().toDate().getTime();
        }

        if (Task.TYPE_RECURRING.equals(t.getType()) && item.occurrenceDateKey != null) {
            Calendar c = parseDateKey(item.occurrenceDateKey);
            if (c == null) return 0L;

            if (t.getStartDate() != null) {
                Calendar tod = Calendar.getInstance();
                tod.setTime(t.getStartDate().toDate());
                c.set(Calendar.HOUR_OF_DAY, tod.get(Calendar.HOUR_OF_DAY));
                c.set(Calendar.MINUTE, tod.get(Calendar.MINUTE));
            }
            return c.getTimeInMillis();
        }

        if (t.getStartDate() != null) return t.getStartDate().toDate().getTime();
        return 0L;
    }

    private List<AllTasksAdapter.DisplayItem> expandRecurring(Task t, Calendar from, Calendar to) {
        List<AllTasksAdapter.DisplayItem> out = new ArrayList<>();
        if (t.getStartDate() == null) return out;

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
            end.set(Calendar.HOUR_OF_DAY, 0);
            end.set(Calendar.MINUTE, 0);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
        }

        Calendar rangeStart = (Calendar) from.clone();
        if (rangeStart.before(start)) rangeStart = (Calendar) start.clone();

        Calendar rangeEnd = (Calendar) to.clone();
        if (end != null && rangeEnd.after(end)) rangeEnd = (Calendar) end.clone();

        if (rangeEnd.before(rangeStart)) return out;

        int interval = Math.max(t.getInterval(), 1);
        String unit = (t.getUnit() != null) ? t.getUnit() : "Day";

        Calendar cur = (Calendar) start.clone();

        for (int guard = 0; guard < 5000 && cur.before(rangeStart); guard++) {
            if (unit.contains("Week")) cur.add(Calendar.WEEK_OF_YEAR, interval);
            else cur.add(Calendar.DAY_OF_YEAR, interval);
        }

        for (int guard = 0; guard < 5000 && !cur.after(rangeEnd); guard++) {
            String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cur.getTime());
            out.add(new AllTasksAdapter.DisplayItem(t, dateKey));

            if (unit.contains("Week")) cur.add(Calendar.WEEK_OF_YEAR, interval);
            else cur.add(Calendar.DAY_OF_YEAR, interval);
        }

        return out;
    }

    private void openDetail(Task task, String occKey) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle b = new Bundle();
        b.putString(TaskDetailFragment.ARG_TASK_ID, task.getId());
        if (Task.TYPE_RECURRING.equals(task.getType()) && occKey != null) {
            b.putString(TaskDetailFragment.ARG_DATE_KEY, occKey);
        }
        NavHostFragment.findNavController(this).navigate(R.id.taskDetailFragment, b);
    }

    private void openEdit(Task task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            Toast.makeText(requireContext(), "Task id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle b = new Bundle();
        b.putString(CreateTaskFragment.ARG_TASK_ID, task.getId());
        NavHostFragment.findNavController(this).navigate(R.id.createTaskFragment, b);
    }

    private void showMenu(View anchor, Task task, String occKey) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("View");
        menu.getMenu().add("Edit");
        menu.getMenu().add("Delete");

        boolean disableDelete = isFinished(task, occKey);
        if (disableDelete) menu.getMenu().getItem(2).setEnabled(false);

        menu.setOnMenuItemClickListener(mi -> {
            String t = mi.getTitle().toString();
            if ("View".equals(t)) openDetail(task, occKey);
            if ("Edit".equals(t)) openEdit(task);
            if ("Delete".equals(t)) confirmDelete(task, occKey);
            return true;
        });

        menu.show();
    }

    private boolean isFinished(Task task, String occKey) {
        if (Task.TYPE_SINGLE.equals(task.getType())) {
            return Task.STATUS_DONE.equals(task.getStatus());
        }
        if (Task.TYPE_RECURRING.equals(task.getType())) {
            if (occKey == null) return false;
            String occ = task.getOccurrenceStatusForDateKey(occKey);
            return Task.STATUS_DONE.equals(occ);
        }
        return false;
    }

    private void confirmDelete(Task task, String occKey) {
        if (isFinished(task, occKey)) {
            Toast.makeText(requireContext(), "Finished tasks cannot be deleted.", Toast.LENGTH_LONG).show();
            return;
        }

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete recurring task")
                    .setItems(new CharSequence[]{
                            "Delete this occurrence + future occurrences",
                            "Delete entire series"
                    }, (dialog, which) -> {
                        if (which == 0) truncateRecurringFromDateKey(task, occKey);
                        else deleteTask(task.getId());
                    })
                    .show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete task")
                    .setMessage("Delete \"" + task.getName() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> deleteTask(task.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void truncateRecurringFromDateKey(Task task, String dateKey) {
        if (dateKey == null) {
            Toast.makeText(requireContext(), "No valid occurrence.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cut = parseDateKey(dateKey);
        if (cut == null) {
            Toast.makeText(requireContext(), "Bad date key.", Toast.LENGTH_SHORT).show();
            return;
        }

        cut.set(Calendar.HOUR_OF_DAY, 0);
        cut.set(Calendar.MINUTE, 0);
        cut.set(Calendar.SECOND, 0);
        cut.set(Calendar.MILLISECOND, 0);
        cut.add(Calendar.MILLISECOND, -1);

        Timestamp newEnd = new Timestamp(cut.getTime());

        taskService.truncateRecurringFromDate(task.getId(), newEnd, new TaskService.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                loadData();
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteTask(String taskId) {
        taskService.deleteTask(taskId, new TaskService.OnTaskActionEventListener() {
            @Override public void onSuccess(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                loadData();
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void quickToggleDone(Task task, String occKey, boolean isDone) {
        String newStatus = isDone ? Task.STATUS_DONE : Task.STATUS_ACTIVE;

        if (Task.TYPE_RECURRING.equals(task.getType())) {
            if (occKey == null) {
                Toast.makeText(requireContext(), "Occurrence key missing.", Toast.LENGTH_SHORT).show();
                return;
            }
            taskService.updateTaskOccurrenceStatus(task.getId(), occKey, newStatus, new TaskService.OnTaskActionEventListener() {
                @Override
                public void onSuccess(String message) {
                    if ("LEVEL_UP".equals(message)) {
                        Intent i = new Intent(getContext(), com.example.ui.boss.BossPrepActivity.class);
                        startActivity(i);
                    } else {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onError(String error) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    loadData();
                }
            });
        } else {
            taskService.updateTaskStatus(task.getId(), newStatus, new TaskService.OnTaskActionEventListener() {
                @Override public void onSuccess(String message) {
                    if (!isAdded()) return;
                    if ("LEVEL_UP".equals(message)) {
                        Intent i = new Intent(getContext(), com.example.myapplication.MainActivity.class);
                        i.putExtra("openTab", "profile");
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(i);
                    } else {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        loadData();
                    }
                }
                @Override public void onError(String error) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    loadData();
                }
            });
        }
    }

    private void showStatusPicker(Task task, String occKey) {
        String[] options = new String[] {
                Task.STATUS_ACTIVE, Task.STATUS_DONE, Task.STATUS_PAUSED, Task.STATUS_CANCELED
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Status")
                .setItems(options, (d, which) -> {
                    String picked = options[which];
                    if (Task.TYPE_RECURRING.equals(task.getType())) {
                        if (occKey == null) {
                            Toast.makeText(requireContext(), "Occurrence key missing.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        taskService.updateTaskOccurrenceStatus(task.getId(), occKey, picked, new TaskService.OnTaskActionEventListener() {
                            @Override public void onSuccess(String message) {
                                if (!isAdded()) return;
                                if ("LEVEL_UP".equals(message)) {
                                    Intent i = new Intent(getContext(), com.example.myapplication.MainActivity.class);
                                    i.putExtra("openTab", "profile");
                                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(i);
                                } else {
                                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                                    loadData();
                                }
                            }
                            @Override public void onError(String error) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                                loadData();
                            }
                        });
                    } else {
                        taskService.updateTaskStatus(task.getId(), picked, new TaskService.OnTaskActionEventListener() {
                            @Override
                            public void onSuccess(String message) {
                                if ("LEVEL_UP".equals(message)) {
                                    Intent i = new Intent(getContext(), com.example.ui.boss.BossPrepActivity.class);
                                    startActivity(i);
                                } else {
                                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void onError(String error) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                                loadData();
                            }
                        });
                    }
                })
                .show();
    }

    @Nullable
    private Calendar parseDateKey(String dateKey) {
        try {
            String[] p = dateKey.split("-");
            int y = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]) - 1;
            int d = Integer.parseInt(p[2]);
            Calendar c = Calendar.getInstance();
            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
