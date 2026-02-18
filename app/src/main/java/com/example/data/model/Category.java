package com.example.data.model;

public class Category {
    private String id;
    private String userId;
    private String name;
    private String colorHex;

    public Category() {}

    public Category(String id, String userId, String name, String colorHex) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.colorHex = colorHex;
    }

    public String getId() { return id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
}
