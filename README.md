WheelView
=========

WheelView is an Android library that allows drawables to be placed on a rotatable wheel. The `WheelView` can be used as a way to select one item from a list. The `SelectionAngle` determines what position on the wheel is selected. Have a look at the sample for a working example!

The library is still in its infancy so if there are any bugs or missing features please let me know!

![1]
![2]

Note - Framerate is much better than these poorly converted gifs!

Usage
-----

1) Add the custom view in xml
```xml
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.lukedeighton.wheelview.WheelView
        android:id="@+id/wheelview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:wheelColor="@color/grey_400"
        app:rotatableWheelDrawable="false"
        app:selectionAngle="90.0"
        app:wheelPosition="bottom"
        app:wheelOffsetY="60dp"
        app:repeatItems="true"
        app:wheelRadius="276dp"
        app:wheelItemCount="14"
        app:wheelPadding="13dp"
        app:wheelItemRadius="43dp"/>
</RelativeLayout>
```

2) Set a `WheelAdapter` similar to how you would set an adapter with a ListView
```java
wheelView.setAdapter(new WheelView.WheelAdapter() {
    @Override
    public Drawable getDrawable(int position) {
        //return drawable here - the position can be seen in the gifs above
    }

    @Override
    public int getCount() {
        //return the count
    }
});
```

3) Set a listener to receive a callback when the closest item to the `SelectionAngle` changes.
```java
wheelView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectListener() {
    @Override
    public void onWheelItemSelected(WheelView parent, int position) {
        //the adapter position that is closest to the selection angle
    }
});
```

Attributes
----------

The WheelView is highly customisable with many attributes that can be set via xml or programmatically (recommend using xml as programmatically set attributes is half implemented at the moment). Here are the custom attributes that can be declared in xml:

  * wheelDrawable
  * wheelColor
  * emptyItemDrawable
  * emptyItemColor
  * selectionDrawable
  * selectionColor
  * selectionPadding
  * selectionAngle
  * repeatItems
  * wheelRadius
  * wheelItemRadius
  * rotatableWheelDrawable
  * wheelOffsetX
  * wheelOffsetY
  * wheelItemCount
  * wheelItemAngle
  * wheelToItemDistance
  * wheelPosition
  * wheelPadding
  * wheelItemTransformer
  * selectionTransformer

[1]: ./Graphics/bottom_wheel.gif
[2]: ./Graphics/center_wheel.gif
