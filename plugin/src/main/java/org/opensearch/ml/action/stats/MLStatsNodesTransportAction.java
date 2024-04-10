/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
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
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLStatsNodesTransportAction extends
    TransportNodesAction<MLStatsNodesRequest, MLStatsNodesResponse, MLStatsNodeRequest, MLStatsNodeResponse> {
    private MLStats mlStats;
    private final JvmService jvmService;

    Client client;

    MLModelManager mlModelManager;

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

        CountDownLatch latch = new CountDownLatch(mlStats.getAllModels().length);
        Map<String, MLModelStats> modelStats = new HashMap<>();
        // return model level stats
        if (mlStatsInput.includeModelStats()) {
            for (String modelId : mlStats.getAllModels()) {
                // Add action listener to retrieve model details
                validateAccess(modelId, ActionListener.wrap(hasPermissionToShowStat -> {
                    if (hasPermissionToShowStat) {
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
                    // Count down the latch after each asynchronous call completes
                    latch.countDown();
                }, e -> {
                    // Handle failure case here
                    // For example, log the error
                    log.error("Failed to retrieve model details for model ID: " + modelId, e);
                    // Count down the latch even in case of failure to ensure proper synchronization
                    latch.countDown();
                }));
            }
            // Wait for all asynchronous calls to complete
            try {
                latch.await();
            } catch (InterruptedException e) {
                // Handle interruption if necessary
                Thread.currentThread().interrupt();
            }
        }
        return new MLStatsNodeResponse(clusterService.localNode(), statValues, algorithmStats, modelStats);
    }

    private void validateAccess(String modelId, ActionListener<Boolean> listener) {
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
                Boolean isHidden = mlModel.getIsHidden();
                if (isHidden != null && isHidden) {
                    if (isSuperAdmin) {
                        listener.onResponse(true);
                    } else {
                        listener.onResponse(false);
                    }
                } else {
                    listener.onResponse(true);
                }
            }, e -> {
                log.error("Failed to find Model", e);
                listener.onFailure(e);
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to find ML model");
            listener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
