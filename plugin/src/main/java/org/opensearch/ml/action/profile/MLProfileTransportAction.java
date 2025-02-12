/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Arrays;
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
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLProfileTransportAction extends
    TransportNodesAction<MLProfileRequest, MLProfileResponse, MLProfileNodeRequest, MLProfileNodeResponse> {
    private MLTaskManager mlTaskManager;
    private final JvmService jvmService;
    private final MLModelManager mlModelManager;

    private final Client client;

    /**
     * Constructor
     * @param threadPool ThreadPool to use
     * @param clusterService ClusterService
     * @param transportService TransportService
     * @param actionFilters Action Filters
     * @param mlTaskManager mlTaskCache object
     * @param environment OpenSearch Environment
     * @param mlModelManager ML model manager
     */
    @Inject
    public MLProfileTransportAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Environment environment,
        MLModelManager mlModelManager,
        Client client
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
        this.mlModelManager = mlModelManager;
        this.client = client;
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
        MLProfileInput profileInput = mlProfileRequest.getMlProfileInput();
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        Set<String> hiddenModels = Optional.ofNullable(mlProfileRequest.getHiddenModelIds()).orElse(Collections.emptySet());

        Map<String, MLTask> tasks = getTasks(profileInput, isSuperAdmin, hiddenModels);
        Map<String, MLModelProfile> models = getModels(profileInput, isSuperAdmin, hiddenModels);

        return new MLProfileNodeResponse(clusterService.localNode(), tasks, models);
    }

    private Map<String, MLTask> getTasks(MLProfileInput profileInput, boolean isSuperAdmin, Set<String> hiddenModels) {
        Map<String, MLTask> tasks = new HashMap<>();
        Arrays.stream(mlTaskManager.getAllTaskIds()).forEach(taskId -> {
            MLTask task = mlTaskManager.getMLTask(taskId);
            if (task != null && (isSuperAdmin || !hiddenModels.contains(task.getModelId()))) {
                if (profileInput.isReturnAllTasks() || profileInput.getTaskIds().contains(taskId)) {
                    tasks.put(taskId, task);
                }
            }
        });
        return tasks;
    }

    private Map<String, MLModelProfile> getModels(MLProfileInput profileInput, boolean isSuperAdmin, Set<String> hiddenModels) {
        Map<String, MLModelProfile> models = new HashMap<>();
        Arrays.stream(mlModelManager.getAllModelIds()).forEach(modelId -> {
            if (isSuperAdmin || !hiddenModels.contains(modelId)) {
                if (profileInput.isReturnAllModels() || profileInput.getModelIds().contains(modelId)) {
                    MLModelProfile modelProfile = mlModelManager.getModelProfile(modelId);
                    if (modelProfile != null) {
                        modelProfile.setIsHidden(hiddenModels.contains(modelId));
                        models.put(modelId, modelProfile);
                    }
                }
            }
        });
        return models;
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
