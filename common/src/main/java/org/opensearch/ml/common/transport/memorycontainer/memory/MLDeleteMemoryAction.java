/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLDeleteMemoryAction extends ActionType<DeleteResponse> {
    public static final MLDeleteMemoryAction INSTANCE = new MLDeleteMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memory/delete";

    private MLDeleteMemoryAction() {
        super(NAME, DeleteResponse::new);
    }
}
