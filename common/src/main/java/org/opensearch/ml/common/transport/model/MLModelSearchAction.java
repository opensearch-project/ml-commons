/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLModelSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/models/search";
    public static final MLModelSearchAction INSTANCE = new MLModelSearchAction();

    private MLModelSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
