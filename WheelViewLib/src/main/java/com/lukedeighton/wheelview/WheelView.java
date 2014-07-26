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
import android.util.AttributeSet;
import android.view.InflateException;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

//TODO onWheelItemSelected callback for when the wheel has settled (0 angular velocity), and one when it is passed
//TODO onWheelItemClickListener
//TODO possible Animation option when setPosition?
//TODO empty - physics to spring away - prevent movement out from edge
//TODO circular clip option?
//TODO Saving State during screen rotate etc. SavedState extends BaseSavedState
//TODO handle measurement of view!
//TODO can items be rendered as views or use recyclerView?
//TODO onWheelItemVisibilityChange needs to factor in when items are cycled within view bounds and should that have another callback?
//TODO option to get wheel state (either flinging or dragging)
//TODO item radius works separately ? uses min angle etc. to figure out in the layout event

public class WheelView extends View {

    private static final Rect sTempRect = new Rect();

	private static final float VELOCITY_FRICTION_COEFFICIENT = 0.02f;
	private static final float CONSTANT_FRICTION_COEFFICIENT = 0.0022f;
	private static final float ANGULAR_VEL_COEFFICIENT = 29f;
    private static final float MAX_ANGULAR_VEL = 0.3f;
	private static final int SMOOTH_AVG_COUNT = 3;

    private static final int RGB_MASK = 0x00FFFFFF;

    //The following code is used to avoid sqrt operations during touch drag events
	private static final float TOUCH_DRAG_COEFFICIENT = 0.8f;
	private static final float[] TOUCH_FACTORS;
	static {
		int size = 20;
		TOUCH_FACTORS = new float[size];
		int maxIndex = size - 1;
		float numerator = size*size;
		for(int i = 0; i < size; i++) {
			int factor = maxIndex - i + 1;
			TOUCH_FACTORS[i] = (1 - factor * factor / numerator) * TOUCH_DRAG_COEFFICIENT; 
		}
	}

    private Vector mForceVector = new Vector(); //TODO do i need this reference?
    private AveragingFifoFloat mAvgAngularAccel = new AveragingFifoFloat(SMOOTH_AVG_COUNT);
    private float mAngle;
    private float mAngularVelocity;
    private long mLastUpdateTime;
    private boolean mRequiresUpdate;
    private int mSelectedPosition;

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
    private float mWheelToItemDistance;
    private float mItemRadius;
    private float mRadius;
    private float mOffsetX;
    private float mOffsetY;
    private int mItemCount;

	private int mLeft, mTop, mWidth, mHeight;
	private Rect mViewBounds = new Rect();
    private Circle mWheelBounds;

    /**
     * Wheel item bounds are always pre-rotation and based on the {@link #mSelectionAngle}
     */
    private List<Circle> mWheelItemBounds;
    private int mAdapterItemCount;

	private boolean mIsDraggingWheel;
	private float mLastTouchAngle;
	private float mLastTouchX, mLastTouchY;
    private long mLastTouchTime;

	private OnAngleChangeListener mOnAngleChangeListener;
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

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WheelView, defStyle, 0);

        Drawable d = a.getDrawable(R.styleable.WheelView_emptyItemDrawable);
        if (d != null) {
            setEmptyItemDrawable(d);
        } else {
            int color = a.getColor(R.styleable.WheelView_emptyItemColor, RGB_MASK);
            if (hasAlphaComponent(color)) {
                setEmptyItemColor(color);
            }
        }

        d = a.getDrawable(R.styleable.WheelView_wheelDrawable);
        if (d != null) {
            setWheelDrawable(d);
        } else {
            int color = a.getColor(R.styleable.WheelView_wheelColor, RGB_MASK);
            if (hasAlphaComponent(color)) {
                setWheelColor(color);
            }
        }

        d = a.getDrawable(R.styleable.WheelView_selectionDrawable);
        if (d != null) {
            setSelectionDrawable(d);
        } else {
            int color = a.getColor(R.styleable.WheelView_selectionColor, RGB_MASK);
            if (hasAlphaComponent(color)) {
                setSelectionColor(color);
            }
        }

        //TODO default values
        //TODO fix angle-padding with selection etc.

        mSelectionPadding = (int) a.getDimension(R.styleable.WheelView_selectionPadding, 0f);
        mIsRepeatable = a.getBoolean(R.styleable.WheelView_repeatItems, false);
        mIsWheelDrawableRotatable = a.getBoolean(R.styleable.WheelView_rotatableWheelDrawable, true);
        mSelectionAngle = a.getFloat(R.styleable.WheelView_selectionAngle, 0f);
        mRadius = a.getDimension(R.styleable.WheelView_wheelRadius, 0f);
        mOffsetX = a.getDimension(R.styleable.WheelView_wheelOffsetX, 0f);
        mOffsetY = a.getDimension(R.styleable.WheelView_wheelOffsetY, 0f);
        mWheelToItemDistance = a.getDimension(R.styleable.WheelView_wheelToItemDistance, -1f);

        int itemCount = a.getInteger(R.styleable.WheelView_wheelItemCount, 0);
        mItemAnglePadding = a.getFloat(R.styleable.WheelView_wheelItemAnglePadding, 0f); //TODO angle works with the ItemRadius

        if(itemCount != 0) {
            setWheelItemCount(itemCount);
        } else {
            float itemAngle = a.getFloat(R.styleable.WheelView_wheelItemAngle, 0f);
            if(itemAngle != 0f) {
                setWheelItemAngle(itemAngle);
            }
        }

        mItemRadius = a.getDimension(R.styleable.WheelView_wheelItemRadius, 0f);

        if(mItemCount == 0) {
            mItemAngle = calculateAngle(mRadius, mWheelToItemDistance) + mItemAnglePadding;
            setWheelItemAngle(mItemAngle);
        }

        String itemTransformerStr = a.getString(R.styleable.WheelView_wheelItemTransformer);
        if(itemTransformerStr != null) {
            mItemTransformer = validateAndInstantiate(itemTransformerStr, WheelItemTransformer.class);
        }

        String selectionTransformerStr = a.getString(R.styleable.WheelView_selectionTransformer);
        if(selectionTransformerStr != null) {
            mSelectionTransformer = validateAndInstantiate(selectionTransformerStr, WheelSelectionTransformer.class);
        }

        a.recycle();
    }

    private boolean hasAlphaComponent(int color) {
        return (color >>> 24) > 0;
    }

    @SuppressWarnings("unchecked")
    private <T> T validateAndInstantiate(String clazzName, Class<? extends T> clazz) {
        String errorMessage;
        T instance;
        try {
            Class<?> xmlClazz = Class.forName(clazzName);
            if(clazz.isAssignableFrom(xmlClazz)) {
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

        if(errorMessage != null) {
            throw new InflateException(errorMessage);
        } else {
            return instance;
        }
    }

    public void initWheelView() {
        //TODO I only really need to init with default values if there are non from attributes...
        mItemTransformer = new ScalableTransformer();
        mSelectionTransformer = new FadingSelectionTransformer(this);
    }

    /**
     * A listener for when a wheel item is selected.
     */
    public static interface OnWheelItemSelectListener {
        void onWheelItemSelected(WheelAdapter wheelItem, int position);
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
    public static interface OnAngleChangeListener {
        /**
         * Receive a callback when the wheel's angle is changed.
         */
        void onAngleChange(float angle);
    }

    public void setOnAngleChangeListener(OnAngleChangeListener listener) {
        mOnAngleChangeListener = listener;
    }

    public OnAngleChangeListener getOnAngleChangeListener() {
        return mOnAngleChangeListener;
    }

    public interface WheelAdapter {
        Drawable getDrawable(int position);
        int getCount();
    }

    public void setAdapter(WheelAdapter wheelAdapter) {
        mAdapter = wheelAdapter;
        int count = mAdapter.getCount();
        mItemCacheArray = new CacheItem[count];
        mAdapterItemCount = count;
        invalidate();
    }

    public WheelAdapter getWheelAdapter() {
        return mAdapter;
    }

    static class CacheItem {
        boolean mDirty;
        boolean mIsVisible;
        Drawable mDrawable;

        CacheItem() {
            mDirty = true;
        }
    }

    public static interface WheelItemTransformer {
        void transform(Rect itemBounds, float itemAngle, float x, float y, float radius);
    }

    public void setWheelItemTransformer(WheelItemTransformer itemTransformer) {
        if(itemTransformer == null) throw new IllegalArgumentException("WheelItemTransformer cannot be null");
        mItemTransformer = itemTransformer;
    }

    public static class SimpleTransformer implements WheelItemTransformer {
        @Override
        public void transform(Rect itemBounds, float itemAngle, float x, float y, float radius) {
            sTempRect.set(Math.round(x - radius), Math.round(y - radius), Math.round(x + radius), Math.round(y + radius));
        }
    }

    public static class ScalableTransformer implements WheelItemTransformer {
        //TODO scale should be based on the item angle
        //TODO bad since cant expose sTempRect, need to set bounds or expose bounds w/o object allocation?
        @Override
        public void transform(Rect itemBounds, float angleFromSelection, float x, float y, float radius) {
            float scale = angleFromSelection * 0.014f;
            scale = Math.min(1.12f, 1.15f - Math.min(0.25f, Math.abs(scale)));
            radius *= scale;
            sTempRect.set(Math.round(x - radius), Math.round(y - radius), Math.round(x + radius), Math.round(y + radius));
        }
    }

    public static interface WheelSelectionTransformer {
        void transform(Drawable drawable, float angleFromSelection);
    }

    public static class FadingSelectionTransformer implements WheelSelectionTransformer {

        private WheelView mWheelView;

        public FadingSelectionTransformer(WheelView wheelView) {
            mWheelView = wheelView;
        }

        @Override
        public void transform(Drawable drawable, float angleFromSelection) {
            int alpha = (int)(Math.abs(angleFromSelection) / mWheelView.mItemAngle * 255f * 2f) - 80;
            if(alpha > 255) alpha = 255;
            else if(alpha < 0) alpha = 0;
            drawable.setAlpha(255 - alpha);
        }
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

        if(mWheelBounds != null) {
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

        if(mWheelBounds != null) {
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

    public void setWheelToItemDistance(float distance) {
        mWheelToItemDistance = distance;
    }

    public float getWheelToItemDistance() {
        return mWheelToItemDistance;
    }

    public void setWheelItemRadius(float radius) {
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

    public void setWheelRadius(float radius) {
        mRadius = radius;
    }

    public float getWheelRadius() {
        return mRadius;
    }

    public void setWheelItemCount(int count) {
        mItemCount = count;
        mItemAngle = calculateItemAngle(count);

        if(mWheelBounds != null) {
            invalidate();
            //TODO ?
        }
    }

    public float getItemCount() {
        return mItemCount;
    }

    public void setWheelOffsetX(float offsetX) {
        mOffsetX = offsetX;
        //TODO
    }

    public float getWheelOffsetX() {
        return mOffsetX;
    }

    public void setWheelOffsetY(float offsetY) {
        mOffsetY = offsetY;
        //TODO
    }

    public float getWheelOffsetY() {
        return mOffsetY;
    }

    /**
     * Find the largest circle to fit within the item angle.
     * The point of intersection occurs at a tangent to the wheel item.
     */
    private float calculateWheelItemRadius(float angle) {
        return (float) (mWheelToItemDistance * Math.sin(Math.toRadians((double) ((angle - mItemAnglePadding) / 2f))));
    }

    private float calculateAngle(float innerRadius, float outerRadius) {
        return 2f * (float) Math.toDegrees(Math.asin((double) (innerRadius/outerRadius)));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        if(mWidth != width || mHeight != height || mLeft != left || mTop != top) {
            layoutWheel(0, 0, width, height);
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    private void layoutWheel(int left, int top, int width, int height) {
		if(width == 0 || height == 0) return;
		
		mLeft = left;
		mTop = top;
		mWidth = width;
		mHeight = height;
		
		mViewBounds.set(left, top, left + width, top + height);
        setWheelBounds(width, height);

        layoutWheelItems();
    }

    private void setWheelBounds(int width, int height) {
        //TODO actually use gravity here to figure it out
        final float centerX = mOffsetX + width*0.5f;
        final float centerY = mOffsetY + height*0.5f;
        mWheelBounds = new Circle(centerX, centerY, mRadius);

        if(mWheelDrawable != null) {
            mWheelDrawable.setBounds(mWheelBounds.getBoundingRect());
        }
    }

    private void layoutWheelItems() {
        if(mWheelItemBounds == null) {
            mWheelItemBounds = new ArrayList<Circle>(mItemCount);
        } else if(!mWheelItemBounds.isEmpty()) {
            mWheelItemBounds.clear();
        }

        float itemAngleRadians = (float) Math.toRadians(mItemAngle);
        float offsetRadians = (float) Math.toRadians(-mSelectionAngle);
        for(int i = 0; i < mItemCount; i++) {
            float angle = itemAngleRadians * i + offsetRadians;
            float x = mWheelBounds.mCenterX + mWheelToItemDistance * (float) Math.cos(angle);
            float y = mWheelBounds.mCenterY + mWheelToItemDistance * (float) Math.sin(angle);
            mWheelItemBounds.add(new Circle(x, y, mItemRadius));
        }

        invalidate();
    }

    public void setWheelColor(int color) {
        setWheelDrawable(createOvalDrawable(color));
    }

    public void setWheelDrawable(int resId) {
        setWheelDrawable(getResources().getDrawable(resId));
    }

    public void setWheelDrawable(Drawable drawable) {
        mWheelDrawable = drawable;

        if(mWheelBounds != null) {
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

        if(mWheelBounds != null) {
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

	public void setAngle(float angle) {
        mAngle = angle;

        updateSelectionPosition();

		if(mOnAngleChangeListener != null) {
			mOnAngleChangeListener.onAngleChange(mAngle);
		}

        invalidate();
	}

    private void updateSelectionPosition() {
        int selectedPosition = (int) ((-mAngle + -0.5*Math.signum(mAngle)*mItemAngle)/mItemAngle);

        int currentSelectedPosition = getSelectedPosition();
        if(selectedPosition != currentSelectedPosition) {
            setSelectedPosition(selectedPosition);
        }
    }

    private void setSelectedPosition(int position) {
        if (mSelectedPosition == position) return;
        mSelectedPosition = position;

        if (mOnItemSelectListener != null) {
            mOnItemSelectListener.onWheelItemSelected(mAdapter, getSelectedPosition());
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
	public boolean onTouchEvent(MotionEvent event) {
		final float x = event.getX();
		final float y = event.getY();

		if(!mWheelBounds.contains(x, y)) {
            if(mIsDraggingWheel) {
                mIsDraggingWheel = false;
                flingWheel();
            }
			return true;
		}

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
        	case MotionEvent.ACTION_DOWN:
                startWheelDrag(x, y);
                mIsDraggingWheel = true;
        		break;
        	case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(mIsDraggingWheel) {
                    mIsDraggingWheel = false;
                    flingWheel();
                }
                break;
        	case MotionEvent.ACTION_MOVE:
                if(!mIsDraggingWheel) {
                    mIsDraggingWheel = true;
                    startWheelDrag(x, y);
                    return true;
                }

                //option to use w = dTheta/dt instead of using the torque approach
                //could possibly use a velocity tracker here, not sure about the performance though
        		float dx = x - mLastTouchX;
        		float dy = y - mLastTouchY;

                long currentTime = SystemClock.uptimeMillis();
                long timeDiff = currentTime - mLastTouchTime;
                mLastTouchTime = currentTime;

                //avoid NaN's that may happen when divide by 0
                if(timeDiff < 1) timeDiff = 1;

                float velX = dx / timeDiff;
                float velY = dy / timeDiff;

                //approximate the force as the velocity vector between last and current touch points
        		mForceVector.set(velX, velY);
        		mLastTouchX = x;
        		mLastTouchY = y;

        		float touchVectorX = mWheelBounds.mCenterX - x;
        		float touchVectorY = mWheelBounds.mCenterY - y;

                //torque = r X F
                float torque = mForceVector.crossProduct(touchVectorX, touchVectorY);

        		float wheelRadius = mWheelBounds.mRadius;
        		float wheelRadiusSquared = wheelRadius * wheelRadius;

        		//dw/dt = torque / I = torque / mr^2
        		mAvgAngularAccel.add(torque / wheelRadiusSquared);

                float touchRadiusSquared = touchVectorX*touchVectorX + touchVectorY*touchVectorY;
                float touchFactor = TOUCH_FACTORS[(int) (touchRadiusSquared / wheelRadiusSquared * TOUCH_FACTORS.length)];
                float touchAngle = mWheelBounds.angleToDegrees(x, y);
                addAngle(-1f * Circle.shortestAngle(touchAngle, mLastTouchAngle) * touchFactor);
        		mLastTouchAngle = touchAngle;

                if(mRequiresUpdate) {
                    mRequiresUpdate = false;
                }
        		break;
		}
		return true;
	}

    private void startWheelDrag(float x, float y) {
        mAngularVelocity = 0f;
        mLastTouchAngle = mWheelBounds.angleToDegrees(x, y);
        mLastTouchX = x;
        mLastTouchY = y;
        mLastTouchTime = SystemClock.uptimeMillis();
    }

	private void flingWheel() {
        float angularAccel = mAvgAngularAccel.mAverage;

        //estimate an angular velocity based on the strength of the angular acceleration
        float angularVel = angularAccel * ANGULAR_VEL_COEFFICIENT;

        //clamp the angular velocity
        if(angularVel > MAX_ANGULAR_VEL) angularVel = MAX_ANGULAR_VEL;
        else if(angularVel < -MAX_ANGULAR_VEL) angularVel = -MAX_ANGULAR_VEL;

		mAngularVelocity = angularVel;

		mAvgAngularAccel.reset();
        mLastUpdateTime = SystemClock.uptimeMillis();
        mRequiresUpdate = true;

        invalidate();
    }

    public int rawPositionToAdapterPosition(int position) {
        return mIsRepeatable ? Circle.clamp(position, mAdapterItemCount) : position;
    }

    public int rawPositionToWheelPosition(int position) {
        return rawPositionToWheelPosition(position, rawPositionToAdapterPosition(position));
    }

    public int rawPositionToWheelPosition(int position, int adapterPosition) {
        int circularOffset = mIsRepeatable ? ((int) Math.floor((position / (float) mAdapterItemCount)) * (mAdapterItemCount - mItemCount)) : 0;
        return Circle.clamp(adapterPosition + circularOffset, mItemCount);
    }

    public int getRawPosition() {
        return mSelectedPosition;
    }

    /**
     * Estimates the wheel's new angle and angular velocity
     */
    private void update(float deltaTime) {
        float vel = mAngularVelocity;
        float velSqr = vel*vel;
        if(vel > 0f) {
            mAngularVelocity -= velSqr*VELOCITY_FRICTION_COEFFICIENT + CONSTANT_FRICTION_COEFFICIENT;
            if(mAngularVelocity < 0f) mAngularVelocity = 0f;
        } else if(vel < 0f) {
            mAngularVelocity -= velSqr*-VELOCITY_FRICTION_COEFFICIENT - CONSTANT_FRICTION_COEFFICIENT;
            if(mAngularVelocity > 0f) mAngularVelocity = 0f;
        }

        if(mAngularVelocity != 0f) {
            addAngle(mAngularVelocity * deltaTime);
        } else {
            mRequiresUpdate = false;
        }
    }

    @Override
	protected void onDraw(Canvas canvas) {
        if(mRequiresUpdate) {
            long currentTime = SystemClock.uptimeMillis();
            long timeDiff = currentTime - mLastUpdateTime;
            mLastUpdateTime = currentTime;
            update(timeDiff);
        }

		float angle = mAngle;
		if(mWheelDrawable != null) {
			if(mIsWheelDrawableRotatable) {
				canvas.save();
				canvas.rotate(angle, mWheelBounds.mCenterX, mWheelBounds.mCenterY);
				mWheelDrawable.draw(canvas);
				canvas.restore();
			} else {
				mWheelDrawable.draw(canvas);
			}
		}

        int adapterItemCount = mAdapterItemCount;
		if(mAdapter != null && adapterItemCount > 0) {
			double angleInRadians = Math.toRadians(angle);
            double cosAngle = Math.cos(angleInRadians);
            double sinAngle = Math.sin(angleInRadians);

            int wheelItemOffset = mItemCount / 2;
            int offset = mSelectedPosition - wheelItemOffset;
            int length = mItemCount + offset;
			for(int i = offset; i < length ; i++) {
                int adapterPosition = rawPositionToAdapterPosition(i);
                int wheelItemPosition = rawPositionToWheelPosition(i, adapterPosition);

                Circle itemBounds = mWheelItemBounds.get(wheelItemPosition);
				float radius = itemBounds.mRadius;
                float centerX = mWheelBounds.mCenterX;
                float centerY = mWheelBounds.mCenterY;

                //translate before rotating so that origin is at the wheel's center
                float x = itemBounds.mCenterX - centerX;
				float y = itemBounds.mCenterY - centerY;

				//rotate
				float x1 = (float) (x * cosAngle - y * sinAngle);
				float y1 = (float) (x * sinAngle + y * cosAngle);
				
				//translate back after rotation
				x1 += centerX;
				y1 += centerY;

                //Rect bounds, x, y, radius, angle
                float angleFromSelection = Circle.shortestAngle(mWheelBounds.angleToDegrees(x1, y1), mSelectionAngle);
                mItemTransformer.transform(sTempRect, angleFromSelection, x1, y1, radius);

                CacheItem cacheItem = mItemCacheArray[adapterPosition];
                if(cacheItem == null) {
                    cacheItem = new CacheItem();
                    mItemCacheArray[adapterPosition] = cacheItem;
                }

                //don't draw if outside of the view bounds
                if(Rect.intersects(sTempRect, mViewBounds)) {
                    if(cacheItem.mDirty) {
                        cacheItem.mDrawable = mAdapter.getDrawable(adapterPosition);
                        cacheItem.mDirty = false;
                    }

                    if(!cacheItem.mIsVisible) {
                        cacheItem.mIsVisible = true;
                        if(mOnItemVisibilityChangeListener != null) {
                            mOnItemVisibilityChangeListener.onItemVisibilityChange(mAdapter, adapterPosition, true);
                        }
                    }

                    if(i == mSelectedPosition && mSelectionDrawable != null) {
                        mSelectionDrawable.setBounds(sTempRect.left - mSelectionPadding, sTempRect.top - mSelectionPadding,
                                sTempRect.right + mSelectionPadding, sTempRect.bottom + mSelectionPadding);
                        mSelectionTransformer.transform(mSelectionDrawable, angleFromSelection);
                        mSelectionDrawable.draw(canvas);
                    }

                    Drawable drawable;
                    if(cacheItem.mDrawable != null) {
                        drawable = cacheItem.mDrawable;
                    } else {
                        if(mEmptyItemDrawable != null) {
                            drawable = mEmptyItemDrawable;
                        } else {
                            drawable = null;
                        }
                    }

                    if(drawable != null) {
                        drawable.setBounds(sTempRect);
                        drawable.draw(canvas);
                    }
                } else {
                    if(cacheItem.mIsVisible) {
                        cacheItem.mIsVisible = false;
                        if(mOnItemVisibilityChangeListener != null) {
                            mOnItemVisibilityChangeListener.onItemVisibilityChange(mAdapter, adapterPosition, false);
                        }
                    }
                }
			}
		}
	}

    private Drawable createOvalDrawable(int color) {
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.getPaint().setColor(color);
        return shapeDrawable;
    }

    /**
     * A simple circle class with some helper methods used in {@link com.lukedeighton.wheelview.WheelView}
     */
    static class Circle {
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
            if(value < 0) {
                return value + (-1 * (int) Math.floor(value / (float) upperLimit)) * upperLimit;
            } else {
                return value % upperLimit;
            }
        }

        static float clamp180(float value) {
            return (((value + 180f) % 360f + 360f) % 360f) - 180f;
        }

        /**
         * Returns the shortest angle difference when the inputs range between -180 and 180 (such as from Math.atan2)
         */
        static float shortestAngle(float angleA, float angleB) {
            float angle = angleA - angleB;
            if(angle > 180f) {
                angle -=360f;
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
            return "Radius: " + mRadius + " X: " + mCenterX +  " Y: " + mCenterY;
        }
    }

    /**
     * A simple class to represent a vector with an add and cross product method.
     */
    static class Vector {
        float x, y;

        Vector() {}

        void set(float x, float y) {
            this.x = x;
            this.y = y;
        }

        float crossProduct(float x, float y) {
            return this.x * y - this.y * x;
        }

        @Override
        public String toString() {
            return "Vector: (" + this.x + ", " + this.y + ")";
        }
    }

    /**
     * Acts like a circular buffer, where the {@link #add(float)} method keeps a running average.
     * When the array of {@link #mPoints} is full, the first index is replaced by the next float {@link #add(float)}
     * This gives a smoother motion when {@link #flingWheel()} occurs.
     * It serves as a lightweight version of {@link android.view.VelocityTracker} but not as accurate at estimating,
     * since I'm doing a simple average (and I believe VelocityTracker does a polynomial best fit?)
     */
    static class AveragingFifoFloat {
        float[] mPoints;
        float mAverage;
        int mCurrentIndex;
        final int mCapacity;
        int mSize;

        AveragingFifoFloat(int capacity) {
            mCapacity = capacity;
            mPoints = new float[capacity];
        }

        void reset() {
            for(int i = 0; i < mSize; i++) {
                mPoints[i] = 0f;
            }
            mSize = 0;
            mAverage = 0f;
        }

        void add(float value) {
            float total = mAverage * mSize;

            if(mSize < mCapacity) {
                mSize++;
            } else {
                total -= mPoints[mCurrentIndex];
            }

            mAverage = (total + value) / mSize;
            mPoints[mCurrentIndex++] = value;
            if(mCurrentIndex >= mCapacity) mCurrentIndex = 0;
        }
    }
}
