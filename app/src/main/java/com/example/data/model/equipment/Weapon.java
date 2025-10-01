package com.example.data.model.equipment;

import com.example.data.model.equipment.type.WeaponType;

public class Weapon {
    public String id;
    public WeaponType type;
    public int level;        // upgrade nivoi
    public double dropProbBonus; // 0.0002 po duplikatu; +0.0001 po upgrade (spec detalj)

    public Weapon() {}
}