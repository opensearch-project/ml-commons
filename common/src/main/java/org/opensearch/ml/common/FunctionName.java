/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Please strictly add new FunctionName to the last line
// and avoid altering the order of the existing FunctionName,
// or it will break the backward compatibility!
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
    METRICS_CORRELATION,
    REMOTE,
    SPARSE_ENCODING,
    SPARSE_TOKENIZE,
    TEXT_SIMILARITY,
    QUESTION_ANSWERING,
    AGENT,
    CONNECTOR;

    public static FunctionName from(String value) {
        try {
            return FunctionName.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong function name");
        }
    }

    private static final HashSet<FunctionName> DL_MODELS = new HashSet<>(Set.of(
        TEXT_EMBEDDING,
        TEXT_SIMILARITY,
        SPARSE_ENCODING,
        SPARSE_TOKENIZE,
        QUESTION_ANSWERING
    ));

    /**
     * Check if model is deep learning model.
     * @return true for deep learning model.
     */
    public static boolean isDLModel(FunctionName functionName) {
        return DL_MODELS.contains(functionName);
    }

    public static boolean needDeployFirst(FunctionName functionName) {
        return DL_MODELS.contains(functionName) || functionName == REMOTE;
    }

    public static boolean isAutoDeployEnabled(boolean autoDeploymentEnabled, FunctionName functionName) {
        return autoDeploymentEnabled && functionName == FunctionName.REMOTE;
    }
}
