/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.prediction;

import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.action.ActionType;

public class MLPredictionTaskRemoteExecutionAction extends ActionType<MLPredictionTaskResponse> {
    public static MLPredictionTaskRemoteExecutionAction INSTANCE = new MLPredictionTaskRemoteExecutionAction();
    public static final String NAME = "cluster:admin/opensearch-ml/predict/" + "ml_task_remote";

    private MLPredictionTaskRemoteExecutionAction() {
        super(NAME, MLPredictionTaskResponse::new);
    }
}
