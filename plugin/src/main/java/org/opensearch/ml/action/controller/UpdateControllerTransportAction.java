/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.transport.controller.MLDeployControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesResponse;
import org.opensearch.ml.common.transport.controller.MLUpdateControllerAction;
import org.opensearch.ml.common.transport.controller.MLUpdateControllerRequest;
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
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UpdateControllerTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;
    MLModelManager mlModelManager;
    MLModelCacheHelper mlModelCacheHelper;
    ClusterService clusterService;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public UpdateControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelCacheHelper mlModelCacheHelper,
        MLModelManager mlModelManager
    ) {
        super(MLUpdateControllerAction.NAME, transportService, actionFilters, MLUpdateControllerRequest::new);
        this.client = client;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.mlModelCacheHelper = mlModelCacheHelper;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLUpdateControllerRequest updateControllerRequest = MLUpdateControllerRequest.fromActionRequest(request);
        MLController updateControllerInput = updateControllerRequest.getUpdateControllerInput();
        String modelId = updateControllerInput.getModelId();
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                FunctionName functionName = mlModel.getAlgorithm();
                if (functionName == TEXT_EMBEDDING || functionName == REMOTE) {
                    modelAccessControlHelper
                        .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                            if (hasPermission) {
                                mlModelManager.getController(modelId, ActionListener.wrap(controller -> {
                                    boolean isDeployRequiredAfterUpdate = controller.isDeployRequiredAfterUpdate(updateControllerInput);
                                    controller.update(updateControllerInput);
                                    updateController(mlModel, controller, isDeployRequiredAfterUpdate, wrappedListener);
                                }, e -> {
                                    if (mlModel.getIsControllerEnabled() == null || !mlModel.getIsControllerEnabled()) {
                                        wrappedListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    "Model controller haven't been created for the model. Consider calling create model controller api instead. Model ID: "
                                                        + modelId,
                                                    RestStatus.CONFLICT
                                                )
                                            );
                                        log.error("Model controller haven't been created for the model: " + modelId, e);
                                    } else {
                                        log.error(e);
                                        wrappedListener.onFailure(e);
                                    }
                                }));
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
                                    "Permission denied: Unable to create the model controller for the model with ID {}. Details: {}",
                                    modelId,
                                    exception
                                );
                            wrappedListener.onFailure(exception);
                        }));
                } else {
                    wrappedListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Creating model controller on this operation on the function category "
                                    + functionName.toString()
                                    + " is not supported.",
                                RestStatus.FORBIDDEN
                            )
                        );
                }
            },
                e -> wrappedListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Failed to find model to create the corresponding model controller with the provided model ID: " + modelId,
                            RestStatus.NOT_FOUND
                        )
                    )
            ));
        } catch (Exception e) {
            log.error("Failed to create model controller for " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    private void updateController(
        MLModel mlModel,
        MLController controller,
        boolean isDeployRequiredAfterUpdate,
        ActionListener<UpdateResponse> actionListener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            String modelId = mlModel.getModelId();
            ActionListener<UpdateResponse> updateResponseListener = ActionListener.wrap(updateResponse -> {
                if (updateResponse != null && updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                    log
                        .info(
                            "Model controller for model {} successfully updated to index, result: {}",
                            modelId,
                            updateResponse.getResult()
                        );
                    if (!ArrayUtils.isEmpty(mlModelCacheHelper.getWorkerNodes(modelId)) && isDeployRequiredAfterUpdate) {
                        log
                            .info(
                                "Model {} is deployed and the user rate limiter config is constructable. Start to deploy the model controller into cache.",
                                modelId
                            );
                        String[] targetNodeIds = mlModelManager.getWorkerNodes(modelId, mlModel.getAlgorithm());
                        MLDeployControllerNodesRequest deployControllerNodesRequest = new MLDeployControllerNodesRequest(
                            targetNodeIds,
                            modelId
                        );
                        client
                            .execute(MLDeployControllerAction.INSTANCE, deployControllerNodesRequest, ActionListener.wrap(nodesResponse -> {
                                if (nodesResponse != null && isDeployControllerSuccessOnAllNodes(nodesResponse)) {
                                    log.info("Successfully update model controller and deploy it into cache with model ID {}", modelId);
                                    actionListener.onResponse(updateResponse);
                                } else {
                                    String[] nodeIds = getDeployControllerFailedNodesList(nodesResponse);
                                    log
                                        .error(
                                            "Successfully update model controller index with model ID {} but deploy model controller to cache was failed on following nodes {}, please retry.",
                                            modelId,
                                            Arrays.toString(nodeIds)
                                        );
                                    actionListener
                                        .onFailure(
                                            new RuntimeException(
                                                "Successfully update model controller index with model ID "
                                                    + modelId
                                                    + " but deploy model controller to cache was failed on following nodes "
                                                    + Arrays.toString(nodeIds)
                                                    + ", please retry."
                                            )
                                        );
                                }
                            }, e -> {
                                log.error("Failed to deploy model controller for model: {}" + modelId, e);
                                actionListener.onFailure(e);
                            }));
                    } else {
                        actionListener.onResponse(updateResponse);
                    }
                } else if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                    // The update response returned an unexpected status may indicate a failed
                    // update
                    log
                        .warn(
                            "Update model controller for model {} got a result status other than update, result status: {}",
                            modelId,
                            updateResponse.getResult()
                        );
                    actionListener.onResponse(updateResponse);
                } else {
                    log.error("Failed to update model controller with model ID: " + modelId);
                    actionListener.onFailure(new RuntimeException("Failed to update model controller with model ID: " + modelId));
                }
            }, actionListener::onFailure);
            UpdateRequest updateRequest = new UpdateRequest(ML_CONTROLLER_INDEX, modelId);
            updateRequest.doc(controller.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS));
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.update(updateRequest, ActionListener.runBefore(updateResponseListener, context::restore));
        } catch (Exception e) {
            log.error("Failed to update model controller.", e);
            actionListener.onFailure(e);
        }
    }

    private boolean isDeployControllerSuccessOnAllNodes(MLDeployControllerNodesResponse deployControllerNodesResponse) {
        return deployControllerNodesResponse.failures() == null || deployControllerNodesResponse.failures().isEmpty();
    }

    private String[] getDeployControllerFailedNodesList(MLDeployControllerNodesResponse deployControllerNodesResponse) {
        if (deployControllerNodesResponse == null) {
            return getAllNodes();
        } else {
            List<String> nodeIds = new ArrayList<>();
            for (FailedNodeException failedNodeException : deployControllerNodesResponse.failures()) {
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
