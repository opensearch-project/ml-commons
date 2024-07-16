package org.opensearch.ml.common;

import java.util.Locale;

public enum ActionType {
    PREDICT,
    BATCH;

    public static ActionType from(String value) {
        try {
            return ActionType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong Predict mode");
        }
    }
}
