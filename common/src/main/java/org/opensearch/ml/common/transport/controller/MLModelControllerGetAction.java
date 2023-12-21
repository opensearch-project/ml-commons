/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

public class MLModelControllerGetAction extends ActionType<MLModelControllerGetResponse> {
    public static final MLModelControllerGetAction INSTANCE = new MLModelControllerGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/model_controllers/get";

    private MLModelControllerGetAction() { super(NAME, MLModelControllerGetResponse::new);}
}
