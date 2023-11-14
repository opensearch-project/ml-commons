package org.opensearch.ml.engine.utils;

import java.util.function.BiFunction;

public class MathUtil {
    public static <T, C extends Number> int findNearest(T query, Iterable<T> base, BiFunction<T, T, C> dist) {
        int index = -1;
        double minValue = Double.MAX_VALUE;
        int i = 0;
        for (T e : base) {
            double d = dist.apply(query, e).doubleValue();
            if (d < minValue) {
                minValue = d;
                index = i;
            }

            i++;
        }

        return index;
    }
}
