package com.example.data.service;

public final class LevelingService {
    private LevelingService() {}

    private static int roundUp100(int x) {
        return (x % 100 == 0) ? x : (x + (100 - x % 100));
    }

    public static int nextXpThreshold(int prevThreshold) {
        return roundUp100(prevThreshold * 2 + prevThreshold / 2);
    }

    public static long nextPp(long prev) {
        return Math.round(prev + 0.75 * prev);
    }
}