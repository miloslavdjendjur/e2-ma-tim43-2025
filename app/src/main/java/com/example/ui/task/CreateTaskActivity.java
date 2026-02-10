package com.example.ui.task;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.R;
import com.example.data.model.Category;
import com.example.data.model.Task;
import com.example.data.repo.CategoryRepository;
import java.util.ArrayList;
import java.util.List;

public class CreateTaskActivity extends AppCompatActivity {

    private EditText etName, etDesc, etInterval;
    private Spinner spinnerCategory, spinnerUnit;
    private RadioGroup rgDifficulty, rgImportance;
    private androidx.appcompat.widget.SwitchCompat switchRecurring;
    private LinearLayout layoutRecurring;
    private CategoryRepository categoryRepo = new CategoryRepository();
    private List<Category> loadedCategories = new ArrayList<>();

    private com.example.data.repo.TaskRepository taskRepo = new com.example.data.repo.TaskRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        initViews();
        loadCategories();

        // Logika za prikazivanje opcija ponavljanja
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
    }

    private void loadCategories() {
        categoryRepo.getAllCategories(categories -> {
            loadedCategories = categories;
            List<String> names = new ArrayList<>();
            for (Category c : categories) names.add(c.getName());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(adapter);
        });
    }

    private void saveTask() {
        String name = etName.getText().toString().trim();
        String description = etDesc.getText().toString().trim();

        // Validacija po specifikaciji
        if (name.isEmpty()) {
            etName.setError("Naziv je obavezan");
            return;
        }

        if (spinnerCategory.getSelectedItem() == null) {
            Toast.makeText(this, "Morate izabrati kategoriju", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dobavljanje izabrane kategorije
        Category selectedCategory = loadedCategories.get(spinnerCategory.getSelectedItemPosition());

        // Kreiranje Task objekta
        Task newTask = new Task();
        newTask.setName(name);
        newTask.setDescription(description);
        newTask.setCategoryId(selectedCategory.getId());
        newTask.setDifficultyXp(getDifficultyXp());
        newTask.setImportanceXp(getImportanceXp());

        // Logika za ponavljanje
        if (switchRecurring.isChecked()) {
            newTask.setType("RECURRING");
            String intervalStr = etInterval.getText().toString();
            newTask.setInterval(intervalStr.isEmpty() ? 1 : Integer.parseInt(intervalStr));
            newTask.setUnit(spinnerUnit.getSelectedItem().toString().toLowerCase());
        } else {
            newTask.setType("SINGLE");
        }

        // Po specifikaciji: Vreme izvrsenja (trenutno postavljamo na sada)
        newTask.setExecutionTime(com.google.firebase.Timestamp.now());

        // Repository does its sheit
        taskRepo.addTask(newTask, new com.example.data.repo.TaskRepository.OnTaskActionEventListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(CreateTaskActivity.this, message, Toast.LENGTH_SHORT).show();
                finish(); // Zatvaramo ekran nakon uspešnog čuvanja
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CreateTaskActivity.this, "Greška: " + error, Toast.LENGTH_LONG).show();
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