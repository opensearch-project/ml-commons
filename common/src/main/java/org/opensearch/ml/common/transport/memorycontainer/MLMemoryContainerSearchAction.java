/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLMemoryContainerSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/search";
    public static final MLMemoryContainerSearchAction INSTANCE = new MLMemoryContainerSearchAction();

    private MLMemoryContainerSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
