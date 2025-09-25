/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLGetWorkingMemoryAction extends ActionType<MLGetWorkingMemoryResponse> {
    public static final MLGetWorkingMemoryAction INSTANCE = new MLGetWorkingMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memory/working/get";

    private MLGetWorkingMemoryAction() {
        super(NAME, MLGetWorkingMemoryResponse::new);
    }
}
