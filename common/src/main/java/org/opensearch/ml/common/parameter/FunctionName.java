/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.parameter;

public enum FunctionName {
    LINEAR_REGRESSION,
    KMEANS,
    AD_LIBSVM,
    SAMPLE_ALGO,
    LOCAL_SAMPLE_CALCULATOR,
    ANOMALY_LOCALIZATION,
    FIT_RCF,
    BATCH_RCF;

    public static FunctionName from(String value) {
        try {
            return FunctionName.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong function name");
        }
    }
}
