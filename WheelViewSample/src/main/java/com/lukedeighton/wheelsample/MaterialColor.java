package com.lukedeighton.wheelsample;

import android.content.Context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class MaterialColor {
    private static Random sRandom = new Random(6);
    private static List<Integer> sMaterialColors;

    private static List<Integer> getMaterialColors(Context context, Pattern pattern) {
        Field[] fields = R.color.class.getFields();
        List<Integer> materialColors = new ArrayList<Integer>(fields.length);
        for(Field field : fields) {
            if(!pattern.matcher(field.getName()).matches()) continue;

            try {
                int resId = field.getInt(null);
                materialColors.add(context.getResources().getColor(resId));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return materialColors;
    }

    public static int random(Context context, String regex) {
        if(sMaterialColors == null) {
            sMaterialColors = getMaterialColors(context, Pattern.compile(regex));
        }

        int rndIndex = sRandom.nextInt(sMaterialColors.size());
        return sMaterialColors.get(rndIndex);
    }
}
