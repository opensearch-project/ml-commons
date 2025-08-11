/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemorySearchService {

    private final Client client;

    public MemorySearchService(Client client) {
        this.client = client;
    }

    public void searchSimilarFactsForSession(
        List<String> facts,
        String sessionId,
        String indexName,
        MemoryStorageConfig storageConfig,
        ActionListener<List<FactSearchResult>> listener
    ) {
        if (sessionId == null || facts.isEmpty()) {
            log.debug("Skipping fact search: facts count={}", facts.size());
            listener.onResponse(new ArrayList<>());
            return;
        }

        List<FactSearchResult> allResults = new ArrayList<>();
        int maxInferSize = storageConfig != null && storageConfig.getMaxInferSize() != null ? storageConfig.getMaxInferSize() : 5;

        // Limit the number of facts to process based on maxInferSize
        List<String> factsToProcess = facts.size() > maxInferSize ? facts.subList(0, maxInferSize) : facts;

        searchFactsSequentially(factsToProcess, 0, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
    }

    private void searchFactsSequentially(
        List<String> facts,
        int currentIndex,
        String sessionId,
        String indexName,
        MemoryStorageConfig storageConfig,
        int maxInferSize,
        List<FactSearchResult> allResults,
        ActionListener<List<FactSearchResult>> listener
    ) {
        if (currentIndex >= facts.size()) {
            listener.onResponse(allResults);
            return;
        }

        String fact = facts.get(currentIndex);

        try {
            XContentBuilder queryBuilder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, storageConfig);
            String queryJson = queryBuilder.toString();

            log.debug("Searching for similar facts with query: {}", queryJson);

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.wrapperQuery(queryJson));
            searchSourceBuilder.size(maxInferSize);
            searchSourceBuilder.fetchSource(new String[] { MEMORY_FIELD }, null);

            SearchRequest searchRequest = new SearchRequest().indices(indexName).source(searchSourceBuilder);

            client.search(searchRequest, ActionListener.wrap(response -> {
                for (SearchHit hit : response.getHits().getHits()) {
                    Map<String, Object> sourceMap = hit.getSourceAsMap();
                    String memory = (String) sourceMap.get(MEMORY_FIELD);
                    if (memory != null) {
                        allResults.add(new FactSearchResult(hit.getId(), memory, hit.getScore()));
                    }
                }

                log.debug("Found {} similar facts for: {}", response.getHits().getHits().length, fact);

                searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
            }, e -> {
                log.error("Failed to search for similar facts for: {}", fact, e);
                searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
            }));
        } catch (Exception e) {
            log.error("Failed to build search query for fact: {}", fact, e);
            searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
        }
    }
}
