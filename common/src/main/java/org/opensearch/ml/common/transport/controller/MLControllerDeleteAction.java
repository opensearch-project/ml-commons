/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLControllerDeleteAction extends ActionType<DeleteResponse> {
    public static final MLControllerDeleteAction INSTANCE = new MLControllerDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/delete";

    private MLControllerDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}
