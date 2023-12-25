/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLSearchAgentAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/agents/search";
    public static final MLSearchAgentAction INSTANCE = new MLSearchAgentAction();

    private MLSearchAgentAction() {
        super(NAME, SearchResponse::new);
    }
}
