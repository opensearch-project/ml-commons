/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

public enum MLTaskType {
    TRAINING,
    PREDICTION,
    TRAINING_AND_PREDICTION,
    EXECUTION,
    UPLOAD_MODEL,
    LOAD_MODEL
}
