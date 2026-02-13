package com.example.data.model;

public class FightResult {
    public boolean bossDefeated;   // Da li je bos pobeđen
    public int coinsEarned;        // Koliko novčića je osvojeno
    public String droppedItemName; // Ime itema (npr. "SWORD", "BOOTS") ili null ako ništa nije palo
    public boolean isWeapon;       // true ako je oružje, false ako je odeća

    public FightResult() {}

    public FightResult(boolean bossDefeated, int coinsEarned, String droppedItemName, boolean isWeapon) {
        this.bossDefeated = bossDefeated;
        this.coinsEarned = coinsEarned;
        this.droppedItemName = droppedItemName;
        this.isWeapon = isWeapon;
    }
}