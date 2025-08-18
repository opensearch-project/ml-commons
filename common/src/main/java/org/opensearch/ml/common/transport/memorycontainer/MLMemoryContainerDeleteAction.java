/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLMemoryContainerDeleteAction extends ActionType<DeleteResponse> {
    public static final MLMemoryContainerDeleteAction INSTANCE = new MLMemoryContainerDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/delete";

    private MLMemoryContainerDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}
