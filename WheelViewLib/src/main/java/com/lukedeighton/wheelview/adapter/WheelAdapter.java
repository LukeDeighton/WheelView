package com.lukedeighton.wheelview.adapter;

import android.graphics.drawable.Drawable;

public interface WheelAdapter {
    Drawable getDrawable(int position);

    int getCount();
}
