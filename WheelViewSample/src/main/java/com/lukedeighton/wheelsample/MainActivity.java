package com.lukedeighton.wheelsample;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.lukedeighton.wheelview.WheelView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WheelView wheelView = (WheelView) findViewById(R.id.wheelview);
        wheelView.setWheelColor(getResources().getColor(R.color.grey_400));
        wheelView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectListener() {
            @Override
            public void onWheelItemSelected(WheelView.WheelAdapter adapter, int position) {
                //Toast.makeText(MainActivity.this, "Selected Item at position: " + position, Toast.LENGTH_SHORT).show();
            }
        });
        wheelView.setAdapter(new WheelView.WheelAdapter() {
            @Override
            public Drawable getDrawable(int position) {
                Log.d("test", "get item at " + position);
                Drawable[] drawable = new Drawable[] {
                    createOvalDrawable(MaterialColor.random(MainActivity.this, "\\D*_500$") ),
                    new TextDrawable(String.valueOf(position))
                };
                return new LayerDrawable(drawable);
            }

            @Override
            public int getCount() {
                return 22;
            }
        });
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