/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateModelControllerAction extends ActionType<UpdateResponse> {
    public static final MLUpdateModelControllerAction INSTANCE = new MLUpdateModelControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/model_controllers/update";

    private MLUpdateModelControllerAction() {
        super(NAME, UpdateResponse::new);
    }
}
