/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

public class MLCreateModelControllerAction extends ActionType<MLCreateModelControllerResponse>{
    public static final MLCreateModelControllerAction INSTANCE = new MLCreateModelControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/create_model_controller";

    private MLCreateModelControllerAction() {
        super(NAME, MLCreateModelControllerResponse::new);
    }
    
}
