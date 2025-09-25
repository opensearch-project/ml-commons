/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLDeleteWorkingMemoryAction extends ActionType<DeleteResponse> {
    public static final MLDeleteWorkingMemoryAction INSTANCE = new MLDeleteWorkingMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memory/working/delete";

    private MLDeleteWorkingMemoryAction() {
        super(NAME, DeleteResponse::new);
    }
}
