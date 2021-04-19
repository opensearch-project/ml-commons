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

package org.opensearch.ml.common.transport.prediction;

import org.opensearch.action.ActionType;

public class MLPredictionTaskAction extends ActionType<MLPredictionTaskResponse> {
    public static final MLPredictionTaskAction INSTANCE = new MLPredictionTaskAction();
    public static final String NAME = "cluster:admin/opensearch-ml/predict";

    private MLPredictionTaskAction() {
        super(NAME, MLPredictionTaskResponse::new);
    }
}
