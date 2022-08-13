/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.predict;

import org.opensearch.action.ActionType;

public class MLPredictModelAction extends ActionType<PredictModelResponse> {
    public static MLPredictModelAction INSTANCE = new MLPredictModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/predict_custom_model";

    private MLPredictModelAction() {
        super(NAME, PredictModelResponse::new);
    }

}
