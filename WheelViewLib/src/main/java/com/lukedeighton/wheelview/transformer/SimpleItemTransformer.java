package com.lukedeighton.wheelview.transformer;

import android.graphics.Rect;

import com.lukedeighton.wheelview.WheelView;

public class SimpleItemTransformer implements WheelItemTransformer {
    @Override
    public void transform(WheelView.ItemState itemState, Rect itemBounds) {
        float radius = itemState.getRadius();
        float x = itemState.getX();
        float y = itemState.getY();
        itemBounds.set(Math.round(x - radius), Math.round(y - radius), Math.round(x + radius), Math.round(y + radius));
    }
}
