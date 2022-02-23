/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.training;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class MLTrainingTaskAction extends ActionType<MLTaskResponse> {
    public static MLTrainingTaskAction INSTANCE = new MLTrainingTaskAction();
    public static final String NAME = "cluster:admin/opensearch/ml/train";

    private MLTrainingTaskAction() {
        super(NAME, MLTaskResponse::new);
    }

}
