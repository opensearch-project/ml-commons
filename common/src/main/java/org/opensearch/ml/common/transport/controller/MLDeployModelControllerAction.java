/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

// This action will only be passively called when creating or updating a model controller when the model is deployed.
public class MLDeployModelControllerAction extends ActionType<MLDeployModelControllerNodesResponse> {
    public static final MLDeployModelControllerAction INSTANCE = new MLDeployModelControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/deploy";

    private MLDeployModelControllerAction() { super(NAME, MLDeployModelControllerNodesResponse::new);}
}
