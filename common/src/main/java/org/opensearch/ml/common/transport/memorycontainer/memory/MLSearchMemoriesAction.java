/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLSearchMemoriesAction extends ActionType<MLSearchMemoriesResponse> {
    public static final MLSearchMemoriesAction INSTANCE = new MLSearchMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memories/search";

    private MLSearchMemoriesAction() {
        super(NAME, MLSearchMemoriesResponse::new);
    }
}
