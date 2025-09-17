/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLGetMemoryAction extends ActionType<MLGetMemoryResponse> {
    public static final MLGetMemoryAction INSTANCE = new MLGetMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memory/get";

    private MLGetMemoryAction() {
        super(NAME, MLGetMemoryResponse::new);
    }
}
