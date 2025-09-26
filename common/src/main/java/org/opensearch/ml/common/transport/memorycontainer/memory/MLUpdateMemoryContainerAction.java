/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateMemoryContainerAction extends ActionType<UpdateResponse> {
    public static final MLUpdateMemoryContainerAction INSTANCE = new MLUpdateMemoryContainerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/update";

    private MLUpdateMemoryContainerAction() {
        super(NAME, UpdateResponse::new);
    }

}
