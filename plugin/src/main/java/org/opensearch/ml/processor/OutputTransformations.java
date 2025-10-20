/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for output transformations in ML inference processor
 *
 * TODO: Support additional pooling methods like max pooling, min pooling, etc.
 */
public class OutputTransformations {

    private static final String MEAN_POOLING_SUFFIX = ".meanPooling()";
    private static final String MAX_POOLING_SUFFIX = ".maxPooling()";

    /**
     * Checks if the output field name contains a transformation function
     */
    public static boolean hasTransformation(String outputFieldName) {
        return outputFieldName != null && (outputFieldName.endsWith(MEAN_POOLING_SUFFIX) || outputFieldName.endsWith(MAX_POOLING_SUFFIX));
    }

    /**
     * Extracts the base field name without transformation function
     */
    public static String getBaseFieldName(String outputFieldName) {
        if (outputFieldName != null) {
            if (outputFieldName.endsWith(MEAN_POOLING_SUFFIX)) {
                return outputFieldName.substring(0, outputFieldName.length() - MEAN_POOLING_SUFFIX.length());
            } else if (outputFieldName.endsWith(MAX_POOLING_SUFFIX)) {
                return outputFieldName.substring(0, outputFieldName.length() - MAX_POOLING_SUFFIX.length());
            }
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
        if (outerList.isEmpty() || !(outerList.getFirst() instanceof List)) {
            throw new IllegalArgumentException("Mean pooling requires nested array structure");
        }

        List<?> firstVector = (List<?>) outerList.getFirst();
        int dimensions = firstVector.size();
        float[] meanVector = new float[dimensions];

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
                float val = element instanceof Number ? ((Number) element).floatValue() : 0.0f;
                meanVector[i] += val;
            }
        }

        // Calculate mean and convert to List
        List<Float> result = new ArrayList<>();
        for (int i = 0; i < dimensions; i++) {
            result.add(meanVector[i] / outerList.size());
        }

        return result;
    }

    /**
     * Applies max pooling transformation to a nested array of floats
     */
    public static Object applyMaxPooling(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Max pooling requires a list input");
        }

        List<?> outerList = (List<?>) value;
        if (outerList.isEmpty() || !(outerList.getFirst() instanceof List)) {
            throw new IllegalArgumentException("Max pooling requires nested array structure");
        }

        List<?> firstVector = (List<?>) outerList.getFirst();
        int dimensions = firstVector.size();
        float[] maxVector = new float[dimensions];

        // Initialize with first vector
        List<?> firstVectorList = (List<?>) outerList.getFirst();
        for (int i = 0; i < dimensions; i++) {
            Object element = firstVectorList.get(i);
            maxVector[i] = element instanceof Number ? ((Number) element).floatValue() : Float.NEGATIVE_INFINITY;
        }

        // Find max across all vectors
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
                float val = element instanceof Number ? ((Number) element).floatValue() : Float.NEGATIVE_INFINITY;
                maxVector[i] = Math.max(maxVector[i], val);
            }
        }

        // Convert to List
        List<Float> result = new ArrayList<>();
        for (int i = 0; i < dimensions; i++) {
            result.add(maxVector[i]);
        }

        return result;
    }

    /**
     * Applies the appropriate transformation based on the field name suffix
     */
    public static Object applyTransformation(String outputFieldName, Object value) {
        if (outputFieldName != null) {
            if (outputFieldName.endsWith(MEAN_POOLING_SUFFIX)) {
                return applyMeanPooling(value);
            } else if (outputFieldName.endsWith(MAX_POOLING_SUFFIX)) {
                return applyMaxPooling(value);
            }
        }
        return value;
    }
}
