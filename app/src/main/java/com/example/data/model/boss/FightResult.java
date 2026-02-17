package com.example.data.model.boss;

/**
 * Rezultat borbe sa bosom. Zadržana su postojeća polja radi kompatibilnosti sa UI.
 * Dodata su nova polja koja olakšavaju prikaz na front-u (HP, multiplikatori nagrade).
 */
public class FightResult {

    // --- Existing fields (kompatibilnost) ---
    public boolean bossDefeated;
    public int coinsEarned;
    public String droppedItemName;
    public boolean isWeapon;

    // --- New fields (za UI/animacije) ---
    public long bossMaxHp;
    public long bossRemainingHp;

    /** 1.0 full reward, 0.5 half, 0.0 none */
    public double rewardMultiplier;

    /** 1.0 full drop chance, 0.5 half, 0.0 none */
    public double dropChanceMultiplier;

    public FightResult() {}

    public FightResult(boolean bossDefeated, int coinsEarned, String droppedItemName, boolean isWeapon) {
        this.bossDefeated = bossDefeated;
        this.coinsEarned = coinsEarned;
        this.droppedItemName = droppedItemName;
        this.isWeapon = isWeapon;
    }

    public FightResult(boolean bossDefeated,
                       int coinsEarned,
                       String droppedItemName,
                       boolean isWeapon,
                       long bossMaxHp,
                       long bossRemainingHp,
                       double rewardMultiplier,
                       double dropChanceMultiplier) {
        this.bossDefeated = bossDefeated;
        this.coinsEarned = coinsEarned;
        this.droppedItemName = droppedItemName;
        this.isWeapon = isWeapon;
        this.bossMaxHp = bossMaxHp;
        this.bossRemainingHp = bossRemainingHp;
        this.rewardMultiplier = rewardMultiplier;
        this.dropChanceMultiplier = dropChanceMultiplier;
    }
}
