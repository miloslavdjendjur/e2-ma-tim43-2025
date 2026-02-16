package com.example.data.model.equipment;

import com.example.data.model.equipment.type.ClothesType;

public class Clothes {
    public String id;
    public ClothesType type;
    public boolean active;       // da li je obučeno
    public int usesLeft;         // 2 borbe
    public int stackedPercent;   // akumulirani procenat efekta (npr. 20 za 2 para rukavica)

    public int count;
    public Clothes() {}
}