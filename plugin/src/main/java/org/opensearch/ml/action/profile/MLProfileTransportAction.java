/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class MLProfileTransportAction extends
    TransportNodesAction<MLProfileRequest, MLProfileResponse, MLProfileNodeRequest, MLProfileNodeResponse> {
    private MLTaskManager mlTaskManager;
    private final JvmService jvmService;

    /**
     * Constructor
     *
     * @param threadPool ThreadPool to use
     * @param clusterService ClusterService
     * @param transportService TransportService
     * @param actionFilters Action Filters
     * @param mlTaskManager mlTaskCache object
     * @param environment OpenSearch Environment
     */
    @Inject
    public MLProfileTransportAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Environment environment
    ) {
        super(
            MLProfileAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLProfileRequest::new,
            MLProfileNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLProfileNodeResponse.class
        );
        this.mlTaskManager = mlTaskManager;
        this.jvmService = new JvmService(environment.settings());
    }

    @Override
    protected MLProfileResponse newResponse(
        MLProfileRequest request,
        List<MLProfileNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLProfileResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLProfileNodeRequest newNodeRequest(MLProfileRequest request) {
        return new MLProfileNodeRequest(request);
    }

    @Override
    protected MLProfileNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLProfileNodeResponse(in);
    }

    @Override
    protected MLProfileNodeResponse nodeOperation(MLProfileNodeRequest request) {
        return createMLProfileNodeResponse(request.getMlProfileRequest());
    }

    private MLProfileNodeResponse createMLProfileNodeResponse(MLProfileRequest mlProfileRequest) {
        log.info("Calculating ml profile response on node id:{}", clusterService.localNode().getId());
        Map<String, MLTask> mlLocalTasks = new HashMap<>();
        MLProfileInput mlProfileInput = mlProfileRequest.getMlProfileInput();
        mlTaskManager.getTaskCaches().forEach((key, value) -> {
            if (mlProfileInput.isReturnAllMLTasks() || (!mlProfileInput.emptyTasks() && mlProfileInput.getTaskIds().contains(key))) {
                log.info("Runtime task profile is found for model {}", value.getMlTask().getModelId());
                mlLocalTasks.put(key, value.getMlTask());
                return;
            }
            if (!mlProfileInput.emptyModels() && mlProfileInput.getModelIds().contains(value.getMlTask().getModelId())) {
                log.info("Runtime task profile is found for model {}", value.getMlTask().getModelId());
                mlLocalTasks.put(key, value.getMlTask());
            }
        });

        return new MLProfileNodeResponse(clusterService.localNode(), mlLocalTasks);
    }
}
