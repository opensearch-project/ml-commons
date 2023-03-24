/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import org.opensearch.action.ActionType;

public class MLDeployModelAction extends ActionType<MLDeployModelResponse> {
    public static MLDeployModelAction INSTANCE = new MLDeployModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/deploy_model";

    private MLDeployModelAction() {
        super(NAME, MLDeployModelResponse::new);
    }

}
