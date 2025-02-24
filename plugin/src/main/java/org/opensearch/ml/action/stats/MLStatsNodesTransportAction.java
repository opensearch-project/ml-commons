/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.FunctionName;
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
import org.opensearch.transport.client.Client;

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

    private MLStatsNodeResponse createMLStatsNodeResponse(MLStatsNodesRequest request) {
        Map<MLNodeLevelStat, Object> nodeLevelStats = getNodeLevelStats(request.getMlStatsInput());
        Map<FunctionName, MLAlgoStats> algoStats = getAlgorithmStats(request.getMlStatsInput());
        Map<String, MLModelStats> modelStats = getModelStats(request);
        return new MLStatsNodeResponse(clusterService.localNode(), nodeLevelStats, algoStats, modelStats);
    }

    private Map<MLNodeLevelStat, Object> getNodeLevelStats(MLStatsInput input) {
        Map<MLNodeLevelStat, Object> stats = new HashMap<>();
        if (input.getTargetStatLevels().contains(MLStatLevel.NODE)) {
            if (input.retrieveStat(MLNodeLevelStat.ML_JVM_HEAP_USAGE)) {
                long heapUsedPercent = jvmService.stats().getMem().getHeapUsedPercent();
                stats.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, heapUsedPercent);
            }
            mlStats.getNodeStats().forEach((statName, stat) -> {
                if (input.retrieveStat(statName)) {
                    stats.put((MLNodeLevelStat) statName, stat.getValue());
                }
            });
        }
        return stats;
    }

    private Map<FunctionName, MLAlgoStats> getAlgorithmStats(MLStatsInput input) {
        Map<FunctionName, MLAlgoStats> stats = new HashMap<>();
        if (input.includeAlgoStats()) {
            for (FunctionName algo : mlStats.getAllAlgorithms()) {
                if (input.retrieveStatsForAlgo(algo)) {
                    Map<ActionName, MLActionStats> actionStats = collectActionStats(mlStats.getAlgorithmStats(algo), input);
                    stats.put(algo, new MLAlgoStats(actionStats));
                }
            }
        }
        return stats;
    }

    private Map<ActionName, MLActionStats> collectActionStats(Map<ActionName, MLActionStats> stats, MLStatsInput input) {
        Map<ActionName, MLActionStats> filteredStats = new HashMap<>();
        stats.forEach((action, stat) -> {
            if (input.retrieveStatsForAction(action)) {
                filteredStats.put(action, stat);
            }
        });
        return filteredStats;
    }

    private Map<String, MLModelStats> getModelStats(MLStatsNodesRequest request) {
        Map<String, MLModelStats> stats = new HashMap<>();
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        Set<String> hiddenModels = Optional.ofNullable(request.getHiddenModelIds()).orElse(Collections.emptySet());

        for (String modelId : mlStats.getAllModels()) {
            if (isSuperAdmin || !hiddenModels.contains(modelId)) {
                if (request.getMlStatsInput().retrieveStatsForModel(modelId)) {
                    Map<ActionName, MLActionStats> actionStats = collectActionStats(
                        mlStats.getModelStats(modelId),
                        request.getMlStatsInput()
                    );
                    boolean isHidden = hiddenModels.contains(modelId);
                    stats.put(modelId, new MLModelStats(actionStats, isHidden));
                }
            }
        }
        return stats;
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
