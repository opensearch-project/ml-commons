/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLHybridSearchMemoriesAction extends ActionType<SearchResponse> {
    public static final MLHybridSearchMemoriesAction INSTANCE = new MLHybridSearchMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memories/hybrid_search";

    private MLHybridSearchMemoriesAction() {
        super(NAME, SearchResponse::new);
    }
}
