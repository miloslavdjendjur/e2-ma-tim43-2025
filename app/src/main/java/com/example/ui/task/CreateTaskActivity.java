package com.example.ui.task;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.data.service.CategoryService; // Promenjeno
import com.example.data.service.TaskService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";

    private EditText etName, etDesc, etInterval;
    private Spinner spinnerCategory, spinnerUnit;
    private RadioGroup rgDifficulty, rgImportance;
    private androidx.appcompat.widget.SwitchCompat switchRecurring;
    private LinearLayout layoutRecurring;
    private TextView tvSelectedDate, tvSelectedTime, tvSelectedEndDate;
    private Button btnPickDate, btnPickTime, btnPickEndDate;

    // Koristimo Servise
    private final CategoryService categoryService = new CategoryService(); // Promenjeno
    private final TaskService taskService = new TaskService();

    private List<Category> loadedCategories = new ArrayList<>();

    private Calendar selectedDate = Calendar.getInstance();
    private Calendar selectedEndDate = null;

    private String editingTaskId = null;
    private Task editingTask = null;
    private boolean taskLoadedForEdit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        initViews();
        setupUnitSpinner();
        updateDateTimeLabels();

        editingTaskId = getIntent().getStringExtra(EXTRA_TASK_ID);

        loadCategories();

        btnPickDate.setOnClickListener(v -> showDatePicker(selectedDate, tvSelectedDate, "Date"));
        btnPickTime.setOnClickListener(v -> showTimePicker());

        btnPickEndDate.setOnClickListener(v -> {
            if (selectedEndDate == null) selectedEndDate = Calendar.getInstance();
            showDatePicker(selectedEndDate, tvSelectedEndDate, "End Date");
        });

        switchRecurring.setOnCheckedChangeListener((buttonView, isChecked) ->
                layoutRecurring.setVisibility(isChecked ? View.VISIBLE : View.GONE)
        );

        findViewById(R.id.btnSaveTask).setOnClickListener(v -> {
            if (editingTaskId != null) updateTask();
            else saveNewTask();
        });
    }

    private void initViews() {
        etName = findViewById(R.id.etTaskName);
        etDesc = findViewById(R.id.etTaskDescription);
        etInterval = findViewById(R.id.etInterval);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerUnit = findViewById(R.id.spinnerUnit);
        rgDifficulty = findViewById(R.id.rgDifficulty);
        rgImportance = findViewById(R.id.rgImportance);
        switchRecurring = findViewById(R.id.switchRecurring);
        layoutRecurring = findViewById(R.id.layoutRecurringOptions);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        btnPickDate = findViewById(R.id.btnPickDate);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        btnPickTime = findViewById(R.id.btnPickTime);
        tvSelectedEndDate = findViewById(R.id.tvSelectedEndDate);
        btnPickEndDate = findViewById(R.id.btnPickEndDate);
    }

    private void updateDateTimeLabels() {
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);
        int month = selectedDate.get(Calendar.MONTH) + 1;
        int year = selectedDate.get(Calendar.YEAR);
        int hour = selectedDate.get(Calendar.HOUR_OF_DAY);
        int minute = selectedDate.get(Calendar.MINUTE);

        tvSelectedDate.setText(String.format(Locale.getDefault(), "Date: %d/%d/%d", day, month, year));
        tvSelectedTime.setText(String.format(Locale.getDefault(), "Time: %02d:%02d", hour, minute));

        if (selectedEndDate != null) {
            int ed = selectedEndDate.get(Calendar.DAY_OF_MONTH);
            int em = selectedEndDate.get(Calendar.MONTH) + 1;
            int ey = selectedEndDate.get(Calendar.YEAR);
            tvSelectedEndDate.setText(String.format(Locale.getDefault(), "End Date: %d/%d/%d", ed, em, ey));
        } else {
            tvSelectedEndDate.setText("End Date: (not set)");
        }
    }

    private void showDatePicker(Calendar targetCal, TextView targetTv, String label) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            int hour = targetCal.get(Calendar.HOUR_OF_DAY);
            int minute = targetCal.get(Calendar.MINUTE);
            targetCal.set(year, month, dayOfMonth, hour, minute, 0);
            targetCal.set(Calendar.MILLISECOND, 0);
            targetTv.setText(label + ": " + dayOfMonth + "/" + (month + 1) + "/" + year);
            updateDateTimeLabels();
        }, targetCal.get(Calendar.YEAR), targetCal.get(Calendar.MONTH), targetCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minute);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);
                    tvSelectedTime.setText(String.format(Locale.getDefault(), "Time: %02d:%02d", hourOfDay, minute));
                },
                selectedDate.get(Calendar.HOUR_OF_DAY),
                selectedDate.get(Calendar.MINUTE),
                true
        );
        timePickerDialog.show();
    }

    private void loadCategories() {
        // Poziv preko servisa
        categoryService.getAllCategories(categories -> {
            loadedCategories = categories != null ? categories : new ArrayList<>();

            List<String> names = new ArrayList<>();
            for (Category c : loadedCategories) names.add(c.getName());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(adapter);

            if (editingTaskId != null && !taskLoadedForEdit) {
                loadTaskForEdit(editingTaskId);
            }
        });
    }

    private void setupUnitSpinner() {
        String[] units = {"Day", "Week"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(unitAdapter);
    }

    private void loadTaskForEdit(String taskId) {
        taskService.getTaskById(taskId, task -> {
            taskLoadedForEdit = true;
            if (task == null) {
                Toast.makeText(this, "Task not found (or no permission).", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            editingTask = task;
            populateFormFromTask(task);
        });
    }

    private void populateFormFromTask(Task task) {
        etName.setText(task.getName() != null ? task.getName() : "");
        etDesc.setText(task.getDescription() != null ? task.getDescription() : "");
        checkDifficulty(task.getDifficultyXp());
        checkImportance(task.getImportanceXp());

        int catIndex = 0;
        for (int i = 0; i < loadedCategories.size(); i++) {
            if (loadedCategories.get(i).getId() != null &&
                    loadedCategories.get(i).getId().equals(task.getCategoryId())) {
                catIndex = i;
                break;
            }
        }
        spinnerCategory.setSelection(catIndex);

        if ("RECURRING".equals(task.getType())) {
            switchRecurring.setChecked(true);
            layoutRecurring.setVisibility(View.VISIBLE);
            etInterval.setText(String.valueOf(task.getInterval()));
            String unit = task.getUnit() != null ? task.getUnit() : "Day";
            if (unit.contains("Week")) spinnerUnit.setSelection(1);
            else spinnerUnit.setSelection(0);

            if (task.getStartDate() != null) selectedDate.setTime(task.getStartDate().toDate());
            if (task.getEndDate() != null) {
                selectedEndDate = Calendar.getInstance();
                selectedEndDate.setTime(task.getEndDate().toDate());
            } else selectedEndDate = null;
        } else {
            switchRecurring.setChecked(false);
            layoutRecurring.setVisibility(View.GONE);
            if (task.getExecutionTime() != null) selectedDate.setTime(task.getExecutionTime().toDate());
            selectedEndDate = null;
        }
        updateDateTimeLabels();
    }

    private void checkDifficulty(int xp) {
        if (xp == 1) rgDifficulty.check(R.id.rbVeryEasy);
        else if (xp == 3) rgDifficulty.check(R.id.rbEasy);
        else if (xp == 7) rgDifficulty.check(R.id.rbHard);
        else if (xp == 20) rgDifficulty.check(R.id.rbExtremelyHard);
        else rgDifficulty.clearCheck();
    }

    private void checkImportance(int xp) {
        if (xp == 1) rgImportance.check(R.id.rbNormal);
        else if (xp == 3) rgImportance.check(R.id.rbImportant);
        else if (xp == 10) rgImportance.check(R.id.rbExtremelyImportant);
        else if (xp == 100) rgImportance.check(R.id.rbSpecial);
        else rgImportance.clearCheck();
    }

    private void saveNewTask() {
        Task newTask = buildTaskFromForm(null);
        if (newTask == null) return;

        taskService.addTask(newTask, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(CreateTaskActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CreateTaskActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateTask() {
        if (editingTaskId == null) return;
        Task updated = buildTaskFromForm(editingTask);
        if (updated == null) return;
        updated.setId(editingTaskId);

        taskService.updateTask(updated, new TaskService.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(CreateTaskActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CreateTaskActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private Task buildTaskFromForm(Task baseTask) {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("Name is required"); return null; }

        if (spinnerCategory.getSelectedItemPosition() == AdapterView.INVALID_POSITION || loadedCategories.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return null;
        }

        Task t = new Task();
        if (baseTask != null) t.setStatus(baseTask.getStatus() != null ? baseTask.getStatus() : "active");
        else t.setStatus("active");

        t.setName(name);
        t.setDescription(etDesc.getText().toString().trim());
        t.setDifficultyXp(getDifficultyXp());
        t.setImportanceXp(getImportanceXp());
        t.setCategoryId(loadedCategories.get(spinnerCategory.getSelectedItemPosition()).getId());

        if (switchRecurring.isChecked()) {
            t.setType("RECURRING");
            String intervalStr = etInterval.getText().toString().trim();
            if (intervalStr.isEmpty()) { etInterval.setError("Required"); return null; }
            int interval;
            try { interval = Integer.parseInt(intervalStr); if (interval <= 0) throw new NumberFormatException(); }
            catch (Exception e) { etInterval.setError("Must be positive"); return null; }

            t.setInterval(interval);
            if (spinnerUnit.getSelectedItem() != null) t.setUnit(spinnerUnit.getSelectedItem().toString());
            else t.setUnit("Day");

            t.setStartDate(new com.google.firebase.Timestamp(selectedDate.getTime()));
            if (selectedEndDate != null) {
                Calendar end = (Calendar) selectedEndDate.clone();
                end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59); end.set(Calendar.SECOND, 59); end.set(Calendar.MILLISECOND, 999);
                t.setEndDate(new com.google.firebase.Timestamp(end.getTime()));
            } else t.setEndDate(null);
            t.setExecutionTime(null);
        } else {
            t.setType("SINGLE");
            t.setExecutionTime(new com.google.firebase.Timestamp(selectedDate.getTime()));
            t.setStartDate(null); t.setEndDate(null); t.setInterval(0); t.setUnit(null);
        }
        return t;
    }

    private int getDifficultyXp() {
        int id = rgDifficulty.getCheckedRadioButtonId();
        if (id == R.id.rbVeryEasy) return 1;
        if (id == R.id.rbEasy) return 3;
        if (id == R.id.rbHard) return 7;
        if (id == R.id.rbExtremelyHard) return 20;
        return 0;
    }

    private int getImportanceXp() {
        int id = rgImportance.getCheckedRadioButtonId();
        if (id == R.id.rbNormal) return 1;
        if (id == R.id.rbImportant) return 3;
        if (id == R.id.rbExtremelyImportant) return 10;
        if (id == R.id.rbSpecial) return 100;
        return 0;
    }
}