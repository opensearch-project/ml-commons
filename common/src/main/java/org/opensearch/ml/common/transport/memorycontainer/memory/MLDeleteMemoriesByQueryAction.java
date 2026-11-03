/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

/**
 * Action for deleting memories by query in a memory container
 */
public class MLDeleteMemoriesByQueryAction extends ActionType<MLDeleteMemoriesByQueryResponse> {

    public static final MLDeleteMemoriesByQueryAction INSTANCE = new MLDeleteMemoriesByQueryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memories/delete_by_query";

    private MLDeleteMemoriesByQueryAction() {
        super(NAME, MLDeleteMemoriesByQueryResponse::new);
    }
}
