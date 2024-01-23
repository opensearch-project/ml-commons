/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

// This action will only be passively called when deleting a model controller when the model is deployed.
public class MLUndeployControllerAction extends ActionType<MLUndeployControllerNodesResponse> {
    public static final MLUndeployControllerAction INSTANCE = new MLUndeployControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/undeploy";

    private MLUndeployControllerAction() {
        super(NAME, MLUndeployControllerNodesResponse::new);
    }
}
