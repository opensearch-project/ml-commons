/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;

public class MLCreateMemoryContainerAction extends ActionType<MLCreateMemoryContainerResponse> {
    public static final MLCreateMemoryContainerAction INSTANCE = new MLCreateMemoryContainerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/create";

    private MLCreateMemoryContainerAction() {
        super(NAME, MLCreateMemoryContainerResponse::new);
    }
}
