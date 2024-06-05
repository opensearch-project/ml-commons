package org.opensearch.ml.common;

import java.util.Locale;

public enum PredictMode {
    PREDICT,
    BATCH,
    ASYNC,
    STREAMING;

    public static PredictMode from(String value) {
        try {
            return PredictMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong Predict mode");
        }
    }
}
