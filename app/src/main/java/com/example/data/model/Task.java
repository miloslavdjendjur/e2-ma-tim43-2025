package com.example.data.model;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Task {

    // Status values (možeš koristiti svuda kao string)
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_CANCELED = "canceled";

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_RECURRING = "RECURRING";

    private String id;
    private String name;
    private String description;
    private String categoryId;
    private String status;     // za SINGLE ili fallback
    private String type;
    private String userId;

    // XP Logika
    private int difficultyXp;
    private int importanceXp;

    // Ponavljanje
    private int interval;
    private String unit;
    private Timestamp executionTime;
    private Timestamp startDate;
    private Timestamp endDate;
    private Map<String, String> occurrenceStatuses;

    public Task() {}

    public int getTotalXp() {
        return difficultyXp + importanceXp;
    }

    // Helpers za recurring
    public Map<String, String> getOccurrenceStatuses() {
        if (occurrenceStatuses == null) occurrenceStatuses = new HashMap<>();
        return occurrenceStatuses;
    }

    public void setOccurrenceStatuses(Map<String, String> occurrenceStatuses) {
        this.occurrenceStatuses = occurrenceStatuses;
    }

    public String getOccurrenceStatusForDateKey(String dateKey) {
        if (dateKey == null) return null;
        if (occurrenceStatuses == null) return null;
        return occurrenceStatuses.get(dateKey);
    }

    public void setOccurrenceStatusForDateKey(String dateKey, String status) {
        if (dateKey == null) return;
        if (occurrenceStatuses == null) occurrenceStatuses = new HashMap<>();
        occurrenceStatuses.put(dateKey, status);
    }

    // Get/Set

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

    public void setUserId(String userId) { this.userId = userId; }
    public String getUserId() { return userId; }

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
