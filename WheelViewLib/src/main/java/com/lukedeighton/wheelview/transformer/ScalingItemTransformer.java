package com.lukedeighton.wheelview.transformer;

import android.graphics.Rect;

import com.lukedeighton.wheelview.WheelView;

public class ScalingItemTransformer implements WheelItemTransformer {
    @Override
    public void transform(WheelView.ItemState itemState, Rect itemBounds) {
        float scale = itemState.getAngleFromSlection() * 0.014f;
        scale = Math.min(1.12f, 1.15f - Math.min(0.25f, Math.abs(scale)));
        float radius = itemState.getRadius() * scale;
        float x = itemState.getX();
        float y = itemState.getY();
        itemBounds.set(Math.round(x - radius), Math.round(y - radius), Math.round(x + radius), Math.round(y + radius));
    }
}
