package com.example.ui.task;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.R;
import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.data.repo.CategoryRepository;
import com.example.data.repo.TaskRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CreateTaskActivity extends AppCompatActivity {

    private EditText etName, etDesc, etInterval;
    private Spinner spinnerCategory, spinnerUnit;
    private RadioGroup rgDifficulty, rgImportance;
    private androidx.appcompat.widget.SwitchCompat switchRecurring;
    private LinearLayout layoutRecurring;
    private TextView tvSelectedDate, tvSelectedTime, tvSelectedEndDate;
    private Button btnPickDate, btnPickTime, btnPickEndDate;

    private CategoryRepository categoryRepo = new CategoryRepository();
    private TaskRepository taskRepo = new TaskRepository();
    private List<Category> loadedCategories = new ArrayList<>();
    private Calendar selectedDate = Calendar.getInstance();
    private Calendar selectedEndDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        initViews();
        loadCategories();
        setupUnitSpinner();

        btnPickDate.setOnClickListener(v -> showDatePicker(selectedDate, tvSelectedDate, "Date"));
        btnPickTime.setOnClickListener(v -> showTimePicker());
        btnPickEndDate.setOnClickListener(v -> {
            if (selectedEndDate == null) selectedEndDate = Calendar.getInstance();
            showDatePicker(selectedEndDate, tvSelectedEndDate, "End Date");
        });

        switchRecurring.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutRecurring.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        findViewById(R.id.btnSaveTask).setOnClickListener(v -> saveTask());
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

    private void showDatePicker(Calendar targetCal, TextView targetTv, String label) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            targetCal.set(year, month, dayOfMonth, 0, 0, 0);
            targetCal.set(Calendar.MILLISECOND, 0);
            targetTv.setText(label + ": " + dayOfMonth + "/" + (month + 1) + "/" + year);
        }, targetCal.get(Calendar.YEAR), targetCal.get(Calendar.MONTH), targetCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minute);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    tvSelectedTime.setText(String.format("Time: %02d:%02d", hourOfDay, minute));
                },
                selectedDate.get(Calendar.HOUR_OF_DAY),
                selectedDate.get(Calendar.MINUTE),
                true);
        timePickerDialog.show();
    }

    private void loadCategories() {
        categoryRepo.getAllCategories(categories -> {
            loadedCategories = categories;
            List<String> names = new ArrayList<>();
            for (Category c : categories) names.add(c.getName());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(adapter);
        });
    }

    private void setupUnitSpinner() {
        String[] units = {"Day", "Week"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(unitAdapter);
    }

    private void saveTask() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Name is required");
            return;
        }

        // Provera za kategoriju - ako se još učitavaju, spreči pucanje
        if (spinnerCategory.getSelectedItemPosition() == AdapterView.INVALID_POSITION) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        Task newTask = new Task();
        newTask.setName(name);
        newTask.setDescription(etDesc.getText().toString().trim());
        newTask.setDifficultyXp(getDifficultyXp());
        newTask.setImportanceXp(getImportanceXp());
        newTask.setCategoryId(loadedCategories.get(spinnerCategory.getSelectedItemPosition()).getId());
        newTask.setStatus("active");

        if (switchRecurring.isChecked()) {
            newTask.setType("RECURRING");

            String intervalStr = etInterval.getText().toString().trim();
            if (intervalStr.isEmpty()) {
                etInterval.setError("Interval is required for recurring tasks");
                return;
            }
            newTask.setInterval(Integer.parseInt(intervalStr));

            if (spinnerUnit.getSelectedItem() != null) {
                newTask.setUnit(spinnerUnit.getSelectedItem().toString());
            } else {
                newTask.setUnit("Day");
            }

            newTask.setStartDate(new com.google.firebase.Timestamp(selectedDate.getTime()));

            if (selectedEndDate != null) {
                newTask.setEndDate(new com.google.firebase.Timestamp(selectedEndDate.getTime()));
            }
        } else {
            newTask.setType("SINGLE");
            newTask.setExecutionTime(new com.google.firebase.Timestamp(selectedDate.getTime()));
        }

        taskRepo.addTask(newTask, new TaskRepository.OnTaskActionEventListener() {
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