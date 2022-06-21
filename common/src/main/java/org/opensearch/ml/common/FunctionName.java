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
    OBJECT_DETECTION,
    RESNET18,
    BERT_QA;

    public static FunctionName from(String value) {
        try {
            return FunctionName.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong function name");
        }
    }
}
