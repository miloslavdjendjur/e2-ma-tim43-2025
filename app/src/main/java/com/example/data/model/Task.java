package com.example.data.model;

public class Task {
    public enum Status { ACTIVE, DONE, CANCELED, PAUSED } // mapira se na stanja iz specifikacije
    public String id;
    public String title;
    public String categoryName;
    public int categoryColor; // ARGB
    public String time;       // "08:30" (za prikaz)
    public int xp;            // zbir težina+bitnost
    public Status status;

    public Task() {}
    public Task(String id, String title, String categoryName, int categoryColor, String time, int xp, Status status) {
        this.id = id; this.title = title; this.categoryName = categoryName; this.categoryColor = categoryColor;
        this.time = time; this.xp = xp; this.status = status;
    }
}
