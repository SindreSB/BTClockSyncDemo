package com.sindrebrun.btclocksyncdemo;

import java.util.List;

/**
 * Created by sindr on 10/11/2017.
 */

public class Utils {
    public static long mode(Long a[]) {
        long maxValue = 0;
        long maxCount = 0;

        for (int i = 0; i < a.length; ++i) {
            int count = 0;
            for (int j = 0; j < a.length; ++j) {
                if (a[j] == a[i]) ++count;
            }
            if (count > maxCount) {
                maxCount = count;
                maxValue = a[i];
            }
        }

        return maxValue;
    }

    public static double average(List<Long> values) {
        Long sum = 0L;
        for(Long e: values){
            sum += e;
        }

        double average = (double)sum / (double)values.size();

        return average;
    }
}
