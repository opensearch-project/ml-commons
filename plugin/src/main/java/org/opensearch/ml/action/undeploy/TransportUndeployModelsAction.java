/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;

import java.util.Set;
import java.util.concurrent.CountDownLatch;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
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
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUndeployModelsAction extends HandledTransportAction<ActionRequest, MLUndeployModelsResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelManager mlModelManager;
    MLStats mlStats;

    private volatile boolean allowCustomDeploymentPlan;

    ModelAccessControlHelper modelAccessControlHelper;

    private volatile Boolean filterByEnabled;

    @Inject
    public TransportUndeployModelsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelManager mlModelManager,
        MLStats mlStats,
        Settings settings,
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
        this.mlStats = mlStats;
        this.modelAccessControlHelper = modelAccessControlHelper;
        allowCustomDeploymentPlan = ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN, it -> allowCustomDeploymentPlan = it);
        filterByEnabled = ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED, it -> filterByEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUndeployModelsResponse> listener) {
        MLUndeployModelsRequest undeployModelsRequest = MLUndeployModelsRequest.fromActionRequest(request);
        String[] modelIds = undeployModelsRequest.getModelIds();
        String[] targetNodeIds = undeployModelsRequest.getNodeIds();

        if (filterByEnabled) {
            // Only allow user undeploy one model if filter by backend role enabled.
            if (modelIds == null || modelIds.length != 1) {
                throw new IllegalArgumentException("only support undeploy one model");
            }

            String modelId = modelIds[0];
            String[] finalModelIds = modelIds;
            validateAccess(modelId, ActionListener.wrap(hasPermissionToUndeploy -> {
                if (hasPermissionToUndeploy == true) {
                    MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, finalModelIds);

                    client
                        .execute(
                            MLUndeployModelAction.INSTANCE,
                            mlUndeployModelNodesRequest,
                            ActionListener
                                .wrap(r -> { listener.onResponse(new MLUndeployModelsResponse(r)); }, e -> { listener.onFailure(e); })
                        );
                } else {
                    listener.onFailure(new IllegalArgumentException("No permission to undeploy model " + modelId));
                }
            }, e -> { listener.onFailure(e); }));
            return;
        }

        MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, modelIds);

        client
            .execute(
                MLUndeployModelAction.INSTANCE,
                mlUndeployModelNodesRequest,
                ActionListener.wrap(r -> { listener.onResponse(new MLUndeployModelsResponse(r)); }, e -> { listener.onFailure(e); })
            );
    }

    private void validateAccess(String modelId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager
                .getModel(
                    modelId,
                    null,
                    excludes,
                    ActionListener
                        .wrap(
                            mlModel -> {
                                modelAccessControlHelper.validateModelGroupAccess(user, mlModel.getModelGroupId(), client, listener);
                            },
                            e -> {
                                log.error("Failed to find Model", e);
                                listener.onFailure(e);
                            }
                        )
                );
        } catch (Exception e) {
            log.error("Failed to undeploy ML model");
            listener.onFailure(e);
        }
    }

    private void validateAccess(String modelId, Set<String> invalidAccessModels, User user, String[] excludes, CountDownLatch latch) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                modelAccessControlHelper
                    .validateModelGroupAccess(
                        user,
                        mlModel.getModelGroupId(),
                        client,
                        new LatchedActionListener<>(ActionListener.wrap(access -> {
                            if (!access) {
                                invalidAccessModels.add(modelId);
                            }
                        }, e -> {
                            log.error("Failed to Validate Access for ModelID " + modelId, e);
                            invalidAccessModels.add(modelId);
                        }), latch)
                    );
            }, e -> {
                log.error("Failed to find Model", e);
                latch.countDown();
            }));
        } catch (Exception e) {
            log.error("Failed to undeploy ML model");
            throw e;
        }
    }
}
