/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionStats;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLModelStats;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatLevel;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.search.SearchHit;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLStatsNodesTransportAction extends
    TransportNodesAction<MLStatsNodesRequest, MLStatsNodesResponse, MLStatsNodeRequest, MLStatsNodeResponse> {
    private MLStats mlStats;
    private final JvmService jvmService;

    private final Client client;

    private final MLModelManager mlModelManager;

    /**
     * Constructor
     *
     * @param threadPool ThreadPool to use
     * @param clusterService ClusterService
     * @param transportService TransportService
     * @param actionFilters Action Filters
     * @param mlStats MLStats object
     * @param environment OpenSearch Environment
     */
    @Inject
    public MLStatsNodesTransportAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        MLStats mlStats,
        Environment environment,
        Client client,
        MLModelManager mlModelManager
    ) {
        super(
            MLStatsNodesAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLStatsNodesRequest::new,
            MLStatsNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLStatsNodeResponse.class
        );
        this.mlStats = mlStats;
        this.jvmService = new JvmService(environment.settings());
        this.client = client;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected MLStatsNodesResponse newResponse(
        MLStatsNodesRequest request,
        List<MLStatsNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLStatsNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLStatsNodeRequest newNodeRequest(MLStatsNodesRequest request) {
        return new MLStatsNodeRequest(request);
    }

    @Override
    protected MLStatsNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLStatsNodeResponse(in);
    }

    @Override
    protected MLStatsNodeResponse nodeOperation(MLStatsNodeRequest request) {
        return createMLStatsNodeResponse(request.getMlStatsNodesRequest());
    }

    MLStatsNodeResponse createMLStatsNodeResponse(MLStatsNodesRequest mlStatsNodesRequest) {
        Map<MLNodeLevelStat, Object> statValues = new HashMap<>();
        MLStatsInput mlStatsInput = mlStatsNodesRequest.getMlStatsInput();
        // return node level stats
        if (mlStatsInput.getTargetStatLevels().contains(MLStatLevel.NODE)) {
            if (mlStatsInput.retrieveStat(MLNodeLevelStat.ML_JVM_HEAP_USAGE)) {
                long heapUsedPercent = jvmService.stats().getMem().getHeapUsedPercent();
                statValues.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, heapUsedPercent);
            }

            for (Enum statName : mlStats.getNodeStats().keySet()) {
                if (mlStatsInput.retrieveStat(statName)) {
                    statValues.put((MLNodeLevelStat) statName, mlStats.getStats().get(statName).getValue());
                }
            }
        }

        Map<FunctionName, MLAlgoStats> algorithmStats = new HashMap<>();
        // return algorithm level stats
        if (mlStatsInput.includeAlgoStats()) {
            for (FunctionName algoName : mlStats.getAllAlgorithms()) {
                if (mlStatsInput.retrieveStatsForAlgo(algoName)) {
                    Map<ActionName, MLActionStats> actionStatsMap = new HashMap<>();
                    for (Map.Entry<ActionName, MLActionStats> entry : mlStats.getAlgorithmStats(algoName).entrySet()) {
                        if (mlStatsInput.retrieveStatsForAction(entry.getKey())) {
                            actionStatsMap.put(entry.getKey(), entry.getValue());
                        }
                    }
                    algorithmStats.put(algoName, new MLAlgoStats(actionStatsMap));
                }
            }
        }

        Map<String, MLModelStats> modelStats = new HashMap<>();
        if (mlStatsInput.includeModelStats()) {
            CountDownLatch latch = new CountDownLatch(1);
            boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
            searchHiddenModels(ActionListener.wrap(hiddenModels -> {
                for (String modelId : mlStats.getAllModels()) {
                    if (isSuperAdmin || !hiddenModels.contains(modelId)) {
                        if (mlStatsInput.retrieveStatsForModel(modelId)) {
                            Map<ActionName, MLActionStats> actionStatsMap = new HashMap<>();
                            for (Map.Entry<ActionName, MLActionStats> entry : mlStats.getModelStats(modelId).entrySet()) {
                                if (mlStatsInput.retrieveStatsForAction(entry.getKey())) {
                                    actionStatsMap.put(entry.getKey(), entry.getValue());
                                }
                            }
                            modelStats.put(modelId, new MLModelStats(actionStatsMap));
                        }
                    }
                }
            }, e -> { log.error("Search Hidden model wasn't successful"); }), latch);
            // Wait for the asynchronous call to complete
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Handle interruption if necessary
                Thread.currentThread().interrupt();
            }
        }
        return new MLStatsNodeResponse(clusterService.localNode(), statValues, algorithmStats, modelStats);
    }

    @VisibleForTesting
    void searchHiddenModels(ActionListener<Set<String>> listener, CountDownLatch latch) {
        SearchRequest searchRequest = buildHiddenModelSearchRequest();
        // Use a try-with-resources block to ensure resources are properly released
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            // Wrap the listener to restore thread context before calling it
            ActionListener<Set<String>> internalListener = ActionListener.runAfter(listener, () -> {
                latch.countDown();
                threadContext.restore();
            });
            // Wrap the search response handler to handle success and failure cases
            // Notify the listener of any search failures
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                // Initialize the result set
                Set<String> result = new HashSet<>(response.getHits().getHits().length); // Set initial capacity to the number of hits

                // Iterate over the search hits and add their IDs to the result set
                for (SearchHit hit : response.getHits()) {
                    result.add(hit.getId());
                }
                // Notify the listener of the search results
                internalListener.onResponse(result);
            }, internalListener::onFailure);

            // Execute the search request asynchronously
            client.search(searchRequest, al);
        } catch (Exception e) {
            // Notify the listener of any unexpected errors
            listener.onFailure(e);
        }
    }

    private SearchRequest buildHiddenModelSearchRequest() {
        SearchRequest searchRequest = new SearchRequest(CommonValue.ML_MODEL_INDEX);
        // Build the query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder
            .filter(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MLModel.IS_HIDDEN_FIELD, true))
                    // Add the additional filter to exclude documents where "chunk_number" exists
                    .mustNot(QueryBuilders.existsQuery("chunk_number"))
            );
        searchRequest.source().query(boolQueryBuilder);
        // Specify the fields to include in the search results (only the "_id" field)
        // No fields to exclude
        searchRequest.source().fetchSource(new String[] { "_id" }, new String[] {});
        return searchRequest;
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
