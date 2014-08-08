package com.lukedeighton.wheelview.transformer;

import android.graphics.Rect;

import com.lukedeighton.wheelview.WheelView;

public interface WheelItemTransformer {
    void transform(WheelView.ItemState itemState, Rect itemBounds);
}
