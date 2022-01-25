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

package org.opensearch.ml.common.transport.trainpredict;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class MLTrainAndPredictionTaskAction extends ActionType<MLTaskResponse> {
    public static final MLTrainAndPredictionTaskAction INSTANCE = new MLTrainAndPredictionTaskAction();
    public static final String NAME = "cluster:admin/opensearch/ml/trainAndPredict";

    private MLTrainAndPredictionTaskAction() {
        super(NAME, MLTaskResponse::new);
    }
}
