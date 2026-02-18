package com.example.data.model;

public final class TitleBook {
    private TitleBook(){}

    public static String titleFor(int level){
        if (level <= 1) return "Rookie";
        if (level == 2) return "Figher";
        if (level == 3) return "Veteran";
        return "Hero " + level;
    }
}
