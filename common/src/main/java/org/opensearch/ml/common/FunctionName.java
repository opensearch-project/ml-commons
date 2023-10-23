/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

public enum FunctionName {
    LINEAR_REGRESSION,
    KMEANS,
    AD_LIBSVM,
    SAMPLE_ALGO,
    LOCAL_SAMPLE_CALCULATOR,
    FIT_RCF,
    BATCH_RCF,
    ANOMALY_LOCALIZATION,
    RCF_SUMMARIZE,
    LOGISTIC_REGRESSION,
    TEXT_EMBEDDING,
    SPARSE_ENCODING,
    SPARSE_TOKENIZE,
    METRICS_CORRELATION,
    REMOTE,
    AGENT,
    CONNECTOR;

    public static FunctionName from(String value) {
        try {
            return FunctionName.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong function name");
        }
    }

    /**
     * Check if model is deep learning model.
     * @return true for deep learning model.
     */
    public static boolean isDLModel(FunctionName functionName) {
        if (functionName == TEXT_EMBEDDING || functionName == SPARSE_ENCODING || functionName == SPARSE_TOKENIZE) {
            return true;
        }
        return false;
    }
}
