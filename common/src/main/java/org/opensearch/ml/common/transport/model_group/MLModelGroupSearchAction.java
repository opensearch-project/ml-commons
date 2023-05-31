/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLModelGroupSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/model_groups/search";
    public static final MLModelGroupSearchAction INSTANCE = new MLModelGroupSearchAction();

    private MLModelGroupSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
