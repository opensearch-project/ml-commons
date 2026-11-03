/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLSemanticSearchMemoriesAction extends ActionType<SearchResponse> {
    public static final MLSemanticSearchMemoriesAction INSTANCE = new MLSemanticSearchMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/memories/semantic_search";

    private MLSemanticSearchMemoriesAction() {
        super(NAME, SearchResponse::new);
    }
}
