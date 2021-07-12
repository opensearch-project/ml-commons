/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.action.search.SearchTaskExecutionAction;
import org.opensearch.ml.common.transport.search.SearchTaskRequest;
import org.opensearch.ml.common.transport.search.SearchTaskResponse;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * SearchTaskRunner is responsible for running search tasks.
 */
@Log4j2
public class SearchTaskRunner extends MLTaskRunner {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;

    public SearchTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
    }

    /**
     * Run search
     *
     * @param request          SearchTaskRequest
     * @param transportService transport service
     * @param listener         Action listener
     */
    public void runSearch(SearchTaskRequest request, TransportService transportService, ActionListener<SearchTaskResponse> listener) {
        mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute prediction task locally
                log.info("execute search request {} locally on node {}", request.toString(), node.getId());
                startSearchTask(request, listener);
            } else {
                // Execute batch task remotely
                log.info("execute search request {} remotely on node {}", request.toString(), node.getId());
                transportService
                    .sendRequest(
                        node,
                        SearchTaskExecutionAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, SearchTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    /**
     * Start search task
     *
     * @param request  SearchTaskRequest
     * @param listener Action listener
     */
    public void startSearchTask(SearchTaskRequest request, ActionListener<SearchTaskResponse> listener) {
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .taskType(MLTaskType.SEARCHING)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
        threadPool.executor(TASK_THREAD_POOL).execute(() -> { search(mlTask, request, listener); });
    }

    private void search(MLTask mlTask, SearchTaskRequest request, ActionListener<SearchTaskResponse> listener) {
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);

        // all potential filters to the search query
        String modelId = request.getModelId();
        String name = request.getName();
        String format = request.getFormat();
        String algorithm = request.getAlgorithm();

        // build search request to search for models using filters
        BoolQueryBuilder query = new BoolQueryBuilder();
        if (!Strings.isNullOrEmpty(modelId)) {
            query.filter(new TermQueryBuilder(TASK_ID, modelId));
        }
        if (!Strings.isNullOrEmpty(name)) {
            query.filter(new TermQueryBuilder(MODEL_NAME, name));
        }
        if (!Strings.isNullOrEmpty(format)) {
            query.filter(new TermQueryBuilder(MODEL_FORMAT, format));
        }
        if (!Strings.isNullOrEmpty(algorithm)) {
            query.filter(new TermQueryBuilder(ALGORITHM, algorithm));
        }
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(query);
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MODEL }, searchSourceBuilder);

        // run search
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            List<Map<String, String>> models = new ArrayList<>();
            SearchHits searchHits = searchResponse.getHits();
            for (SearchHit searchHit : searchHits) {
                Map<String, Object> source = searchHit.getSourceAsMap();
                Map<String, String> modelDesc = new HashMap<>();
                modelDesc.put("modelId", (String) source.get(TASK_ID));
                modelDesc.put("name", (String) source.get(MODEL_NAME));
                modelDesc.put("format", (String) source.get(MODEL_FORMAT));
                modelDesc.put("algorithm", (String) source.get(ALGORITHM));
                models.add(modelDesc);
            }
            SearchTaskResponse response = SearchTaskResponse.builder().models(models.toString()).build();
            listener.onResponse(response);
        }, searchException -> {
            log.error("Search model failed", searchException);
            handleSearchFailure(mlTask, listener, searchException);
        }));
    }

    private void handleSearchFailure(MLTask mlTask, ActionListener<SearchTaskResponse> listener, Exception e) {
        handleMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
