/**
 *
 *   Copyright (C) 2014 Luke Deighton
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.lukedeighton.wheelview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.InflateException;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;

import com.lukedeighton.wheelview.adapter.WheelAdapter;
import com.lukedeighton.wheelview.transformer.FadingSelectionTransformer;
import com.lukedeighton.wheelview.transformer.ScalingItemTransformer;
import com.lukedeighton.wheelview.transformer.WheelItemTransformer;
import com.lukedeighton.wheelview.transformer.WheelSelectionTransformer;

import java.util.ArrayList;
import java.util.List;

//TODO onWheelItemSelected callback for when the wheel has settled (0 angular velocity), and one when it is passed
//TODO empty - physics to spring away - prevent movement out from edge
//TODO sticky selection - always settle on a state that completely selects an item
//TODO circular clip option?
//TODO Saving State during screen rotate etc. SavedState extends BaseSavedState
//TODO handle measurement of view!
//TODO can items be rendered as views or use recyclerView - use viewgroup?
//TODO onWheelItemVisibilityChange needs to factor in when items are cycled within view bounds and should that have another callback?
//TODO option to get wheel state (either flinging or dragging)
//TODO item radius works separately ? uses min angle etc. to figure out in the layout event

public class WheelView extends View {

    private static final Rect sTempRect = new Rect();

    private static final float VELOCITY_FRICTION_COEFFICIENT = 0.015f;
    private static final float CONSTANT_FRICTION_COEFFICIENT = 0.0028f;
    private static final float ANGULAR_VEL_COEFFICIENT = 22f;
    private static final float MAX_ANGULAR_VEL = 0.3f;

    private static final int LEFT_MASK = 0x01;
    private static final int RIGHT_MASK = 0x02;
    private static final int TOP_MASK = 0x04;
    private static final int BOTTOM_MASK = 0x08;

    private static final int NEVER_USED = 0;

    //The touch factors decrease the drag movement towards the center of the wheel. It is there so
    //that dragging the wheel near the center doesn't cause the wheel's angle to change
    //drastically. It is squared to provide a linear function once multiplied by 1/r^2
    private static final int TOUCH_FACTOR_SIZE = 20;
    private static final float TOUCH_DRAG_COEFFICIENT = 0.8f;

    private static final float[] TOUCH_FACTORS;
    static {
        int size = TOUCH_FACTOR_SIZE;
        TOUCH_FACTORS = new float[size];
        int maxIndex = size - 1;
        float numerator = size * size;
        for (int i = 0; i < size; i++) {
            int factor = maxIndex - i + 1;
            TOUCH_FACTORS[i] = (1 - factor * factor / numerator) * TOUCH_DRAG_COEFFICIENT;
        }
    }

    private static final float CLICK_MAX_DRAGGED_ANGLE = 1.5f;

    private VelocityTracker mVelocityTracker;
    private Vector mForceVector = new Vector();
    private Vector mRadiusVector = new Vector();
    private float mAngle;
    private float mAngularVelocity;
    private long mLastUpdateTime;
    private boolean mRequiresUpdate;
    private int mSelectedPosition;
    private float mLastWheelTouchX;
    private float mLastWheelTouchY;

    private CacheItem[] mItemCacheArray;
    private Drawable mWheelDrawable;
    private Drawable mEmptyItemDrawable;
    private Drawable mSelectionDrawable;

    private boolean mIsRepeatable;
    private boolean mIsWheelDrawableRotatable = true;

    /**
     * The item angle is the angle covered per item on the wheel and is in degrees.
     * The {@link #mItemAnglePadding} is included in the item angle.
     */
    private float mItemAngle;

    /**
     * Angle padding is in degrees and reduces the wheel's items size during layout
     */
    private float mItemAnglePadding;

    /**
     * Selection Angle is the angle at which an item is considered selected.
     * The {@link #mOnItemSelectListener} is called when the 'most selected' item changes.
     */
    private float mSelectionAngle;

    private int mSelectionPadding;

    private int mWheelPadding;
    private int mWheelToItemDistance;
    private int mItemRadius;
    private int mRadius;
    private int mRadiusSquared;
    private int mOffsetX;
    private int mOffsetY;
    private int mItemCount;

    private int mWheelPosition;
    private int mLeft, mTop, mWidth, mHeight;
    private Rect mViewBounds = new Rect();
    private Circle mWheelBounds;

    /**
     * Wheel item bounds are always pre-rotation and based on the {@link #mSelectionAngle}
     */
    private List<Circle> mWheelItemBounds;

    /**
     * The ItemState contain the rotated position
     */
    private List<ItemState> mItemStates;
    private int mAdapterItemCount;

    private boolean mIsDraggingWheel;
    private float mLastTouchAngle;
    private ItemState mClickedItem;
    private float mDraggedAngle;

    private OnWheelItemClickListener mOnItemClickListener;
    private OnWheelAngleChangeListener mOnAngleChangeListener;
    private OnWheelItemSelectListener mOnItemSelectListener;
    private OnWheelItemVisibilityChangeListener mOnItemVisibilityChangeListener;
    private WheelItemTransformer mItemTransformer;
    private WheelSelectionTransformer mSelectionTransformer;
    private WheelAdapter mAdapter;

    public WheelView(Context context) {
        super(context);
        initWheelView();
    }

    public WheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initWheelView();

        //TODO follow this pattern from android source - better performance
        /* final int N = a.getIndexCount();
        for (int i = 0; i < N; i++) {
            int attr = a.getIndex(i);
            switch (attr) {
                case com.android.internal.R.styleable.View_background:
                    background = a.getDrawable(attr);
                    break; */

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WheelView, defStyle, 0);

        Drawable d = a.getDrawable(R.styleable.WheelView_emptyItemDrawable);
        if (d != null) {
            setEmptyItemDrawable(d);
        } else if (a.hasValue(R.styleable.WheelView_emptyItemColor)) {
            int color = a.getColor(R.styleable.WheelView_emptyItemColor, NEVER_USED);
            setEmptyItemColor(color);
        }

        d = a.getDrawable(R.styleable.WheelView_wheelDrawable);
        if (d != null) {
            setWheelDrawable(d);
        } else if (a.hasValue(R.styleable.WheelView_wheelColor)){
            int color = a.getColor(R.styleable.WheelView_wheelColor, NEVER_USED);
            setWheelColor(color);
        }

        d = a.getDrawable(R.styleable.WheelView_selectionDrawable);
        if (d != null) {
            setSelectionDrawable(d);
        } else if (a.hasValue(R.styleable.WheelView_selectionColor)) {
            int color = a.getColor(R.styleable.WheelView_selectionColor, NEVER_USED);
            setSelectionColor(color);
        }

        mSelectionPadding = a.getDimensionPixelSize(R.styleable.WheelView_selectionPadding, 0);
        mIsRepeatable = a.getBoolean(R.styleable.WheelView_repeatItems, false);
        mIsWheelDrawableRotatable = a.getBoolean(R.styleable.WheelView_rotatableWheelDrawable, true);
        mSelectionAngle = a.getFloat(R.styleable.WheelView_selectionAngle, 0f);
        setWheelRadius(a.getLayoutDimension(R.styleable.WheelView_wheelRadius, 0 /* TODO Wrap_content */));
        mOffsetX = a.getDimensionPixelSize(R.styleable.WheelView_wheelOffsetX, 0);
        mOffsetY = a.getDimensionPixelSize(R.styleable.WheelView_wheelOffsetY, 0);
        mWheelToItemDistance = a.getLayoutDimension(R.styleable.WheelView_wheelToItemDistance, ViewGroup.LayoutParams.MATCH_PARENT);

        int itemCount = a.getInteger(R.styleable.WheelView_wheelItemCount, 0);

        //TODO maybe just remove angle padding?
        mItemAnglePadding = a.getFloat(R.styleable.WheelView_wheelItemAnglePadding, 0f); //TODO angle works with the ItemRadius

        if (itemCount != 0) {
            setWheelItemCount(itemCount);
        } else {
            float itemAngle = a.getFloat(R.styleable.WheelView_wheelItemAngle, 0f);
            if (itemAngle != 0f) {
                setWheelItemAngle(itemAngle);
            }
        }

        mItemRadius = a.getDimensionPixelSize(R.styleable.WheelView_wheelItemRadius, 0);

        if (mItemCount == 0 && mWheelToItemDistance > 0) {
            mItemAngle = calculateAngle(mRadius, mWheelToItemDistance) + mItemAnglePadding;
            setWheelItemAngle(mItemAngle);
        }

        String itemTransformerStr = a.getString(R.styleable.WheelView_wheelItemTransformer);
        if (itemTransformerStr != null) {
            mItemTransformer = validateAndInstantiate(itemTransformerStr, WheelItemTransformer.class);
        }

        String selectionTransformerStr = a.getString(R.styleable.WheelView_selectionTransformer);
        if (selectionTransformerStr != null) {
            mSelectionTransformer = validateAndInstantiate(selectionTransformerStr, WheelSelectionTransformer.class);
        }

        mWheelPadding = a.getDimensionPixelSize(R.styleable.WheelView_wheelPadding, 0);

        mWheelPosition = a.getInt(R.styleable.WheelView_wheelPosition, 0);
        if (!a.hasValue(R.styleable.WheelView_selectionAngle)) {
            //TODO use gravity to default the selection angle if not already specified
        }

        a.recycle();
    }

    @SuppressWarnings("unchecked")
    //TODO what is attr.getValue(TypedValue ? - can it replace this mess?
    private <T> T validateAndInstantiate(String clazzName, Class<? extends T> clazz) {
        String errorMessage;
        T instance;
        try {
            Class<?> xmlClazz = Class.forName(clazzName);
            if (clazz.isAssignableFrom(xmlClazz)) {
                try {
                    errorMessage = null;
                    instance = (T) xmlClazz.newInstance();
                } catch (InstantiationException e) {
                    errorMessage = "No argumentless constructor for " + xmlClazz.getSimpleName();
                    instance = null;
                } catch (IllegalAccessException e) {
                    errorMessage = "The argumentless constructor is not public for " + xmlClazz.getSimpleName();
                    instance = null;
                }
            } else {
                errorMessage = "Class inflated from xml (" + xmlClazz.getSimpleName() + ") does not implement " + clazz.getSimpleName();
                instance = null;
            }
        } catch (ClassNotFoundException e) {
            errorMessage = clazzName + " class was not found when inflating from xml";
            instance = null;
        }

        if (errorMessage != null) {
            throw new InflateException(errorMessage);
        } else {
            return instance;
        }
    }

    private boolean hasMask(int value, int mask) {
        return (value & mask) == mask;
    }

    public boolean isPositionLeft() {
        return hasMask(mWheelPosition, LEFT_MASK);
    }

    public boolean isPositionRight() {
        return hasMask(mWheelPosition, RIGHT_MASK);
    }

    public boolean isPositionTop() {
        return hasMask(mWheelPosition, TOP_MASK);
    }

    public boolean isPositionBottom() {
        return hasMask(mWheelPosition, BOTTOM_MASK);
    }

    public void initWheelView() {
        //TODO I only really need to init with default values if there are non defined from attributes...
        mItemTransformer = new ScalingItemTransformer();
        mSelectionTransformer = new FadingSelectionTransformer();
    }

    public static interface OnWheelItemClickListener {
        void onWheelItemClick(WheelView parent, int position, boolean isSelected);
    }

    public void setOnWheelItemClickListener(OnWheelItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public OnWheelItemClickListener getOnWheelItemClickListener() {
        return mOnItemClickListener;
    }

    /**
     * A listener for when a wheel item is selected.
     */
    public static interface OnWheelItemSelectListener {
        void onWheelItemSelected(WheelView parent, int position);
        //TODO onWheelItemSettled?
    }

    public void setOnWheelItemSelectedListener(OnWheelItemSelectListener listener) {
        mOnItemSelectListener = listener;
    }

    public OnWheelItemSelectListener getOnWheelItemSelectListener() {
        return mOnItemSelectListener;
    }

    public static interface OnWheelItemVisibilityChangeListener {
        void onItemVisibilityChange(WheelAdapter adapter, int position, boolean isVisible);
    }

    /* TODO public */ void setOnWheelItemVisibilityChangeListener(OnWheelItemVisibilityChangeListener listener) {
        mOnItemVisibilityChangeListener = listener;
    }

    public OnWheelItemVisibilityChangeListener getOnItemVisibilityChangeListener() {
        return mOnItemVisibilityChangeListener;
    }

    /**
     * A listener for when the wheel angle is changed.
     */
    public static interface OnWheelAngleChangeListener {
        /**
         * Receive a callback when the wheel's angle is changed.
         */
        void onWheelAngleChange(float angle);
    }

    public void setOnWheelAngleChangeListener(OnWheelAngleChangeListener listener) {
        mOnAngleChangeListener = listener;
    }

    public OnWheelAngleChangeListener getOnWheelAngleChangeListener() {
        return mOnAngleChangeListener;
    }

    public void setAdapter(WheelAdapter wheelAdapter) {
        mAdapter = wheelAdapter;
        int count = mAdapter.getCount();
        mItemCacheArray = new CacheItem[count];
        mAdapterItemCount = count;
        invalidate();
    }

    public WheelAdapter getAdapter() {
        return mAdapter;
    }

    public void setWheelItemTransformer(WheelItemTransformer itemTransformer) {
        if (itemTransformer == null) throw new IllegalArgumentException("WheelItemTransformer cannot be null");
        mItemTransformer = itemTransformer;
    }

    public void setWheelSelectionTransformer(WheelSelectionTransformer transformer) {
        mSelectionTransformer = transformer;
    }

    public WheelSelectionTransformer getWheelSelectionTransformer() {
        return mSelectionTransformer;
    }

    /**
     * <p> When true the wheel drawable is rotated as well as the wheel items.
     * For performance it is better to not rotate the wheel drawable.
     * <p> The default value is true
     */
    public void setWheelDrawableRotatable(boolean isWheelDrawableRotatable) {
        mIsWheelDrawableRotatable = isWheelDrawableRotatable;
        invalidate();
    }

    public boolean isWheelDrawableRotatable() {
        return mIsWheelDrawableRotatable;
    }

    /**
     * Repeat Items
     */
    public void setRepeatableWheelItems(boolean isRepeatable) {
        mIsRepeatable = isRepeatable;
    }

    public boolean isRepeatable() {
        return mIsRepeatable;
    }

    public void setWheelItemAngle(float angle) {
        mItemAngle = angle + mItemAnglePadding;
        mItemCount = calculateItemCount(mItemAngle);
        //TODO mItemRadius = calculateWheelItemRadius(mItemAngle);

        if (mWheelBounds != null) {
            invalidate();
        }

        //TODO
    }

    public float getWheelItemAngle() {
        return mItemAngle;
    }

    private float calculateItemAngle(int itemCount) {
        return 360f / itemCount;
    }

    private int calculateItemCount(float angle) {
        return (int) (360f / angle);
    }

    public void setWheelItemAnglePadding(float anglePadding) {
        mItemAnglePadding = anglePadding;

        //TODO
    }

    public float getWheelItemAnglePadding() {
        return mItemAnglePadding;
    }

    public void setSelectionAngle(float angle) {
        mSelectionAngle = Circle.clamp180(angle);

        if (mWheelBounds != null) {
            layoutWheelItems();
        }
    }

    public float getSelectionAngle() {
        return mSelectionAngle;
    }

    public void setSelectionPadding(int padding) {
        mSelectionPadding = padding;
    }

    public int getSelectionPadding() {
        return mSelectionPadding;
    }

    public void setWheelToItemDistance(int distance) {
        mWheelToItemDistance = distance;
    }

    public float getWheelToItemDistance() {
        return mWheelToItemDistance;
    }

    public void setWheelItemRadius(int radius) {
        mItemRadius = radius;
    }

    /* TODO
    public void setWheelItemRadius(float radius, int itemCount) {
        mItemRadius = radius;
        mItemAngle = calculateItemAngle(itemCount);
        mItemCount = itemCount;
    } */

    public float getWheelItemRadius() {
        return mItemRadius;
    }

    public void setWheelRadius(int radius) {
        mRadius = radius;

        if (radius >= 0) mRadiusSquared = radius * radius;
    }

    public float getWheelRadius() {
        return mRadius;
    }

    public void setWheelItemCount(int count) {
        mItemCount = count;
        mItemAngle = calculateItemAngle(count);

        if (mWheelBounds != null) {
            invalidate();
            //TODO ?
        }
    }

    public float getItemCount() {
        return mItemCount;
    }

    public void setWheelOffsetX(int offsetX) {
        mOffsetX = offsetX;
        //TODO
    }

    public float getWheelOffsetX() {
        return mOffsetX;
    }

    public void setWheelOffsetY(int offsetY) {
        mOffsetY = offsetY;
        //TODO
    }

    public float getWheelOffsetY() {
        return mOffsetY;
    }

    public void setWheelPosition(int position) {
        //TODO
    }

    /**
     * Find the largest circle to fit within the item angle.
     * The point of intersection occurs at a tangent to the wheel item.
     */
    private float calculateWheelItemRadius(float angle) {
        return (float) (mWheelToItemDistance * Math.sin(Math.toRadians((double) ((angle - mItemAnglePadding) / 2f))));
    }

    private float calculateAngle(float innerRadius, float outerRadius) {
        return 2f * (float) Math.toDegrees(Math.asin((double) (innerRadius / outerRadius)));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        if (mWidth != width || mHeight != height || mLeft != left || mTop != top) {
            layoutWheel(0, 0, width, height);
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    private void layoutWheel(int left, int top, int width, int height) {
        if (width == 0 || height == 0) return;

        mLeft = left;
        mTop = top;
        mWidth = width;
        mHeight = height;

        mViewBounds.set(left, top, left + width, top + height);
        setWheelBounds(width, height);

        layoutWheelItems();
    }

    private void setWheelBounds(int width, int height) {
        float relativeVertical = 0.5f, relativeHorizontal = 0.5f;
        if (isPositionLeft()) relativeHorizontal -= 0.5f;
        if (isPositionRight()) relativeHorizontal += 0.5f;
        if (isPositionTop()) relativeVertical -= 0.5f;
        if (isPositionBottom()) relativeVertical += 0.5f;

        final int centerX = (int) (mOffsetX + width * relativeHorizontal);
        final int centerY = (int) (mOffsetY + height * relativeVertical);
        mWheelBounds = new Circle(centerX, centerY, mRadius);

        if (mWheelDrawable != null) {
            mWheelDrawable.setBounds(mWheelBounds.getBoundingRect());
        }
    }

    private void layoutWheelItems() {
        mItemStates = new ArrayList<ItemState>(mItemCount);
        for (int i = 0; i < mItemCount; i++) {
            mItemStates.add(new ItemState());
        }

        if (mWheelItemBounds == null) {
            mWheelItemBounds = new ArrayList<Circle>(mItemCount);
        } else if (!mWheelItemBounds.isEmpty()) {
            mWheelItemBounds.clear();
        }

        if (mWheelToItemDistance == ViewGroup.LayoutParams.MATCH_PARENT) {
            mWheelToItemDistance = (int) (mWheelBounds.mRadius - mItemRadius - mWheelPadding);
        }

        float itemAngleRadians = (float) Math.toRadians(mItemAngle);
        float offsetRadians = (float) Math.toRadians(-mSelectionAngle);
        for (int i = 0; i < mItemCount; i++) {
            float angle = itemAngleRadians * i + offsetRadians;
            float x = mWheelBounds.mCenterX + mWheelToItemDistance * (float) Math.cos(angle);
            float y = mWheelBounds.mCenterY + mWheelToItemDistance * (float) Math.sin(angle);
            mWheelItemBounds.add(new Circle(x, y, mItemRadius));
        }

        invalidate();
    }

    /**
     * You should set the wheel drawable not to rotate for a performance benefit.
     * See the method {@link #setWheelDrawableRotatable(boolean)}
     */
    public void setWheelColor(int color) {
        setWheelDrawable(createOvalDrawable(color));
    }

    /**
     * If the drawable has infinite lines of symmetry then you should set the wheel drawable to
     * not rotate, see {@link #setWheelDrawableRotatable(boolean)}. In other words, if the drawable
     * doesn't look any different whilst it is rotating, you should improve the performance by
     * disabling the drawable from rotating.
     */
    public void setWheelDrawable(int resId) {
        setWheelDrawable(getResources().getDrawable(resId));
    }

    public void setWheelDrawable(Drawable drawable) {
        mWheelDrawable = drawable;

        if (mWheelBounds != null) {
            mWheelDrawable.setBounds(mWheelBounds.getBoundingRect());
            invalidate();
        }
    }

    public void setEmptyItemColor(int color) {
        setEmptyItemDrawable(createOvalDrawable(color));
    }

    public void setEmptyItemDrawable(int resId) {
        setEmptyItemDrawable(getResources().getDrawable(resId));
    }

    public void setEmptyItemDrawable(Drawable drawable) {
        mEmptyItemDrawable = drawable;

        if (mWheelBounds != null) {
            invalidate();
        }
    }

    public void setSelectionColor(int color) {
        setSelectionDrawable(createOvalDrawable(color));
    }

    public void setSelectionDrawable(int resId) {
        setSelectionDrawable(getResources().getDrawable(resId));
    }

    public void setSelectionDrawable(Drawable drawable) {
        mSelectionDrawable = drawable;
        invalidate();
    }

    public Drawable getSelectionDrawable() {
        return mSelectionDrawable;
    }

    public Drawable getEmptyItemDrawable() {
        return mEmptyItemDrawable;
    }

    public Drawable getWheelDrawable() {
        return mWheelDrawable;
    }

    public float getAngleForPosition(int position) {
        return -1f * position * mItemAngle;
    }

    public void setPosition(int position) {
        setAngle(getAngleForPosition(position));
    }

    public int getPosition() {
        return mSelectedPosition;
    }

    public void setAngle(float angle) {
        mAngle = angle;

        updateSelectionPosition();

        if (mOnAngleChangeListener != null) {
            mOnAngleChangeListener.onWheelAngleChange(mAngle);
        }

        invalidate();
    }

    private void updateSelectionPosition() {
        int selectedPosition = (int) ((-mAngle + -0.5*Math.signum(mAngle)*mItemAngle) / mItemAngle);

        int currentSelectedPosition = getSelectedPosition();
        if (selectedPosition != currentSelectedPosition) {
            setSelectedPosition(selectedPosition);
        }
    }

    private void setSelectedPosition(int position) {
        if (mSelectedPosition == position) return;
        mSelectedPosition = position;

        if (mOnItemSelectListener != null) {
            mOnItemSelectListener.onWheelItemSelected(this, getSelectedPosition());
        }
    }

    public int getSelectedPosition() {
        return rawPositionToAdapterPosition(mSelectedPosition);
    }

    public float getAngle() {
        return mAngle;
    }

    private void addAngle(float degrees) {
        setAngle(mAngle + degrees);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        if (!mWheelBounds.contains(x, y)) {
            if (mIsDraggingWheel) {
                flingWheel();
            }
            return true;
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (!mIsDraggingWheel) {
                    startWheelDrag(event, x, y);
                }

                mClickedItem = getClickedItem(x, y);
                break;
            case MotionEvent.ACTION_UP:
                if (mOnItemClickListener != null && mClickedItem != null
                        && mClickedItem == getClickedItem(x, y)
                        && mDraggedAngle < CLICK_MAX_DRAGGED_ANGLE) {
                    boolean isSelected = Math.abs(mClickedItem.mRelativePos) < 1f;
                    mOnItemClickListener.onWheelItemClick(this,
                            mClickedItem.mAdapterPosition, isSelected);
                }
            case MotionEvent.ACTION_CANCEL:
                if (mIsDraggingWheel) {
                    flingWheel();
                }

                mVelocityTracker.recycle();
                mVelocityTracker = null;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mIsDraggingWheel) {
                    startWheelDrag(event, x, y);
                    return true;
                }

                mVelocityTracker.addMovement(event);
                mLastWheelTouchX = x;
                mLastWheelTouchY = y;
                setRadiusVector(x, y);

                float touchRadiusSquared = mRadiusVector.x * mRadiusVector.x + mRadiusVector.y * mRadiusVector.y;
                float touchFactor = TOUCH_FACTORS[(int) (touchRadiusSquared / mRadiusSquared * TOUCH_FACTORS.length)];
                float touchAngle = mWheelBounds.angleToDegrees(x, y);
                float draggedAngle = -1f * Circle.shortestAngle(touchAngle, mLastTouchAngle) * touchFactor;
                addAngle(draggedAngle);
                mLastTouchAngle = touchAngle;
                mDraggedAngle += draggedAngle;

                if (mRequiresUpdate) {
                    mRequiresUpdate = false;
                }
                break;
        }
        return true;
    }

    private ItemState getClickedItem(float touchX, float touchY) {
        for (ItemState state : mItemStates) {
            Circle itemBounds = state.mBounds;
            if (itemBounds.contains(touchX, touchY)) return state;
        }
        return null;
    }

    private void startWheelDrag(MotionEvent event, float x, float y) {
        mIsDraggingWheel = true;
        mDraggedAngle = 0f;

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
        mVelocityTracker.addMovement(event);

        mAngularVelocity = 0f;
        mLastTouchAngle = mWheelBounds.angleToDegrees(x, y);
    }

    private void flingWheel() {
        mIsDraggingWheel = false;

        mVelocityTracker.computeCurrentVelocity(1);

        //torque = r X F
        mForceVector.set(mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
        setRadiusVector(mLastWheelTouchX, mLastWheelTouchY);
        float torque = mForceVector.crossProduct(mRadiusVector);

        //dw/dt = torque / I = torque / mr^2
        float angularAccel = torque / mRadiusSquared;

        //estimate an angular velocity based on the strength of the angular acceleration
        float angularVel = angularAccel * ANGULAR_VEL_COEFFICIENT;

        //clamp the angular velocity
        if (angularVel > MAX_ANGULAR_VEL) angularVel = MAX_ANGULAR_VEL;
        else if (angularVel < -MAX_ANGULAR_VEL) angularVel = -MAX_ANGULAR_VEL;
        mAngularVelocity = angularVel;

        mLastUpdateTime = SystemClock.uptimeMillis();
        mRequiresUpdate = true;

        invalidate();
    }

    private void setRadiusVector(float x, float y) {
        float rVectorX = mWheelBounds.mCenterX - x;
        float rVectorY = mWheelBounds.mCenterY - y;
        mRadiusVector.set(rVectorX, rVectorY);
    }

    public int rawPositionToAdapterPosition(int position) {
        return mIsRepeatable ? Circle.clamp(position, mAdapterItemCount) : position;
    }

    public int rawPositionToWheelPosition(int position) {
        return rawPositionToWheelPosition(position, rawPositionToAdapterPosition(position));
    }

    public int rawPositionToWheelPosition(int position, int adapterPosition) {
        int circularOffset = mIsRepeatable ? ((int) Math.floor((position /
                (float) mAdapterItemCount)) * (mAdapterItemCount - mItemCount)) : 0;
        return Circle.clamp(adapterPosition + circularOffset, mItemCount);
    }

    /**
     * Estimates the wheel's new angle and angular velocity
     */
    private void update(float deltaTime) {
        float vel = mAngularVelocity;
        float velSqr = vel*vel;
        if (vel > 0f) {
            mAngularVelocity -= velSqr * VELOCITY_FRICTION_COEFFICIENT + CONSTANT_FRICTION_COEFFICIENT;
            if (mAngularVelocity < 0f) mAngularVelocity = 0f;
        } else if (vel < 0f) {
            mAngularVelocity -= velSqr * -VELOCITY_FRICTION_COEFFICIENT - CONSTANT_FRICTION_COEFFICIENT;
            if (mAngularVelocity > 0f) mAngularVelocity = 0f;
        }

        if (mAngularVelocity != 0f) {
            addAngle(mAngularVelocity * deltaTime);
        } else {
            mRequiresUpdate = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRequiresUpdate) {
            long currentTime = SystemClock.uptimeMillis();
            long timeDiff = currentTime - mLastUpdateTime;
            mLastUpdateTime = currentTime;
            update(timeDiff);
        }

        float angle = mAngle;
        if (mWheelDrawable != null) {
            if (mIsWheelDrawableRotatable) {
                canvas.save();
                canvas.rotate(angle, mWheelBounds.mCenterX, mWheelBounds.mCenterY);
                mWheelDrawable.draw(canvas);
                canvas.restore();
            } else {
                mWheelDrawable.draw(canvas);
            }
        }

        int adapterItemCount = mAdapterItemCount;
        if (mAdapter == null || adapterItemCount <= 0) return;

        double angleInRadians = Math.toRadians(angle);
        double cosAngle = Math.cos(angleInRadians);
        double sinAngle = Math.sin(angleInRadians);
        float centerX = mWheelBounds.mCenterX;
        float centerY = mWheelBounds.mCenterY;

        int wheelItemOffset = mItemCount / 2;
        int offset = mSelectedPosition - wheelItemOffset;
        int length = mItemCount + offset;
        for (int i = offset; i < length; i++) {
            int adapterPosition = rawPositionToAdapterPosition(i);
            int wheelItemPosition = rawPositionToWheelPosition(i, adapterPosition);

            Circle itemBounds = mWheelItemBounds.get(wheelItemPosition);
            float radius = itemBounds.mRadius;

            //translate before rotating so that origin is at the wheel's center
            float x = itemBounds.mCenterX - centerX;
            float y = itemBounds.mCenterY - centerY;

            //rotate
            float x1 = (float) (x * cosAngle - y * sinAngle);
            float y1 = (float) (x * sinAngle + y * cosAngle);

            //translate back after rotation
            x1 += centerX;
            y1 += centerY;

            ItemState itemState = mItemStates.get(wheelItemPosition);
            updateItemState(itemState, adapterPosition, x1, y1, radius);
            mItemTransformer.transform(itemState, sTempRect);

            CacheItem cacheItem = mItemCacheArray[adapterPosition];
            if (cacheItem == null) {
                cacheItem = new CacheItem();
                mItemCacheArray[adapterPosition] = cacheItem;
            }

            //don't draw if outside of the view bounds
            if (Rect.intersects(sTempRect, mViewBounds)) {
                if (cacheItem.mDirty) {
                    cacheItem.mDrawable = mAdapter.getDrawable(adapterPosition);
                    cacheItem.mDirty = false;
                }

                if (!cacheItem.mIsVisible) {
                    cacheItem.mIsVisible = true;
                    if (mOnItemVisibilityChangeListener != null) {
                        mOnItemVisibilityChangeListener.onItemVisibilityChange(mAdapter, adapterPosition, true);
                    }
                }

                if (i == mSelectedPosition && mSelectionDrawable != null) {
                    mSelectionDrawable.setBounds(sTempRect.left - mSelectionPadding, sTempRect.top - mSelectionPadding,
                            sTempRect.right + mSelectionPadding, sTempRect.bottom + mSelectionPadding);
                    mSelectionTransformer.transform(mSelectionDrawable, itemState);
                    mSelectionDrawable.draw(canvas);
                }

                Drawable drawable;
                if (cacheItem.mDrawable != null) {
                    drawable = cacheItem.mDrawable;
                } else {
                    if (mEmptyItemDrawable != null) {
                        drawable = mEmptyItemDrawable;
                    } else {
                        drawable = null;
                    }
                }

                if (drawable != null) {
                    drawable.setBounds(sTempRect);
                    drawable.draw(canvas);
                }
            } else {
                if (cacheItem.mIsVisible) {
                    cacheItem.mIsVisible = false;
                    if (mOnItemVisibilityChangeListener != null) {
                        mOnItemVisibilityChangeListener.onItemVisibilityChange(mAdapter, adapterPosition, false);
                    }
                }
            }
        }
    }

    private void updateItemState(ItemState itemState, int adapterPosition,
                                 float x, float y, float radius) {
        float itemAngle = mWheelBounds.angleToDegrees(x, y);
        float angleFromSelection = Circle.shortestAngle(itemAngle, mSelectionAngle);
        float relativePos = angleFromSelection / mItemAngle * 2f;

        itemState.mAngleFromSelection = angleFromSelection;
        itemState.mRelativePos = relativePos;
        itemState.mBounds.mCenterX = x;
        itemState.mBounds.mCenterY = y;
        itemState.mAdapterPosition = adapterPosition;

        //TODO The radius is always known - doesn't really need this?
        itemState.mBounds.mRadius = radius;
    }

    private Drawable createOvalDrawable(int color) {
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.getPaint().setColor(color);
        return shapeDrawable;
    }

    /**
     * The ItemState is used to provide extra information when transforming the selection drawable
     * or item bounds. It is also used to
     */
    public static class ItemState {
        WheelView mWheelView;
        Circle mBounds;
        float mAngleFromSelection;
        float mRelativePos;
        int mAdapterPosition; //TODO

        private ItemState() {
            mBounds = new Circle();
        }

        public WheelView getWheelView() {
            return mWheelView;
        }

        public float getAngleFromSelection() {
            return mAngleFromSelection;
        }

        public Circle getBounds() {
            return mBounds;
        }

        public float getRelativePosition() {
            return mRelativePos;
        }
    }

    static class CacheItem {
        boolean mDirty;
        boolean mIsVisible;
        Drawable mDrawable;

        CacheItem() {
            mDirty = true;
        }
    }

    /**
     * A simple class to represent a vector with an add and cross product method. Used only to
     * calculate the Wheel's angular velocity in {@link #flingWheel()}
     */
    static class Vector {
        float x, y;

        Vector() {}

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
}
