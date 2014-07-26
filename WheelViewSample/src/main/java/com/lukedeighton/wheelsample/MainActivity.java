package com.lukedeighton.wheelsample;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.lukedeighton.wheelview.WheelView;

import java.util.Random;

//TODO design as much like new android/google design - simple colours like cards
public class MainActivity extends Activity {

    private Random mRandom = new Random(4);
    private float angle = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WheelView wheelView = (WheelView) findViewById(R.id.wheelview);

        wheelView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectListener() {
            @Override
            public void onWheelItemSelected(WheelView.WheelAdapter wheelItem, int position) {
                //Toast.makeText(MainActivity.this, "Selected Item at position: " + position, Toast.LENGTH_SHORT).show();
            }
        });
        wheelView.setAdapter(new WheelView.WheelAdapter() {
            @Override
            public Drawable getDrawable(int position) {

                Log.d("test", "get item at " + position);
                Drawable[] drawable = new Drawable[] {
                    createOvalDrawable(Color.argb(255, randomByte(), randomByte(), randomByte())),
                    new TextDrawable(String.valueOf(position))
                };

                return new LayerDrawable(drawable);
            }

            @Override
            public int getCount() {
                return 8;
            }
        });

       // wheelView.setSelectionAngle(180f);


        /*
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wheelView.setSelectionAngle(angle -= 10f);
                    }
                });
            }
        }, 500, 500);
*/

        /*
        wheelView.setOnWheelItemVisibilityChangeListener(new WheelView.OnWheelItemVisibilityChangeListener() {
            @Override
            public void onItemVisibilityChange(WheelView.WheelAdapter adapter, int position, boolean isVisible) {
                Log.d("test", "position: " + position + " isVisible: " + isVisible);
            }
        });
        */
    }

    private int randomByte() {
        return (int) (mRandom.nextFloat() * 255f + 0.5f);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Drawable createOvalDrawable(int color) {
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.getPaint().setColor(color);
        return shapeDrawable;
    }

}