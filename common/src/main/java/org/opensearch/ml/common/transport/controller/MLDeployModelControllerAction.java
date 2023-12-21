/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

public class MLDeployModelControllerAction extends ActionType<MLDeployModelControllerNodesResponse> {
    public static final MLDeployModelControllerAction INSTANCE = new MLDeployModelControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/deploy_model_controllers";

    private MLDeployModelControllerAction() { super(NAME, MLDeployModelControllerNodesResponse::new);}
}
