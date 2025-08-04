/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLDeleteMemoriesAction extends ActionType<DeleteResponse> {
    public static final MLDeleteMemoriesAction INSTANCE = new MLDeleteMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/delete";

    private MLDeleteMemoriesAction() {
        super(NAME, DeleteResponse::new);
    }
}
