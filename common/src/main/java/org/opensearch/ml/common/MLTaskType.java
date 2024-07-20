/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

public enum MLTaskType {
    TRAINING,
    PREDICTION,
    BATCH_PREDICTION,
    TRAINING_AND_PREDICTION,
    EXECUTION,
    @Deprecated
    UPLOAD_MODEL,
    @Deprecated
    LOAD_MODEL,
    REGISTER_MODEL,
    DEPLOY_MODEL
}
