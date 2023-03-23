/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import org.opensearch.action.ActionType;

public class MLDeployModelOnNodeAction extends ActionType<MLDeployModelNodesResponse> {
    public static MLDeployModelOnNodeAction INSTANCE = new MLDeployModelOnNodeAction();
    public static final String NAME = "cluster:admin/opensearch/ml/deploy_model_on_nodes";

    private MLDeployModelOnNodeAction() {
        super(NAME, MLDeployModelNodesResponse::new);
    }

}
