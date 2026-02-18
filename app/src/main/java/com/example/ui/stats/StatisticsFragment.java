package com.example.ui.stats;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.data.model.Task;
import com.example.data.repo.TaskRepository;
import com.example.myapplication.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class StatisticsFragment extends Fragment {

    private PieChart pieChartStatus;
    private BarChart barChartCategories;
    private LineChart lineChartXp;
    private LineChart lineChartDifficulty;
    private TextView tvCurrentStreak, tvLongestStreak;

    private final TaskRepository taskRepository = new TaskRepository();

    public StatisticsFragment() {
        super(R.layout.fragment_statistics);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        pieChartStatus = view.findViewById(R.id.pieChartStatus);
        barChartCategories = view.findViewById(R.id.barChartCategories);
        lineChartXp = view.findViewById(R.id.lineChartXp);
        lineChartDifficulty = view.findViewById(R.id.lineChartDifficulty);
        tvCurrentStreak = view.findViewById(R.id.tvCurrentStreak);
        tvLongestStreak = view.findViewById(R.id.tvLongestStreak);

        loadData();
    }

    private void loadData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> categoryNameMap = new HashMap<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("name");

                        if (doc.getId() != null && name != null) {
                            categoryNameMap.put(doc.getId(), name);
                        }
                    }

                    taskRepository.getTasks(tasks -> {
                        if (tasks == null) return;
                        calculateAndDisplayStats(tasks, categoryNameMap);
                    });
                })
                .addOnFailureListener(e -> {
                    // Fallback
                    taskRepository.getTasks(tasks -> {
                        if (tasks == null) return;
                        calculateAndDisplayStats(tasks, new HashMap<>());
                    });
                });
    }

    private void calculateAndDisplayStats(List<Task> tasks, Map<String, String> categoryNameMap) {
        int doneCount = 0;
        int canceledCount = 0;
        int activeCount = 0;

        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> xpPerDay = new TreeMap<>();
        Map<String, List<Integer>> difficultyPerDay = new TreeMap<>();
        List<LocalDate> datesWithDoneTasks = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Task task : tasks) {

            if (Task.TYPE_SINGLE.equals(task.getType())) {
                if (Task.STATUS_DONE.equals(task.getStatus())) {
                    doneCount++;
                    processDoneTask(task, task.getExecutionTime(), categoryCounts, xpPerDay, difficultyPerDay, datesWithDoneTasks, categoryNameMap);
                } else if (Task.STATUS_CANCELED.equals(task.getStatus())) {
                    canceledCount++;
                } else {
                    activeCount++;
                }
            }
            else if (Task.TYPE_RECURRING.equals(task.getType())) {
                Map<String, String> statuses = task.getOccurrenceStatuses();
                if (statuses != null) {
                    for (Map.Entry<String, String> entry : statuses.entrySet()) {
                        String dateStr = entry.getKey();
                        String status = entry.getValue();

                        if (Task.STATUS_DONE.equals(status)) {
                            doneCount++;
                            try {
                                Date d = sdf.parse(dateStr);
                                if (d != null) {
                                    Timestamp ts = new Timestamp(d);
                                    processDoneTask(task, ts, categoryCounts, xpPerDay, difficultyPerDay, datesWithDoneTasks, categoryNameMap);
                                }
                            } catch (Exception e) { e.printStackTrace(); }

                        } else if (Task.STATUS_CANCELED.equals(status)) {
                            canceledCount++;
                        }
                    }
                }
                activeCount++;
            }
        }

        setupPieChart(doneCount, canceledCount, activeCount);
        setupBarChart(categoryCounts);
        setupXpLineChart(xpPerDay);
        setupDifficultyLineChart(difficultyPerDay);
        calculateStreaks(datesWithDoneTasks);
    }

    private void processDoneTask(Task task, Timestamp time,
                                 Map<String, Integer> catCounts,
                                 Map<String, Integer> xpMap,
                                 Map<String, List<Integer>> diffMap,
                                 List<LocalDate> datesList,
                                 Map<String, String> categoryNameMap) {
        if (time == null) return;

        String catId = task.getCategoryId();
        String catName = "Other"; // Default

        if (catId != null) {
            catName = categoryNameMap.getOrDefault(catId, "Other");
        }

        catCounts.put(catName, catCounts.getOrDefault(catName, 0) + 1);

        LocalDate date = time.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String dateKey = date.toString();

        int xp = task.getTotalXp();
        xpMap.put(dateKey, xpMap.getOrDefault(dateKey, 0) + xp);

        if (!diffMap.containsKey(dateKey)) {
            diffMap.put(dateKey, new ArrayList<>());
        }
        diffMap.get(dateKey).add(task.getDifficultyXp());

        datesList.add(date);
    }

    private void setupPieChart(int done, int canceled, int active) {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(done, "Done"));
        entries.add(new PieEntry(active, "Active/Paused"));
        entries.add(new PieEntry(canceled, "Canceled"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{Color.GREEN, Color.GRAY, Color.RED});
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData data = new PieData(dataSet);
        pieChartStatus.setData(data);
        pieChartStatus.getDescription().setEnabled(false);
        pieChartStatus.setCenterText("Total: " + (done + canceled + active));
        pieChartStatus.animateY(1000);
        pieChartStatus.invalidate();
    }

    private void setupBarChart(Map<String, Integer> categoryCounts) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue()));
            labels.add(entry.getKey());
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Tasks");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);

        BarData data = new BarData(dataSet);
        barChartCategories.setData(data);
        barChartCategories.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChartCategories.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChartCategories.getXAxis().setGranularity(1f);
        barChartCategories.getDescription().setEnabled(false);
        barChartCategories.animateY(1000);
        barChartCategories.invalidate();
    }

    private void setupXpLineChart(Map<String, Integer> xpPerDay) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            int xp = xpPerDay.getOrDefault(key, 0);
            entries.add(new Entry(6 - i, xp));
            labels.add(day.format(DateTimeFormatter.ofPattern("dd.MM")));
        }

        LineDataSet dataSet = new LineDataSet(entries, "XP");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setValueTextSize(10f);

        LineData data = new LineData(dataSet);
        lineChartXp.setData(data);
        lineChartXp.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineChartXp.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChartXp.getDescription().setEnabled(false);
        lineChartXp.invalidate();
    }

    private void setupDifficultyLineChart(Map<String, List<Integer>> diffMap) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            List<Integer> diffs = diffMap.get(key);

            float avg = 0;
            if (diffs != null && !diffs.isEmpty()) {
                int sum = 0;
                for (int d : diffs) sum += d;
                avg = (float) sum / diffs.size();
            }

            entries.add(new Entry(6 - i, avg));
            labels.add(day.format(DateTimeFormatter.ofPattern("dd.MM")));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Average difficulty (XP)");
        dataSet.setColor(Color.MAGENTA);
        dataSet.setCircleColor(Color.MAGENTA);
        dataSet.setLineWidth(2f);
        dataSet.setDrawFilled(true);

        LineData data = new LineData(dataSet);
        lineChartDifficulty.setData(data);
        lineChartDifficulty.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineChartDifficulty.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChartDifficulty.getDescription().setEnabled(false);
        lineChartDifficulty.invalidate();
    }

    private void calculateStreaks(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            tvCurrentStreak.setText("0");
            tvLongestStreak.setText("0");
            return;
        }

        Collections.sort(dates);
        List<LocalDate> uniqueDates = new ArrayList<>();
        uniqueDates.add(dates.get(0));
        for (int i = 1; i < dates.size(); i++) {
            if (!dates.get(i).equals(dates.get(i - 1))) {
                uniqueDates.add(dates.get(i));
            }
        }

        int maxStreak = 0;
        int currentStreak = 0;
        int tempStreak = 1;

        for (int i = 1; i < uniqueDates.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(uniqueDates.get(i - 1), uniqueDates.get(i));
            if (daysBetween == 1) {
                tempStreak++;
            } else {
                maxStreak = Math.max(maxStreak, tempStreak);
                tempStreak = 1;
            }
        }
        maxStreak = Math.max(maxStreak, tempStreak);

        LocalDate today = LocalDate.now();
        LocalDate lastActivity = uniqueDates.get(uniqueDates.size() - 1);

        long diff = ChronoUnit.DAYS.between(lastActivity, today);
        if (diff > 1) {
            currentStreak = 0;
        } else {
            currentStreak = 1;
            for (int i = uniqueDates.size() - 2; i >= 0; i--) {
                long d = ChronoUnit.DAYS.between(uniqueDates.get(i), uniqueDates.get(i + 1));
                if (d == 1) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        }

        tvCurrentStreak.setText(String.valueOf(currentStreak));
        tvLongestStreak.setText(String.valueOf(maxStreak));
    }
}