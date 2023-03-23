/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import org.opensearch.action.ActionType;

public class MLUndeployModelAction extends ActionType<MLUndeployModelNodesResponse> {
    public static MLUndeployModelAction INSTANCE = new MLUndeployModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/undeploy_model";

    private MLUndeployModelAction() {
        super(NAME, MLUndeployModelNodesResponse::new);
    }

}
