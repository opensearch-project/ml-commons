/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLConnectorSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/search";
    public static final MLConnectorSearchAction INSTANCE = new MLConnectorSearchAction();

    private MLConnectorSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
