WheelView
=========

WheelView is an Android library that allows drawables to be placed on a rotatable wheel. The `WheelView` can be used as a way to select one item from a list. The `SelectionAngle` determines what position on the wheel is selected. Have a look at the sample for a working example!

The library is still in its infancy so if there are any bugs or missing features please let me know!

Usage
-----

To determine the item that is most selected set an `OnWheelItemSelectListener`. The listener will be called when the closest item to the `SelectionAngle` changes.
```java
wheelView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectListener() {
    @Override
    public void onWheelItemSelected(WheelView parent, int position) {
        //the adapter position that is closest to the selection angle
    }
});
```

The number of positions on the wheel is independent to the data to be displayed on the wheel. To link data to the wheel items use the `WheelAdapter` which has similar responsiblity to that of a ListView's `ListAdapter`, however currently only works with Drawables.

```java
wheelView.setAdapter(new WheelView.WheelAdapter() {
    @Override
    public Drawable getDrawable(int position) {
        //return drawable here
    }

    @Override
    public int getCount() {
        //return the count
    }
});
```

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
