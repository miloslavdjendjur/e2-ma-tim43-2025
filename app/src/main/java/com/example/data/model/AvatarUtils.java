package com.example.data.model;

import com.example.myapplication.R;

public final class AvatarUtils {
    private AvatarUtils() {}

    // Returns drawable resource for avatarIndex (0..9).
    // Expected PNG names in res/drawable: avatar1..avatar10
    public static int imageResForAvatarIndex(int avatarIndex) {
        switch (avatarIndex) {
            case 0: return R.drawable.avatar1;
            case 1: return R.drawable.avatar2;
            case 2: return R.drawable.avatar3;
            case 3: return R.drawable.avatar4;
            case 4: return R.drawable.avatar5;
            case 5: return R.drawable.avatar6;
            case 6: return R.drawable.avatar7;
            case 7: return R.drawable.avatar8;
            case 8: return R.drawable.avatar9;
            case 9: return R.drawable.avatar10;
            default: return R.drawable.avatar1;
        }
    }
}
