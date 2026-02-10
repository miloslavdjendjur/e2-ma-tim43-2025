package com.example.data.model;

public final class TitleBook {
    private TitleBook(){}

    public static String titleFor(int level){
        if (level <= 1) return "Novajlija";
        if (level == 2) return "Borac";
        if (level == 3) return "Veteran";
        return "Heroj " + level;
    }
}
