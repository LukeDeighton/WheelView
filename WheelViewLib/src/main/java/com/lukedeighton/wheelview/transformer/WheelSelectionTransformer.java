package com.lukedeighton.wheelview.transformer;

import android.graphics.drawable.Drawable;

import com.lukedeighton.wheelview.WheelView;

public interface WheelSelectionTransformer {
    void transform(Drawable drawable, WheelView.ItemState itemState);
}
