/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

public enum ActionName {
    TRAIN,
    PREDICT,
    TRAIN_PREDICT,
    EXECUTE,
    UPLOAD,
    LOAD,
    UNLOAD;

    public static ActionName from(String value) {
        try {
            return ActionName.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong action name");
        }
    }
}
