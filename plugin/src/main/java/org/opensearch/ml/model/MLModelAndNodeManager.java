/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.Arrays;
import java.util.List;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.transport.load.LoadModelResponse;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;

/**
 * Manager class for ML models&nodes. It contains ML model auto reload operations etc.
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

    // private volatile Integer maxRetryTimeAutoReLoadModel;

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

        // maxRetryTimeAutoReLoadModel = ML_COMMONS_MODEL_AUTO_RELOAD_MAX_RETRY.get(settings);
        // clusterService
        // .getClusterSettings()
        // .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_RELOAD_MAX_RETRY,
        // it -> maxRetryTimeAutoReLoadModel = it);
    }

    public void autoReLoadModel() {
        // log.debug("enableAutoReLoadModel {} maxRetryTimeAutoReLoadModel {}", enableAutoReLoadModel,
        // maxRetryTimeAutoReLoadModel);

        log.debug("enableAutoReLoadModel {} ", enableAutoReLoadModel);

        // 如果不需要自动reload，什么都不做直接返回
        if (!enableAutoReLoadModel) {
            return;
        }

        // 先获取所有的node id，包括已经挂的节点
        String[] allNodeIds = nodeHelper.getAllNodeIds();
        log.debug("all node ids are: {}", Arrays.toString(allNodeIds));

        // 再获取所有合法节点，即没有挂掉的节点
        String[] eligibleNodeIds = nodeHelper.getEligibleNodeIds();
        log.debug("eligibleNode ids are: {}", Arrays.toString(eligibleNodeIds));

        // 上面两个数组比较获取属于集群但不合法节点， 然后获取这些节点的model deployment plan，依次把这些model一个个重新load
        String[] needReLoadModelNodeIds = null;
        if (allNodeIds.length != eligibleNodeIds.length) {  // 说明有挂掉的节点
            needReLoadModelNodeIds = Arrays.stream(allNodeIds).filter(m -> !List.of(eligibleNodeIds).contains(m)).toArray(String[]::new);
        }

        // 如果没有，说明集群中所有节点都没有挂掉，都在正常运行，则什么都不需做
        if (CollectionUtils.isEmpty(needReLoadModelNodeIds)) {
            log.debug("all nodes run successfully");
            return;
        }

        for (String needReLoadModelNodeId : needReLoadModelNodeIds) {
            DiscoveryNode node = nodeHelper.getNode(needReLoadModelNodeId);
            if (MLNodeUtils.isMLNode(node)) {
                try {
                    autoReLoadModelByNodeId(node.getId());
                } catch (Exception e) {
                    log.error("Failed to auto reload model by node id {}, the reason is: {}", needReLoadModelNodeId, e);
                    throw new RuntimeException(e);
                }
            }
        }
        // 还没确定是否要做重试retry
        // Callable<Void> callable = () -> {
        // doAutoReLoadModel();
        // return null;
        // };
        //
        // // 定义重试器
        // Retryer<Void> retryer = RetryerBuilder
        // .<Void>newBuilder()
        // .retryIfException() // 抛出runtime异常、checked异常时都会重试，但是抛出error不会重试。
        // .retryIfExceptionOfType(Error.class)// 只在抛出error重试
        // .withWaitStrategy(WaitStrategies.incrementingWait(0, TimeUnit.SECONDS, 0, TimeUnit.SECONDS))
        // .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetryTimeAutoReLoadModel))
        // .build();
        //
        // try {
        // retryer.call(callable);
        // } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
        // throw new RuntimeException("retry max time, always failure", e);
        // }
    }

    private void autoReLoadModelByNodeId(String nodeId) {
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
                log.error("ML Task id {} failed to parse, the reason is: {}", hit.getId(), e);
                throw new RuntimeException(e);
            }
        }
    }

    private void autoReLoadModelByNodeAndModelId(String nodeId, String modelId) {
        MLLoadModelRequest mlLoadModelRequest = new MLLoadModelRequest(modelId, new String[] { nodeId }, false, false);
        LoadModelResponse loadModelResponse = client.execute(MLLoadModelAction.INSTANCE, mlLoadModelRequest).actionGet();

        if (loadModelResponse == null) {
            throw new RuntimeException("Can't auto reload model " + modelId + " by node " + nodeId);
        }
    }
}
