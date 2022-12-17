/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX_MAPPING;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_MAX_RETRY_TIMES;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLReloadModel.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.common.MLReloadModel.NODE_ID_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
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
import org.opensearch.action.update.UpdateAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLReloadModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.transport.load.LoadModelResponse;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.annotations.VisibleForTesting;

/**
 * Manager class for ML models and nodes. It contains ML model auto reload operations etc.
 */
@Log4j2
public class MLModelAndNodeManager {
    private final Client client;
    private final ClusterService clusterService;
    private final ThreadPool threadPool;
    private final NamedXContentRegistry xContentRegistry;
    private final ModelHelper modelHelper;
    private final DiscoveryNodeHelper nodeHelper;
    private final MLModelCacheHelper modelCacheHelper;
    private final MLStats mlStats;
    private final MLCircuitBreakerService mlCircuitBreakerService;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLTaskManager mlTaskManager;
    private final MLModelManager mlModelManager;
    private final MLEngine mlEngine;

    private volatile Boolean enableAutoReLoadModel;

    public MLModelAndNodeManager(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        NamedXContentRegistry xContentRegistry,
        ModelHelper modelHelper,
        DiscoveryNodeHelper nodeHelper,
        Settings settings,
        MLStats mlStats,
        MLCircuitBreakerService mlCircuitBreakerService,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        MLModelCacheHelper modelCacheHelper,
        MLEngine mlEngine
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.threadPool = threadPool;
        this.xContentRegistry = xContentRegistry;
        this.modelHelper = modelHelper;
        this.nodeHelper = nodeHelper;
        this.modelCacheHelper = modelCacheHelper;
        this.mlStats = mlStats;
        this.mlCircuitBreakerService = mlCircuitBreakerService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.mlEngine = mlEngine;

        enableAutoReLoadModel = ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE, it -> enableAutoReLoadModel = it);
    }

    public void autoReLoadModel() {
        log.debug("enableAutoReLoadModel {} ", enableAutoReLoadModel);

        // if we don't need to reload automatically, just return without doing anything
        if (!enableAutoReLoadModel) {
            return;
        }

        // At opensearch startup, get local node id, if not ml node,we ignored, just return without doing anything
        String localNodeId = clusterService.localNode().getId();
        DiscoveryNode node = nodeHelper.getNode(localNodeId);
        if (!MLNodeUtils.isMLNode(node)) {
            return;
        }

        // According to the node id to query the number of retries, if more than 2 (the maximum number of retries), we do not need to retry,
        // that the number of unsuccessful reload has reached the maximum number of times, do not need to reload
        Integer reTryTimes = 0;
        if (isExistedIndex(ML_MODEL_RELOAD_INDEX)) {
            reTryTimes = getReTryTimes(localNodeId);
        }
        if (reTryTimes > ML_MODEL_RELOAD_MAX_RETRY_TIMES) {
            throw new RuntimeException("have exceeded max retry times, always failure");
        }

        // auto reload all models of this local node, if it fails, reTryTimes+1, if it succeeds, reTryTimes is cleared to 0
        autoReLoadModelByNodeId(localNodeId);
    }

    @VisibleForTesting
    void autoReLoadModelByNodeId(String nodeId) {
        SearchRequest searchRequest = new SearchRequest(ML_TASK_INDEX);
        SearchResponse response = client.execute(SearchAction.INSTANCE, searchRequest).actionGet();

        if (response == null) {
            return;
        }
        SearchHits searchHits = response.getHits();
        if (searchHits == null) {
            return;
        }
        SearchHit[] hits = searchHits.getHits();
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
            } catch (Exception e) {
                reTryTimes++;
                // Store the latest value of the reTryTimes and node id under the index ".plugins-ml-model-reload"
                saveLatestReTryTimes(nodeId, reTryTimes);
                log.error("ML Task id {} failed to parse, the reason is: {}", hit.getId(), e);
                throw new RuntimeException(e);
            }
        }

        // Store the latest value of the reTryTimes and node id under the index ".plugins-ml-model-reload"
        saveLatestReTryTimes(nodeId, reTryTimes);
    }

    /**
     *
     * @param nodeId
     * @param modelId
     */
    @VisibleForTesting
    void autoReLoadModelByNodeAndModelId(String nodeId, String modelId) {
        MLLoadModelRequest mlLoadModelRequest = new MLLoadModelRequest(modelId, new String[] { nodeId }, false, false);
        LoadModelResponse loadModelResponse = client.execute(MLLoadModelAction.INSTANCE, mlLoadModelRequest).actionGet();

        if (loadModelResponse == null) {
            throw new RuntimeException("Can't auto reload model " + modelId + " by node " + nodeId);
        }
    }

    @VisibleForTesting
    Integer getReTryTimes(String nodeId) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(null, new String[] { MODEL_LOAD_RETRY_TIMES_FIELD });
        QueryBuilder queryBuilder = new TermQueryBuilder(NODE_ID_FIELD, nodeId);
        searchSourceBuilder.query(queryBuilder);
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(ML_MODEL_RELOAD_INDEX);
        SearchResponse response = client.execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet(5000);

        if (response == null) {
            throw new RuntimeException("can't get retry times by " + nodeId);
        }
        SearchHits searchHits = response.getHits();
        if (searchHits == null) {
            throw new RuntimeException("can't get retry times by " + nodeId);
        }
        SearchHit[] hits = searchHits.getHits();
        if (CollectionUtils.isEmpty(hits)) {
            throw new RuntimeException("can't get retry times by " + nodeId);
        }

        for (SearchHit hit : hits) {
            try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hit.getSourceRef())) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                MLReloadModel mlReloadModel = MLReloadModel.parse(parser);

                return mlReloadModel.getRetryTimes();
            } catch (Exception e) {
                log.error("node id:{} failed to parse, the reason is: {}", nodeId, e);
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("can't get retry times by " + nodeId);
    }

    @VisibleForTesting
    boolean isExistedIndex(String indexName) {
        IndicesExistsRequest existsRequest = new IndicesExistsRequest(indexName);
        IndicesExistsResponse exists = client.execute(IndicesExistsAction.INSTANCE, existsRequest).actionGet(5000);

        return exists.isExists();
    }

    @VisibleForTesting
    void saveLatestReTryTimes(String nodeId, Integer reTryTimes) {
        Map<String, Object> content = new HashMap<>();
        content.put(NODE_ID_FIELD, nodeId);
        content.put(MODEL_LOAD_RETRY_TIMES_FIELD, reTryTimes);

        if (isExistedIndex(ML_MODEL_RELOAD_INDEX)) {
            // update data under the index
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.index(ML_MODEL_RELOAD_INDEX);
            updateRequest.id(nodeId);
            updateRequest.doc(content);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            UpdateResponse updateResponse = client.execute(UpdateAction.INSTANCE, updateRequest).actionGet(5000);

            if (updateResponse.status() == RestStatus.CREATED) {
                log.debug("node id:{} update retry times successfully", nodeId);
            } else {
                throw new RuntimeException("can't update retry times by " + nodeId);
            }
        } else {
            // create index and store data
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(ML_MODEL_RELOAD_INDEX);
            createIndexRequest.mapping(ML_MODEL_RELOAD_INDEX_MAPPING);
            CreateIndexResponse createIndexResponse = client.execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet(5000);

            if (createIndexResponse.isAcknowledged()) {
                log.debug("create index:{} with its mapping successfully", ML_MODEL_RELOAD_INDEX);
            } else {
                throw new RuntimeException("can't create index:" + ML_MODEL_RELOAD_INDEX + " with its mapping");
            }

            IndexRequest indexRequest = new IndexRequest(ML_MODEL_RELOAD_INDEX);
            indexRequest.id(nodeId);
            indexRequest.source(content);
            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            IndexResponse indexResponse = client.execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);

            if (indexResponse.status() == RestStatus.CREATED) {
                log.debug("node id:{} insert retry times successfully", nodeId);
            } else {
                throw new RuntimeException("can't insert retry times by " + nodeId);
            }
        }
    }
}
