package com.example.data.model;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Task {

    // Types
    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_RECURRING = "RECURRING";

    // Statuses
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_CANCELED = "canceled";

    private String id;
    private String name;
    private String description;
    private String categoryId;

    // SINGLE status (whole task)
    private String status;

    private String type;
    private String userId;

    // XP (your current logic)
    private int difficultyXp;
    private int importanceXp;

    // Recurrence
    private int interval;
    private String unit;
    private Timestamp executionTime; // for SINGLE
    private Timestamp startDate;     // for RECURRING
    private Timestamp endDate;       // optional for RECURRING

    /**
     * For recurring tasks: status is per occurrence date.
     * Key format: yyyy-MM-dd : status
     */
    private Map<String, String> occurrenceStatuses;

    /**
     * XP processing flags (to prevent double-awarding if user toggles status back/forth).
     * SINGLE: xpProcessed == true means we already processed XP for this task when it was first set to DONE.
     * RECURRING: occurrenceXpProcessed[dateKey] == true means we processed XP for that occurrence.
     */
    private Boolean xpProcessed;
    private Map<String, Boolean> occurrenceXpProcessed;

    public Task() {}

    public int getTotalXp() {
        return difficultyXp + importanceXp;
    }

    // ---- XP processed helpers ----

    public boolean isXpProcessed() {
        return xpProcessed != null && xpProcessed;
    }

    public void setXpProcessed(Boolean xpProcessed) {
        this.xpProcessed = xpProcessed;
    }

    public Map<String, Boolean> getOccurrenceXpProcessed() {
        return occurrenceXpProcessed;
    }

    public void setOccurrenceXpProcessed(Map<String, Boolean> occurrenceXpProcessed) {
        this.occurrenceXpProcessed = occurrenceXpProcessed;
    }

    public boolean isOccurrenceXpProcessed(String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) return false;
        if (occurrenceXpProcessed == null) return false;
        Boolean v = occurrenceXpProcessed.get(dateKey);
        return v != null && v;
    }

    public void setOccurrenceXpProcessed(String dateKey, boolean processed) {
        if (dateKey == null || dateKey.isEmpty()) return;
        if (occurrenceXpProcessed == null) occurrenceXpProcessed = new HashMap<>();
        occurrenceXpProcessed.put(dateKey, processed);
    }

    // ---- Occurrence status helpers ----

    public Map<String, String> getOccurrenceStatuses() {
        return occurrenceStatuses;
    }

    public void setOccurrenceStatuses(Map<String, String> occurrenceStatuses) {
        this.occurrenceStatuses = occurrenceStatuses;
    }

    public String getOccurrenceStatusForDateKey(String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) return null;
        if (occurrenceStatuses == null) return null;
        return occurrenceStatuses.get(dateKey);
    }

    public void setOccurrenceStatusForDateKey(String dateKey, String status) {
        if (dateKey == null || dateKey.isEmpty()) return;
        if (occurrenceStatuses == null) occurrenceStatuses = new HashMap<>();
        occurrenceStatuses.put(dateKey, status);
    }

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getDifficultyXp() { return difficultyXp; }
    public void setDifficultyXp(int difficultyXp) { this.difficultyXp = difficultyXp; }

    public int getImportanceXp() { return importanceXp; }
    public void setImportanceXp(int importanceXp) { this.importanceXp = importanceXp; }

    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Timestamp getExecutionTime() { return executionTime; }
    public void setExecutionTime(Timestamp executionTime) { this.executionTime = executionTime; }

    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp startDate) { this.startDate = startDate; }

    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp endDate) { this.endDate = endDate; }
}
