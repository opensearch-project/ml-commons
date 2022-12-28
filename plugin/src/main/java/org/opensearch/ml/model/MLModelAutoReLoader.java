/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_MAX_RETRY_TIMES;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.common.CommonValue.NODE_ID_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.annotations.VisibleForTesting;

/**
 * Manager class for ML models and nodes. It contains ML model auto reload operations etc.
 */
@Log4j2
public class MLModelAutoReLoader {
    private final Client client;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final DiscoveryNodeHelper nodeHelper;
    private volatile Boolean enableAutoReLoadModel;

    /**
     * constructor method， init all the params necessary for model auto reloading
     * @param clusterService
     * @param client
     * @param xContentRegistry
     * @param nodeHelper
     * @param settings
     */
    public MLModelAutoReLoader(
        ClusterService clusterService,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeHelper,
        Settings settings
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeHelper = nodeHelper;

        enableAutoReLoadModel = ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE, it -> enableAutoReLoadModel = it);
    }

    /**
     * the main method: model auto reloading
     */
    public void autoReLoadModel() {
        log.debug("enableAutoReLoadModel {} ", enableAutoReLoadModel);

        // if we don't need to reload automatically, just return without doing anything
        if (!enableAutoReLoadModel) {
            return;
        }

        // At opensearch startup, get local node id, if not ml node,we ignored, just return without doing anything
        String localNodeId = clusterService.localNode().getId();
        if (!MLNodeUtils.isMLNode(nodeHelper.getNode(localNodeId))) {
            return;
        }

        // According to the node id to get retry times, if more than the maxi retry times, don't need to retry
        // that the number of unsuccessful reload has reached the maximum number of times, do not need to reload
        Integer reTryTimes = getReTryTimes(localNodeId);
        if (reTryTimes > ML_MODEL_RELOAD_MAX_RETRY_TIMES) {
            log.debug("have exceeded max retry times, always failure");
        }

        // auto reload all models of this local node, if it fails, reTryTimes+1, if it succeeds, reTryTimes is cleared to 0
        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> autoReLoadModelByNodeId(localNodeId));

        try {
            completableFuture.get();
        } catch (Exception e) {
            throw new RuntimeException("Can't auto reload model");
        }
    }

    /**
     * auto reload all the models under the node id
     * the node must be a ml node
     * @param nodeId node id
     */
    @VisibleForTesting
    void autoReLoadModelByNodeId(String nodeId) {
        if (!isExistedIndex(ML_TASK_INDEX)) {
            return;
        }

        SearchRequest searchRequest = new SearchRequest(ML_TASK_INDEX);
        SearchResponse response = client.execute(SearchAction.INSTANCE, searchRequest).actionGet(5000);

        SearchHit[] hits = response.getHits().getHits();
        if (CollectionUtils.isEmpty(hits)) {
            return;
        }

        Integer reTryTimes = 0;
        for (SearchHit hit : hits) {
            try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hit.getSourceRef())) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                MLTask mlTask = MLTask.parse(parser);

                String workerNode = mlTask.getWorkerNode();
                MLTaskType mlTaskType = mlTask.getTaskType();
                MLTaskState mlTaskState = mlTask.getState();

                if (workerNode.contains(nodeId) && mlTaskType == MLTaskType.LOAD_MODEL && mlTaskState == MLTaskState.COMPLETED) {
                    autoReLoadModelByNodeAndModelId(nodeId, mlTask.getModelId());
                }
            } catch (RuntimeException | IOException e) {
                reTryTimes++;
                // Store the latest value of the reTryTimes and node id under the index ".plugins-ml-model-reload"
                saveLatestReTryTimes(nodeId, reTryTimes);
                log.error("Can't auto reload model in node id {} ,has try {} times\nThe reason is:{}", nodeId, reTryTimes, e.getMessage());
            }
        }

        // Store the latest value of the reTryTimes and node id under the index ".plugins-ml-model-reload"
        saveLatestReTryTimes(nodeId, reTryTimes);
    }

    /**
     *  auto reload 1 model under the node id
     * @param nodeId node id
     * @param modelId model id
     */
    @VisibleForTesting
    void autoReLoadModelByNodeAndModelId(String nodeId, String modelId) {
        MLLoadModelRequest mlLoadModelRequest = new MLLoadModelRequest(modelId, new String[] { nodeId }, false, false);
        client.execute(MLLoadModelAction.INSTANCE, mlLoadModelRequest).actionGet(5000);
    }

    /**
     * get retry times from the index ".plugins-ml-model-reload" by 1 ml node
     * @param nodeId the filter condition to query
     * @return retry times
     */
    @VisibleForTesting
    Integer getReTryTimes(String nodeId) {
        if (!isExistedIndex(ML_MODEL_RELOAD_INDEX)) {
            return 0;
        }

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(new String[] { MODEL_LOAD_RETRY_TIMES_FIELD }, null);
        QueryBuilder queryBuilder = QueryBuilders.idsQuery().addIds(nodeId);
        searchSourceBuilder.query(queryBuilder);
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(ML_MODEL_RELOAD_INDEX);

        try {
            SearchResponse response = client.execute(SearchAction.INSTANCE, searchRequest).actionGet(5000);

            SearchHit[] hits = response.getHits().getHits();
            if (CollectionUtils.isEmpty(hits)) {
                return 0;
            }

            if (hits.length != 1) {
                throw new RuntimeException("can't get retry times by " + nodeId);
            }

            Map<String, Object> sourceAsMap = hits[0].getSourceAsMap();
            return (Integer) sourceAsMap.get(MODEL_LOAD_RETRY_TIMES_FIELD);
        } catch (IndexNotFoundException e) {
            throw new IndexNotFoundException("index " + ML_MODEL_RELOAD_INDEX + " not found");
        }
    }

    /**
     * judge whether the index ".plugins-ml-model-reload" existed
     * @param indexName index name
     * @return true: existed. false: not existed
     */
    @VisibleForTesting
    boolean isExistedIndex(String indexName) {
        IndicesExistsRequest existsRequest = new IndicesExistsRequest(indexName);
        IndicesExistsResponse exists = client.execute(IndicesExistsAction.INSTANCE, existsRequest).actionGet(5000);

        return exists.isExists();
    }

    /**
     * save retry times
     * @param nodeId node id
     * @param reTryTimes actual retry times
     */
    @VisibleForTesting
    void saveLatestReTryTimes(String nodeId, Integer reTryTimes) {
        Map<String, Object> content = new HashMap<>();
        content.put(NODE_ID_FIELD, nodeId);
        content.put(MODEL_LOAD_RETRY_TIMES_FIELD, reTryTimes);

        IndexRequest indexRequest = new IndexRequest(ML_MODEL_RELOAD_INDEX);
        indexRequest.id(nodeId);
        indexRequest.source(content);
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        IndexResponse indexResponse = client.execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);

        if (indexResponse.status() == RestStatus.CREATED || indexResponse.status() == RestStatus.OK) {
            log.debug("node id:{} insert retry times successfully", nodeId);
        } else {
            throw new RuntimeException("can't insert retry times by " + nodeId);
        }
    }
}
