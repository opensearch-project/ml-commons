/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;

public class MLMemoryContainerGetAction extends ActionType<MLMemoryContainerGetResponse> {
    public static final MLMemoryContainerGetAction INSTANCE = new MLMemoryContainerGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/get";

    private MLMemoryContainerGetAction() {
        super(NAME, MLMemoryContainerGetResponse::new);
    }
}
