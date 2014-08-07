package com.lukedeighton.wheelview;

/**
 * A simple class to represent a vector with an add and cross product method.
 */
public class Vector {
    float x, y;

    Vector() {
    }

    void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    float crossProduct(Vector vector) {
        return this.x * vector.y - this.y * vector.x;
    }

    @Override
    public String toString() {
        return "Vector: (" + this.x + ", " + this.y + ")";
    }
}