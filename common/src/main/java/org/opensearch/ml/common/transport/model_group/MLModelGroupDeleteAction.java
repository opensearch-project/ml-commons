/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLModelGroupDeleteAction extends ActionType<DeleteResponse> {
    public static final MLModelGroupDeleteAction INSTANCE = new MLModelGroupDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/model_groups/delete";

    private MLModelGroupDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}
