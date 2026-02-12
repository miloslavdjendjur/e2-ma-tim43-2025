package com.example.data.service;

import com.example.data.model.TitleBook;
import com.example.data.model.User;

public final class LevelingService {
    private LevelingService() {}

    /**
     * Računa sledeći prag na osnovu trenutnog praga.
     * Formula: roundUp100(prev * 2.5)
     */
    public static int nextXpThreshold(int prevThreshold) {
        return roundUp100((int)(prevThreshold * 2 + prevThreshold / 2));
    }

    /**
     * Računa prag za određeni nivo iterativno (počevši od osnovnog praga 200).
     */
    public static int getThresholdForLevel(int level) {
        int threshold = 200; // Base threshold for Level 1 -> 2
        for (int i = 1; i < level; i++) {
            threshold = nextXpThreshold(threshold);
        }
        return threshold;
    }

    /**
     * Računa sledeći broj PP (Power Points) na osnovu prethodnog.
     */
    public static long nextPp(long prev) {
        if (prev == 0) return 40; // Početna vrednost ako je korisnik na 0
        return Math.round(prev + 0.75 * prev);
    }

    private static int roundUp100(int x) {
        return (x % 100 == 0) ? x : (x + (100 - x % 100));
    }

    /**
     * Glavna metoda: Dodaje XP korisniku i procesira Level Up (može više nivoa odjednom).
     * Modifikuje prosleđeni User objekat.
     *
     * @param user Korisnik kojeg ažuriramo
     * @param xpGained Količina novog XP-a
     * @return true ako se desio Level Up, false inače.
     */
    public static boolean addXp(User user, int xpGained) {
        if (user == null) return false;

        user.xp += xpGained;
        int currentThreshold = getThresholdForLevel(user.level);
        boolean leveledUp = false;

        // While petlja u slučaju da dobije toliko XP-a da skoči više nivoa odjednom
        while (user.xp >= currentThreshold) {
            user.xp -= currentThreshold; // Resetujemo XP (relativni sistem) ili oduzimamo prag
            user.level++;

            // Ažuriraj PP
            user.pp = nextPp(user.pp);

            // Ažuriraj Titulu
            user.title = TitleBook.titleFor(user.level);

            // Izračunaj prag za sledeći krug petlje
            currentThreshold = nextXpThreshold(currentThreshold);
            leveledUp = true;
        }

        return leveledUp;
    }
}