/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

public class MLPromptSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/prompts/search";
    public static final MLPromptSearchAction INSTANCE = new MLPromptSearchAction();

    private MLPromptSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
