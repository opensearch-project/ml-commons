/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.contants;

public enum TribuoOutputType {
    //for tribuo clustering
    CLUSTERID,
    //for tribuo regression
    REGRESSOR,
    //for anomaly detection based on libSVM
    ANOMALY_DETECTION_LIBSVM;
}
