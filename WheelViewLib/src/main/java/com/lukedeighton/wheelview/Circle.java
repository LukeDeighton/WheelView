package com.lukedeighton.wheelview;

import android.graphics.Rect;

/**
 * A simple circle with some helper methods used in {@link com.lukedeighton.wheelview.WheelView}
 */
public class Circle {
    final float mCenterX, mCenterY;
    final float mRadius;

    Circle(float centerX, float centerY, float radius) {
        mCenterX = centerX;
        mCenterY = centerY;
        mRadius = radius;
    }

    /**
     * Clamps the value to a number between 0 and the upperLimit
     */
    static int clamp(int value, int upperLimit) {
        if (value < 0) {
            return value + (-1 * (int) Math.floor(value / (float) upperLimit)) * upperLimit;
        } else {
            return value % upperLimit;
        }
    }

    static float clamp180(float value) {
        //TODO clamp(int value, int upperLimit) could use this code? + test it
        return (((value + 180f) % 360f + 360f) % 360f) - 180f;
    }

    /**
     * Returns the shortest angle difference when the inputs range between -180 and 180 (such as from Math.atan2)
     */
    static float shortestAngle(float angleA, float angleB) {
        float angle = angleA - angleB;
        if (angle > 180f) {
            angle -= 360f;
        } else if (angle < -180f) {
            angle += 360f;
        }
        return angle;
    }

    boolean contains(float x, float y) {
        x = this.mCenterX - x;
        y = this.mCenterY - y;
        return x * x + y * y <= mRadius * mRadius;
    }

    Rect getBoundingRect() {
        return new Rect(Math.round(mCenterX - mRadius), Math.round(mCenterY - mRadius),
                Math.round(mCenterX + mRadius), Math.round(mCenterY + mRadius));
    }

    /**
     * The Angle from this circle's center to the position x, y
     * y is considered to go down (like android view system)
     */
    float angleTo(float x, float y) {
        return (float) Math.atan2((mCenterY - y), (x - mCenterX));
    }

    float angleToDegrees(float x, float y) {
        return (float) Math.toDegrees(angleTo(x, y));
    }

    @Override
    public String toString() {
        return "Radius: " + mRadius + " X: " + mCenterX + " Y: " + mCenterY;
    }
}