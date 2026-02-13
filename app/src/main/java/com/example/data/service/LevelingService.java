package com.example.data.service;

import com.example.data.model.TitleBook;
import com.example.data.model.User;
import com.google.firebase.Timestamp;

public final class LevelingService {
    private LevelingService() {}

    public static int nextXpThreshold(int prevThreshold) {
        return roundUp100((int)(prevThreshold * 2 + prevThreshold / 2));
    }

    public static int getThresholdForLevel(int level) {
        int threshold = 200;
        for (int i = 1; i < level; i++) {
            threshold = nextXpThreshold(threshold);
        }
        return threshold;
    }

    public static long nextPp(long prev) {
        if (prev == 0) return 40;
        return Math.round(prev + 0.75 * prev);
    }

    private static int roundUp100(int x) {
        return (x % 100 == 0) ? x : (x + (100 - x % 100));
    }

    public static boolean addXp(User user, int xpGained) {
        if (user == null) return false;

        user.xp += xpGained;
        int currentThreshold = getThresholdForLevel(user.level);
        boolean leveledUp = false;

        while (user.xp >= currentThreshold) {
            user.xp -= currentThreshold;
            user.level++;

            user.lastLevelUpDate = Timestamp.now();

            user.pp = nextPp(user.pp);
            user.title = TitleBook.titleFor(user.level);

            currentThreshold = nextXpThreshold(currentThreshold);
            leveledUp = true;
        }

        return leveledUp;
    }
}
