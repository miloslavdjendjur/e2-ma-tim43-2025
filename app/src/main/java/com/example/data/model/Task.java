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

    // XP (current/scaled XP used for awarding)
    private int difficultyXp;
    private int importanceXp;

    /**
     * BASE XP values used for quota logic (spec values):
     * difficulty: 1,3,7,20
     * importance: 1,3,10,100
     *
     * If old tasks don't have these saved, we infer them from current XP.
     */
    private Integer baseDifficultyXp;
    private Integer baseImportanceXp;

    // Recurrence
    private int interval;
    private String unit;
    private Timestamp executionTime; // for SINGLE
    private Timestamp startDate;     // for RECURRING
    private Timestamp endDate;       // optional for RECURRING

    private Map<String, String> occurrenceStatuses;
    private Boolean xpProcessed;
    private Map<String, Boolean> occurrenceXpProcessed;

    public Task() {}

    public int getTotalXp() {
        return difficultyXp + importanceXp;
    }

    // ---- BASE XP (for quota) ----

    public int getBaseDifficultyXp() {
        if (baseDifficultyXp != null) return baseDifficultyXp;
        return inferBaseDifficultyFromCurrent(difficultyXp);
    }

    public void setBaseDifficultyXp(Integer baseDifficultyXp) {
        this.baseDifficultyXp = baseDifficultyXp;
    }

    public int getBaseImportanceXp() {
        if (baseImportanceXp != null) return baseImportanceXp;
        return inferBaseImportanceFromCurrent(importanceXp);
    }

    public void setBaseImportanceXp(Integer baseImportanceXp) {
        this.baseImportanceXp = baseImportanceXp;
    }

    private int inferBaseDifficultyFromCurrent(int current) {
        // Allowed base diffs: 1,3,7,20
        int[] base = new int[]{1, 3, 7, 20};
        return nearest(base, current);
    }

    private int inferBaseImportanceFromCurrent(int current) {
        // Allowed base imps: 1,3,10,100
        int[] base = new int[]{1, 3, 10, 100};
        return nearest(base, current);
    }

    private int nearest(int[] base, int value) {
        int best = base[0];
        int bestDiff = Math.abs(value - best);
        for (int i = 1; i < base.length; i++) {
            int d = Math.abs(value - base[i]);
            if (d < bestDiff) {
                bestDiff = d;
                best = base[i];
            }
        }
        return best;
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

    public void setDifficultyXp(int difficultyXp) {
        this.difficultyXp = difficultyXp;
        if (this.baseDifficultyXp == null) {
            if (difficultyXp == 1 || difficultyXp == 3 || difficultyXp == 7 || difficultyXp == 20) {
                this.baseDifficultyXp = difficultyXp;
            }
        }
    }

    public int getImportanceXp() { return importanceXp; }

    public void setImportanceXp(int importanceXp) {
        this.importanceXp = importanceXp;
        if (this.baseImportanceXp == null) {
            if (importanceXp == 1 || importanceXp == 3 || importanceXp == 10 || importanceXp == 100) {
                this.baseImportanceXp = importanceXp;
            }
        }
    }

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
