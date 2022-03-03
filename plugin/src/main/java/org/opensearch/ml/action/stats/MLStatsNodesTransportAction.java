/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.ml.stats.InternalStatNames;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class MLStatsNodesTransportAction extends
    TransportNodesAction<MLStatsNodesRequest, MLStatsNodesResponse, MLStatsNodeRequest, MLStatsNodeResponse> {
    private MLStats mlStats;
    private final JvmService jvmService;

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
        Environment environment
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

    private MLStatsNodeResponse createMLStatsNodeResponse(MLStatsNodesRequest mlStatsNodesRequest) {
        Map<String, Object> statValues = new HashMap<>();
        Set<String> statsToBeRetrieved = mlStatsNodesRequest.getStatsToBeRetrieved();
        boolean retrieveAllStats = mlStatsNodesRequest.isRetrieveAllStats();

        if (retrieveAllStats || statsToBeRetrieved.contains(InternalStatNames.JVM_HEAP_USAGE.getName())) {
            long heapUsedPercent = jvmService.stats().getMem().getHeapUsedPercent();
            statValues.put(InternalStatNames.JVM_HEAP_USAGE.getName(), heapUsedPercent);
        }

        for (String statName : mlStats.getNodeStats().keySet()) {
            if (retrieveAllStats || statsToBeRetrieved.contains(statName)) {
                statValues.put(statName, mlStats.getStats().get(statName).getValue());
            }
        }

        return new MLStatsNodeResponse(clusterService.localNode(), statValues);
    }
}
