/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_CONTROLLER_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.controller.MLModelControllerDeleteAction;
import org.opensearch.ml.common.transport.controller.MLModelControllerDeleteRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteModelControllerTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {
    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;
    MLModelManager mlModelManager;
    MLModelCacheHelper mlModelCacheHelper;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeleteModelControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLModelManager mlModelManager,
        MLModelCacheHelper mlModelCacheHelper,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelControllerDeleteAction.NAME, transportService, actionFilters, MLModelControllerDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlModelManager = mlModelManager;
        this.mlModelCacheHelper = mlModelCacheHelper;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelControllerDeleteRequest modelControllerDeleteRequest = MLModelControllerDeleteRequest.fromActionRequest(request);
        String modelId = modelControllerDeleteRequest.getModelId();
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                modelAccessControlHelper
                    .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                        if (hasPermission) {
                            mlModelManager
                                .getModelController(
                                    modelId,
                                    ActionListener
                                        .wrap(
                                            modelController -> deleteModelControllerWithDeployedModel(modelId, wrappedListener),
                                            deleteException -> {
                                                log.error(deleteException);
                                                wrappedListener.onFailure(deleteException);
                                            }
                                        )
                                );
                        } else {
                            wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "User doesn't have privilege to perform this operation on this model controller, model ID: "
                                            + modelId,
                                        RestStatus.FORBIDDEN
                                    )
                                );
                        }
                    }, exception -> {
                        log
                            .error(
                                "Permission denied: Unable to delete the model controller with the provided model id {}. Details: {}",
                                modelId,
                                exception
                            );
                        wrappedListener.onFailure(exception);
                    }));
            }, e -> {
                log
                    .warn(
                        "Failed to find corresponding model during deleting the model controller. Now trying to delete the model controller alone. Model ID: "
                            + modelId
                    );
                mlModelManager
                    .getModelController(
                        modelId,
                        ActionListener
                            .wrap(modelController -> deleteModelControllerWithDeployedModel(modelId, wrappedListener), deleteException -> {
                                log.error(deleteException);
                                wrappedListener.onFailure(deleteException);
                            })
                    );
            }));
        } catch (Exception e) {
            log.error("Failed to delete model controller for model" + modelId, e);
            actionListener.onFailure(e);
        }
    }

    // This method is used to handle the condition if we need to undeploy the model controller before deleting it from the index or not.
    private void deleteModelControllerWithDeployedModel(String modelId, ActionListener<DeleteResponse> actionListener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            if (!ArrayUtils.isEmpty(mlModelCacheHelper.getWorkerNodes(modelId))) {
                log.info("Model has already been deployed in ML cache, need undeploy model controller before sending delete request.");
                String[] targetNodeIds = getAllNodes();
                MLUndeployModelControllerNodesRequest undeployModelControllerNodesRequest = new MLUndeployModelControllerNodesRequest(
                    targetNodeIds,
                    modelId
                );
                client
                    .execute(
                        MLUndeployModelControllerAction.INSTANCE,
                        undeployModelControllerNodesRequest,
                        ActionListener.runBefore(ActionListener.wrap(nodesResponse -> {
                            if (nodesResponse != null && isUndeployModelControllerSuccessOnAllNodes(nodesResponse)) {
                                log
                                    .info(
                                        "Successfully undeploy model controller from cache. Start to delete the model controller for model {}",
                                        modelId
                                    );
                                deleteModelController(modelId, actionListener);
                            } else {
                                String[] nodeIds = getUndeployModelControllerFailedNodesList(nodesResponse);
                                log
                                    .error(
                                        "Failed to undeploy model controller with model ID {} on following nodes {}, deletion is aborted. Please retry or undeploy the model manually and then perform the deletion.",
                                        modelId,
                                        Arrays.toString(nodeIds)
                                    );
                                actionListener
                                    .onFailure(
                                        new RuntimeException(
                                            "Failed to undeploy model controller with model ID "
                                                + modelId
                                                + " on following nodes "
                                                + Arrays.toString(nodeIds)
                                                + ", deletion is aborted. Please retry or undeploy the model manually and then perform the deletion."
                                        )
                                    );
                            }
                        }, e -> {
                            log
                                .error(
                                    "Failed to undeploy model controller from cache and delete the model controller for model {}",
                                    modelId,
                                    e
                                );
                            actionListener.onFailure(e);
                        }), context::restore)
                    );
            } else {
                deleteModelController(modelId, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to delete model controller", e);
            actionListener.onFailure(e);
        }
    }

    private void deleteModelController(String modelId, ActionListener<DeleteResponse> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_CONTROLLER_INDEX, modelId);
        client.delete(deleteRequest, new ActionListener<>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                log.info("Model controller for model {} successfully deleted from index, result: {}", modelId, deleteResponse.getResult());
                mlModelManager.updateModel(modelId, Map.of(MLModel.IS_MODEL_CONTROLLER_ENABLED_FIELD, false));
                actionListener.onResponse(deleteResponse);
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to delete model controller for model: " + modelId, e);
                actionListener.onFailure(e);
            }
        });
    }

    private boolean isUndeployModelControllerSuccessOnAllNodes(
        MLUndeployModelControllerNodesResponse undeployModelControllerNodesResponse
    ) {
        return undeployModelControllerNodesResponse.failures() == null || undeployModelControllerNodesResponse.failures().isEmpty();
    }

    private String[] getUndeployModelControllerFailedNodesList(
        MLUndeployModelControllerNodesResponse undeployModelControllerNodesResponse
    ) {
        if (undeployModelControllerNodesResponse == null) {
            return getAllNodes();
        } else {
            List<String> nodeIds = new ArrayList<>();
            for (FailedNodeException failedNodeException : undeployModelControllerNodesResponse.failures()) {
                nodeIds.add(failedNodeException.nodeId());
            }
            return nodeIds.toArray(new String[0]);
        }
    }

    private String[] getAllNodes() {
        Iterator<DiscoveryNode> iterator = clusterService.state().nodes().iterator();
        List<String> nodeIds = new ArrayList<>();
        while (iterator.hasNext()) {
            nodeIds.add(iterator.next().getId());
        }
        return nodeIds.toArray(new String[0]);
    }
}
