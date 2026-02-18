package com.example.ui.shop;

import androidx.annotation.DrawableRes;

public class ShopItem {
    public String title;
    public String description;
    public int price;
    public int count;
    public boolean isShopItem;

    public String typeCategory;
    public Object typeEnum;

    @DrawableRes
    public int imageResId; // <--- NOVO POLJE ZA SLIKU

    private ShopItem() {}

    // Ažurirana metoda za Shop
    public static ShopItem createForShop(String title, String desc, int price, Object typeEnum, String cat, int imageResId) {
        ShopItem item = new ShopItem();
        item.title = title;
        item.description = desc;
        item.price = price;
        item.typeEnum = typeEnum;
        item.typeCategory = cat;
        item.isShopItem = true;
        item.imageResId = imageResId; // <--- Setujemo sliku
        return item;
    }

    // Ažurirana metoda za Inventar
    public static ShopItem createForInventory(String title, String desc, int count, Object typeEnum, String cat, int imageResId) {
        ShopItem item = new ShopItem();
        item.title = title;
        item.description = desc;
        item.count = count;
        item.typeEnum = typeEnum;
        item.typeCategory = cat;
        item.isShopItem = false;
        item.imageResId = imageResId; // <--- Setujemo sliku
        return item;
    }
}