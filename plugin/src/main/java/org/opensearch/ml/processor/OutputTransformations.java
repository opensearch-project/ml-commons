/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for output transformations in ML inference processor
 */
public class OutputTransformations {

    private static final String MEAN_POOLING_SUFFIX = ".meanPooling()";

    /**
     * Checks if the output field name contains a transformation function
     */
    public static boolean hasTransformation(String outputFieldName) {
        return outputFieldName != null && outputFieldName.endsWith(MEAN_POOLING_SUFFIX);
    }

    /**
     * Extracts the base field name without transformation function
     */
    public static String getBaseFieldName(String outputFieldName) {
        if (hasTransformation(outputFieldName)) {
            return outputFieldName.substring(0, outputFieldName.length() - MEAN_POOLING_SUFFIX.length());
        }
        return outputFieldName;
    }

    /**
     * Applies mean pooling transformation to a nested array of floats
     */
    public static Object applyMeanPooling(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Mean pooling requires a list input");
        }

        List<?> outerList = (List<?>) value;
        if (outerList.isEmpty() || !(outerList.get(0) instanceof List)) {
            throw new IllegalArgumentException("Mean pooling requires nested array structure");
        }

        List<?> firstVector = (List<?>) outerList.get(0);
        int dimensions = firstVector.size();
        double[] meanVector = new double[dimensions];

        // Sum all vectors
        for (Object vectorObj : outerList) {
            if (!(vectorObj instanceof List)) {
                throw new IllegalArgumentException("All elements must be vectors (lists)");
            }
            List<?> vector = (List<?>) vectorObj;
            if (vector.size() != dimensions) {
                throw new IllegalArgumentException("All vectors must have the same dimension");
            }

            for (int i = 0; i < dimensions; i++) {
                Object element = vector.get(i);
                double val = element instanceof Number ? ((Number) element).doubleValue() : 0.0;
                meanVector[i] += val;
            }
        }

        // Calculate mean and convert to List
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < dimensions; i++) {
            result.add(meanVector[i] / outerList.size());
        }

        return result;
    }
}
