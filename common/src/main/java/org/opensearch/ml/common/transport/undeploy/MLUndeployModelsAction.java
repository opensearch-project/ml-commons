/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import org.opensearch.action.ActionType;

public class MLUndeployModelsAction extends ActionType<MLUndeployModelNodesResponse> {
    public static MLUndeployModelsAction INSTANCE = new MLUndeployModelsAction();
    public static final String NAME = "cluster:admin/opensearch/ml/undeploy_models";

    private MLUndeployModelsAction() {
        super(NAME, MLUndeployModelNodesResponse::new);
    }

}
