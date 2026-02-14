package com.example.data.model.boss;

public class AttackResult {
    public boolean hit;
    public int damageDealt;
    public long bossRemainingHp;
    public int attacksLeft;
    public int roll;

    public AttackResult() {}

    public AttackResult(boolean hit, int damageDealt, long bossRemainingHp, int attacksLeft, int roll) {
        this.hit = hit;
        this.damageDealt = damageDealt;
        this.bossRemainingHp = bossRemainingHp;
        this.attacksLeft = attacksLeft;
        this.roll = roll;
    }
}

