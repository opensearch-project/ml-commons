/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prediction;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class MLPredictionTaskAction extends ActionType<MLTaskResponse> {
    public static final MLPredictionTaskAction INSTANCE = new MLPredictionTaskAction();
    public static final String NAME = "cluster:admin/opensearch/ml/predict";

    private MLPredictionTaskAction() {
        super(NAME, MLTaskResponse::new);
    }
}
