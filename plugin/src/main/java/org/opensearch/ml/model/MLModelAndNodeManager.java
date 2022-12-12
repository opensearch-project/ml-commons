/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_MAX_RETRY;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import lombok.extern.log4j.Log4j2;

import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.threadpool.ThreadPool;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

/**
 * Manager class for ML models&nodes. It contains ML model auto reload operations etc.
 */
@Log4j2
public class MLModelAndNodeManager {

    public static final int TIMEOUT_IN_MILLIS = 5000;

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

    private volatile Integer maxRetryTimeAutoReLoadModel;

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

        maxRetryTimeAutoReLoadModel = ML_COMMONS_MODEL_AUTO_RELOAD_MAX_RETRY.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_RELOAD_MAX_RETRY, it -> maxRetryTimeAutoReLoadModel = it);
    }

    public void autoReLoadModel() {
        log.debug("enableAutoReLoadModel {} maxRetryTimeAutoReLoadModel {}", enableAutoReLoadModel, maxRetryTimeAutoReLoadModel);

        // 如果不需要自动reload，什么都不做直接返回
        if (!enableAutoReLoadModel) {
            return;
        }

        Callable<Void> callable = () -> {
            doAutoReLoadModel();
            return null;
        };

        // 定义重试器
        Retryer<Void> retryer = RetryerBuilder
            .<Void>newBuilder()
            .retryIfException() // 抛出runtime异常、checked异常时都会重试，但是抛出error不会重试。
            .retryIfExceptionOfType(Error.class)// 只在抛出error重试
            .withWaitStrategy(WaitStrategies.incrementingWait(0, TimeUnit.SECONDS, 0, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetryTimeAutoReLoadModel))
            .build();

        try {
            retryer.call(callable);
        } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
            throw new RuntimeException("retry max time, always failure", e);
        }
    }

    private void doAutoReLoadModel() {
        log.debug(Thread.currentThread().getName() + " -->model auto reload is not finished yet...");

        // 先获取所有的node id，包括已经挂的节点
        String[] allNodeIds = nodeHelper.getAllNodeIds();
        log.debug("all node ids are: {}", allNodeIds);

        // 再获取所有合法节点，即没有挂掉的节点
        String[] eligibleNodeIds = nodeHelper.getEligibleNodeIds();
        log.debug("eligibleNode ids are: {}", eligibleNodeIds);

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
                String[] modelIds = getAllModelIdsByNode(needReLoadModelNodeId);
                loadAllModelsByNode(needReLoadModelNodeId, modelIds, FunctionName.TEXT_EMBEDDING);
            }
        }
    }

    private String[] getAllModelIdsByNode(String nodeId) {
        // GetRequest getRequest = new GetRequest(ML_TASK_INDEX);
        //// client.searchTask(new SearchRequest()).actionGet()
        // try (ThreadContext.StoredContext context = client.threadPool().getThreadContext()
        // .stashContext()) {
        // client.get(getRequest, ActionListener.wrap(response -> {
        // if (response != null && response.isExists()) {
        // try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry,
        // response.getSourceAsBytesRef())) {
        // ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        // MLTask mlTask = MLTask.parse(parser);
        //
        // actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
        // } catch (Exception e) {
        // log.error("Failed to parse ml task" + response.getId(), e);
        // actionListener.onFailure(e);
        // }
        // } else {
        // actionListener.onFailure(
        // new MLResourceNotFoundException("Failed to find ML index " + ML_TASK_INDEX));
        // }
        // }, e -> {
        // if (e instanceof IndexNotFoundException) {
        // log.error("Failed to find ML index " + ML_TASK_INDEX, e);
        // actionListener.onFailure(new MLResourceNotFoundException(e));
        // } else {
        // log.error("Failed to get ML index " + ML_TASK_INDEX, e);
        // actionListener.onFailure(e);
        // }
        // }));
        // } catch (Exception e) {
        // log.error("Failed to get ML index " + ML_TASK_INDEX, e);
        // actionListener.onFailure(e);
        // }
        return new String[] {};
    }

    private void loadAllModelsByNode(String nodeId, String[] modelIds, FunctionName functionName) {

        return;
    }
}
