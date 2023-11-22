/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLTaskDeleteAction extends ActionType<DeleteResponse> {
    public static final MLTaskDeleteAction INSTANCE = new MLTaskDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/tasks/delete";

    private MLTaskDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}
