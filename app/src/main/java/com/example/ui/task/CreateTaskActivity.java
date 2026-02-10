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
    private TextView tvSelectedDate;
    private Button btnPickDate;
    private TextView tvSelectedTime;
    private Button btnPickTime;

    private CategoryRepository categoryRepo = new CategoryRepository();
    private TaskRepository taskRepo = new TaskRepository();
    private List<Category> loadedCategories = new ArrayList<>();
    private Calendar selectedDate = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        initViews();
        loadCategories();

        btnPickDate.setOnClickListener(v -> showDatePicker());
        btnPickTime.setOnClickListener(v -> showTimePicker());

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
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth, 0, 0, 0);
            selectedDate.set(Calendar.MILLISECOND, 0);
            tvSelectedDate.setText("Datum: " + dayOfMonth + "." + (month + 1) + "." + year + ".");
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        if (tvSelectedTime == null || selectedDate == null) return;

        TimePickerDialog timePickerDialog = new TimePickerDialog(CreateTaskActivity.this,
                (view, hourOfDay, minute) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minute);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    String timeText = String.format("Vreme: %02d:%02d", hourOfDay, minute);
                    tvSelectedTime.setText(timeText);
                },
                selectedDate.get(Calendar.HOUR_OF_DAY),
                selectedDate.get(Calendar.MINUTE),
                true
        );
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

    private void saveTask() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Naziv je obavezan");
            return;
        }

        Task newTask = new Task();
        newTask.setName(name);
        newTask.setDescription(etDesc.getText().toString().trim());
        newTask.setCategoryId(loadedCategories.get(spinnerCategory.getSelectedItemPosition()).getId());
        newTask.setDifficultyXp(getDifficultyXp());
        newTask.setImportanceXp(getImportanceXp());
        newTask.setExecutionTime(new com.google.firebase.Timestamp(selectedDate.getTime()));
        newTask.setStatus("aktivan");

        if (switchRecurring.isChecked()) {
            newTask.setType("RECURRING");
            newTask.setInterval(Integer.parseInt(etInterval.getText().toString()));
            newTask.setUnit(spinnerUnit.getSelectedItem().toString());
        } else {
            newTask.setType("SINGLE");
        }

        taskRepo.addTask(newTask, new TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(CreateTaskActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show();
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