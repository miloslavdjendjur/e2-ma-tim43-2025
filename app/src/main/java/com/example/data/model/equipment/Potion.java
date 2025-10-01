package com.example.data.model.equipment;

import com.example.data.model.equipment.type.PotionType;

public class Potion {
    public String id;
    public PotionType type;
    public int count;          // koliko u inventaru
    public boolean pendingUse; // za jednokratne – označi da će se potrošiti u prvoj narednoj borbi

    public Potion() {}
}