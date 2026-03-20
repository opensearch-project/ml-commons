/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.MemorySearchValidationUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportHybridSearchMemoriesAction extends HandledTransportAction<MLHybridSearchMemoriesRequest, SearchResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportHybridSearchMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLHybridSearchMemoriesAction.NAME, transportService, actionFilters, MLHybridSearchMemoriesRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLHybridSearchMemoriesRequest request, ActionListener<SearchResponse> actionListener) {
        MLHybridSearchMemoriesInput input = request.getMlHybridSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (!MemorySearchValidationUtils.validateSearchRequest(mlFeatureEnabledSetting, input, tenantId, "Hybrid", actionListener)) {
            return;
        }
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        memoryContainerHelper.getMemoryContainer(input.getMemoryContainerId(), tenantId, ActionListener.wrap(container -> {
            User user = RestActionUtils.getUserContext(client);
            if (!MemorySearchValidationUtils.validateContainerConfig(container, user, memoryContainerHelper, "Hybrid", actionListener)) {
                return;
            }
            executeHybridSearch(input, container, user, actionListener);
        }, actionListener::onFailure));
    }

    // Note: tenantId is not passed to the search call because hybrid search uses client.search()
    // directly (SearchDataObjectRequest doesn't support the pipeline parameter). Container access
    // is already validated via tenantId in the getMemoryContainer() call in doExecute().
    private void executeHybridSearch(
        MLHybridSearchMemoriesInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<SearchResponse> actionListener
    ) {
        try {
            MemoryConfiguration memoryConfig = container.getConfiguration();
            String ownerId = MemorySearchValidationUtils.resolveOwnerId(user, memoryContainerHelper);

            // Build hybrid query (match + neural) with inline filter via wrapperQuery
            String hybridQueryString = MemorySearchQueryBuilder
                .buildHybridSearchQueryString(
                    input.getQuery(),
                    memoryConfig,
                    input.getK(),
                    input.getNamespace(),
                    input.getTags(),
                    ownerId,
                    input.getMemoryContainerId(),
                    input.getFilter()
                );

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.wrapperQuery(hybridQueryString));
            searchSourceBuilder.size(input.getK());
            searchSourceBuilder.fetchSource(null, new String[] { "memory_embedding" });
            if (input.getMinScore() != null) {
                searchSourceBuilder.minScore(input.getMinScore());
            }

            // Use inline temporary pipeline per-request — no pre-created pipeline needed.
            // Benefits: no cluster state writes, supports per-request weight tuning,
            // works for both new and existing containers without backward compat concerns.
            // If neural-search plugin is not installed, the search will fail with a clear error.
            searchSourceBuilder.searchPipelineSource(buildInlinePipeline(input.getBm25Weight(), input.getNeuralWeight()));

            SearchRequest searchRequest = new SearchRequest(memoryConfig.getLongMemoryIndexName());
            searchRequest.source(searchSourceBuilder);

            doSearch(searchRequest, memoryConfig, actionListener);
        } catch (Exception e) {
            log.error("Failed to build hybrid search request", e);
            actionListener.onFailure(new OpenSearchException("Failed to build hybrid search request: " + e.getMessage(), e));
        }
    }

    /**
     * Builds the inline normalization-processor pipeline definition for hybrid search.
     * Uses mutable maps — OpenSearch's ConfigurationUtils.readOptionalStringProperty()
     * calls map.remove() when consuming processor config, so immutable maps will fail.
     * weights[0] = bm25Weight (match query), weights[1] = neuralWeight (neural query).
     */
    private static Map<String, Object> buildInlinePipeline(float bm25Weight, float neuralWeight) {
        Map<String, Object> weights = new HashMap<>();
        weights.put("weights", new ArrayList<>(List.of((double) bm25Weight, (double) neuralWeight)));

        Map<String, Object> combination = new HashMap<>();
        combination.put("technique", "arithmetic_mean");
        combination.put("parameters", weights);

        Map<String, Object> normalization = new HashMap<>();
        normalization.put("technique", "min_max");

        Map<String, Object> processor = new HashMap<>();
        processor.put("normalization", normalization);
        processor.put("combination", combination);

        Map<String, Object> normalizationProcessor = new HashMap<>();
        normalizationProcessor.put("normalization-processor", processor);

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("phase_results_processors", new ArrayList<>(List.of(normalizationProcessor)));
        return pipeline;
    }

    private void doSearch(SearchRequest searchRequest, MemoryConfiguration memoryConfig, ActionListener<SearchResponse> actionListener) {
        ActionListener<SearchResponse> errorHandlingListener = ActionListener.wrap(actionListener::onResponse, e -> {
            log.error("Hybrid search execution failed", e);
            // Provide a clear error if neural-search plugin is not installed
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("normalization-processor") || msg.contains("No processor factory registered")) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Hybrid search requires the neural-search plugin to be installed. "
                                + "Please install the neural-search plugin or use _semantic_search instead.",
                            RestStatus.BAD_REQUEST,
                            e
                        )
                    );
            } else {
                actionListener.onFailure(new OpenSearchException("Hybrid search execution failed: " + msg, e));
            }
        });

        if (memoryConfig.isUseSystemIndex()) {
            // Stash context and restore it after the async response — do NOT use try-with-resources
            // because the context must remain open until the async callback fires
            ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext();
            client.search(searchRequest, ActionListener.runBefore(errorHandlingListener, context::restore));
        } else {
            client.search(searchRequest, errorHandlingListener);
        }
    }
}
