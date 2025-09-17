/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLAddMemoriesAction extends ActionType<MLAddMemoriesResponse> {
    public static final MLAddMemoriesAction INSTANCE = new MLAddMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memories/add";

    private MLAddMemoriesAction() {
        super(NAME, MLAddMemoriesResponse::new);
    }
}
