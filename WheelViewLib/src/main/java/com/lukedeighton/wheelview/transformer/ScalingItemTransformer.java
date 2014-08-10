package com.lukedeighton.wheelview.transformer;

import android.graphics.Rect;

import com.lukedeighton.wheelview.Circle;
import com.lukedeighton.wheelview.WheelView;

public class ScalingItemTransformer implements WheelItemTransformer {
    @Override
    public void transform(WheelView.ItemState itemState, Rect itemBounds) {
        float scale = itemState.getAngleFromSelection() * 0.014f;
        scale = Math.min(1.12f, 1.15f - Math.min(0.25f, Math.abs(scale)));
        Circle bounds = itemState.getBounds();
        float radius = bounds.getRadius() * scale;
        float x = bounds.getCenterX();
        float y = bounds.getCenterY();
        itemBounds.set(Math.round(x - radius), Math.round(y - radius), Math.round(x + radius), Math.round(y + radius));
    }
}
