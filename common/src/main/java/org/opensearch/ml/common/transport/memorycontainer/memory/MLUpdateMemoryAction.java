/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateMemoryAction extends ActionType<UpdateResponse> {
    public static final MLUpdateMemoryAction INSTANCE = new MLUpdateMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memory/update";

    private MLUpdateMemoryAction() {
        super(NAME, UpdateResponse::new);
    }
}
