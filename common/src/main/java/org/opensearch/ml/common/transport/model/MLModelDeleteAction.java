/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLModelDeleteAction extends ActionType<DeleteResponse> {
    public static final MLModelDeleteAction INSTANCE = new MLModelDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/models/delete";

    private MLModelDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}
