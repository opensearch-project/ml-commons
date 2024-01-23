/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

public class MLCreateControllerAction extends ActionType<MLCreateControllerResponse> {
    public static final MLCreateControllerAction INSTANCE = new MLCreateControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/create";

    private MLCreateControllerAction() {
        super(NAME, MLCreateControllerResponse::new);
    }

}
