/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUndeployModelsAction extends HandledTransportAction<ActionRequest, MLUndeployModelsResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    Settings settings;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportUndeployModelsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLUndeployModelsAction.NAME, transportService, actionFilters, MLDeployModelRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.settings = settings;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUndeployModelsResponse> listener) {
        MLUndeployModelsRequest undeployModelsRequest = MLUndeployModelsRequest.fromActionRequest(request);
        String[] modelIds = undeployModelsRequest.getModelIds();
        String[] targetNodeIds = undeployModelsRequest.getNodeIds();

        if (modelAccessControlHelper.isModelAccessControlEnabled()) {
            // Only allow user undeploy one model if model access control enabled.
            if (modelIds == null || modelIds.length != 1) {
                throw new IllegalArgumentException("only support undeploy one model");
            }

            String modelId = modelIds[0];
            validateAccess(modelId, ActionListener.wrap(hasPermissionToUndeploy -> {
                if (hasPermissionToUndeploy) {
                    MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, modelIds);

                    client.execute(MLUndeployModelAction.INSTANCE, mlUndeployModelNodesRequest, ActionListener.wrap(r -> {
                        listener.onResponse(new MLUndeployModelsResponse(r));
                    }, e -> { listener.onFailure(e); }));
                } else {
                    listener.onFailure(new IllegalArgumentException("No permission to undeploy model " + modelId));
                }
            }, listener::onFailure));
            return;
        }

        MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, modelIds);

        client.execute(MLUndeployModelAction.INSTANCE, mlUndeployModelNodesRequest, ActionListener.wrap(r -> {
            listener.onResponse(new MLUndeployModelsResponse(r));
        }, listener::onFailure));
    }

    private void validateAccess(String modelId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
                Boolean isHidden = mlModel.getIsHidden();
                if (isHidden != null && isHidden) {
                    if (isSuperAdmin) {
                        listener.onResponse(true);
                    } else {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "User doesn't have privilege to perform this operation on this model",
                                    RestStatus.FORBIDDEN
                                )
                            );
                    }
                } else {
                    modelAccessControlHelper.validateModelGroupAccess(user, mlModel.getModelGroupId(), client, listener);
                }
            }, e -> {
                log.error("Failed to find Model", e);
                listener.onFailure(e);
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to undeploy ML model");
            listener.onFailure(e);
        }
    }

    // VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }

}
