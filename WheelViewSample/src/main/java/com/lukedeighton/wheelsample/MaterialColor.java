package com.lukedeighton.wheelsample;

import android.content.Context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaterialColor {
    private static Random sRandom = new Random();
    private static HashMap<String, Integer> sMaterialHashMap;
    private static Pattern sColorPattern = Pattern.compile("_[aA]?+\\d+");

    private static HashMap<String, Integer> getMaterialColors(Context context) {
        Field[] fields = R.color.class.getFields();
        HashMap<String, Integer> materialHashMap = new HashMap<String, Integer>(fields.length);
        for(Field field : fields) {
            if (field.getType() != int.class) continue;

            String fieldName = field.getName(); //prone to errors but okay for a sample!
            if (fieldName.startsWith("abc") || fieldName.startsWith("material")) continue;

            try {
                int resId = field.getInt(null);
                materialHashMap.put(fieldName, context.getResources().getColor(resId));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return materialHashMap;
    }

    public static Map.Entry<String, Integer> random(Context context, String regex) {
        if (sMaterialHashMap == null) {
            sMaterialHashMap = getMaterialColors(context);
        }

        Pattern pattern = Pattern.compile(regex);
        List<Map.Entry<String, Integer>> materialColors = new ArrayList<Map.Entry<String, Integer>>();
        for(Map.Entry<String, Integer> entry : sMaterialHashMap.entrySet()) {
            if (!pattern.matcher(entry.getKey()).matches()) continue;
            materialColors.add(entry);
        }

        int rndIndex = sRandom.nextInt(materialColors.size());
        return materialColors.get(rndIndex);
    }

    public static int getContrastColor(String colourName) {
        return sMaterialHashMap.get(colourName + "_700");
    }

    public static String getColorName(Map.Entry<String, Integer> entry) {
        String color = entry.getKey();
        Matcher matcher = sColorPattern.matcher(color);
        if (matcher.find()) {
            return color.substring(0, matcher.start());
        }
        return null;
    }
}
