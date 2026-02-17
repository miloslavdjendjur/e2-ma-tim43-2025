package com.example.data.model.boss;

import com.google.firebase.Timestamp;

public class Boss {
    private String id;
    private int level;          // Nivo bosa
    private long maxHp;         // Ukupan HP
    private long currentHp;     // Preostali HP

    // Source of truth is "status". Ovo polje je zadržano for backward compatibility with existing code/UI.
    private boolean defeated;   // Da li je pobeđen (izvodi se iz status-a)

    private int attacksLeft;    // Brojač (5..)
    private String status;      // "ACTIVE", "DEFEATED", "ESCAPED"
    private Timestamp createdAt;

    public Boss() {}

    public Boss(int level, long maxHp) {
        this.level = level;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.attacksLeft = 5;
        this.status = "ACTIVE";
        this.defeated = false;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getMaxHp() { return maxHp; }
    public void setMaxHp(long maxHp) { this.maxHp = maxHp; }

    public long getCurrentHp() { return currentHp; }
    public void setCurrentHp(long currentHp) { this.currentHp = currentHp; }

    public boolean isDefeated() { return defeated; }

    public void setDefeated(boolean defeated) {
        this.defeated = defeated;
        if (defeated) {
            this.status = "DEFEATED";
        } else {
            if (this.status == null || "DEFEATED".equals(this.status)) {
                this.status = "ACTIVE";
            }
        }
    }

    public int getAttacksLeft() { return attacksLeft; }
    public void setAttacksLeft(int attacksLeft) { this.attacksLeft = attacksLeft; }

    public String getStatus() { return status; }

    public void setStatus(String status) {
        this.status = status;
        this.defeated = "DEFEATED".equals(status);
    }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
