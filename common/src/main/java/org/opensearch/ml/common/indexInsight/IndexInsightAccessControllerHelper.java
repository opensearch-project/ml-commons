/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class IndexInsightAccessControllerHelper {
    // Verify the access by dry run
    public static void verifyAccessController(Client client, ActionListener<Boolean> actionListener, String targetIndex) {
        SearchRequest searchRequest = constructSimpleQueryRequest(targetIndex);
        client.search(searchRequest, ActionListener.wrap(r -> { actionListener.onResponse(true); }, e -> {
            log.error("You don't have access to this index");
            actionListener.onFailure(new IllegalArgumentException("You don't have access to this index"));
        }));
    }

    public static SearchRequest constructSimpleQueryRequest(String targetIndex) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        searchSourceBuilder.size(1);
        SearchRequest searchRequest = new SearchRequest(targetIndex);
        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }
}
