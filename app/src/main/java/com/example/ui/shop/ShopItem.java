package com.example.ui.shop;

import androidx.annotation.DrawableRes;

public class ShopItem {
    // ... tvoja postojeća polja ...
    public String title;
    public String description;
    public int price;
    public int count;
    public boolean isShopItem;
    public String typeCategory;
    public Object typeEnum;
    @DrawableRes public int imageResId;

    public boolean isActive;

    private ShopItem() {}

    public static ShopItem createForShop(String title, String desc, int price, Object typeEnum, String cat, int imageResId) {
        ShopItem item = new ShopItem();
        item.title = title;
        item.description = desc;
        item.price = price;
        item.typeEnum = typeEnum;
        item.typeCategory = cat;
        item.isShopItem = true;
        item.imageResId = imageResId;
        return item;
    }

    public static ShopItem createForInventory(String title, String desc, int count, Object typeEnum, String cat, int imageResId, boolean isActive) {
        ShopItem item = new ShopItem();
        item.title = title;
        item.description = desc;
        item.count = count;
        item.typeEnum = typeEnum;
        item.typeCategory = cat;
        item.isShopItem = false;
        item.imageResId = imageResId;
        item.isActive = isActive;
        return item;
    }
}