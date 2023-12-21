/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLModelControllerDeleteAction extends ActionType<DeleteResponse> {
    public static final MLModelControllerDeleteAction INSTANCE = new MLModelControllerDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/model_controllers/delete";

    private MLModelControllerDeleteAction() { super(NAME, DeleteResponse::new);}
}
