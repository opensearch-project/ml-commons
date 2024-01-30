/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateControllerAction extends ActionType<UpdateResponse> {
    public static final MLUpdateControllerAction INSTANCE = new MLUpdateControllerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/update";

    private MLUpdateControllerAction() {
        super(NAME, UpdateResponse::new);
    }
}
