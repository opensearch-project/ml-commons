/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.controller.MLCreateControllerAction;
import org.opensearch.ml.common.transport.controller.MLCreateControllerRequest;
import org.opensearch.ml.common.transport.controller.MLCreateControllerResponse;
import org.opensearch.ml.common.transport.controller.MLDeployControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
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
public class CreateControllerTransportAction extends HandledTransportAction<ActionRequest, MLCreateControllerResponse> {
    MLIndicesHandler mlIndicesHandler;
    Client client;
    MLModelManager mlModelManager;
    ClusterService clusterService;
    MLModelCacheHelper mlModelCacheHelper;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public CreateControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelCacheHelper mlModelCacheHelper,
        MLModelManager mlModelManager
    ) {
        super(MLCreateControllerAction.NAME, transportService, actionFilters, MLCreateControllerRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.mlModelCacheHelper = mlModelCacheHelper;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateControllerResponse> actionListener) {
        MLCreateControllerRequest createControllerRequest = MLCreateControllerRequest.fromActionRequest(request);
        MLController controller = createControllerRequest.getControllerInput();
        String modelId = controller.getModelId();
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLCreateControllerResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                FunctionName functionName = mlModel.getAlgorithm();
                Boolean isHidden = mlModel.getIsHidden();
                if (functionName == TEXT_EMBEDDING || functionName == REMOTE) {
                    modelAccessControlHelper
                        .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                            if (hasPermission) {
                                if (mlModel.getModelState() != MLModelState.DEPLOYING) {
                                    indexAndCreateController(mlModel, controller, wrappedListener);
                                } else {
                                    String errorMessage =
                                        "Creating a model controller during its corresponding model in DEPLOYING state is not allowed, please either create the model controller after it is deployed or before deploying it.";
                                    errorMessage = getErrorMessage(errorMessage, modelId, isHidden);
                                    log.error(errorMessage);
                                    wrappedListener.onFailure(new OpenSearchStatusException(errorMessage, RestStatus.CONFLICT));
                                }
                            } else {
                                String errorMessage = "User doesn't have privilege to perform this operation on this model controller.";
                                errorMessage = getErrorMessage(errorMessage, modelId, isHidden);
                                log.error(errorMessage);
                                wrappedListener.onFailure(new OpenSearchStatusException(errorMessage, RestStatus.FORBIDDEN));
                            }
                        }, exception -> {
                            log
                                .error(
                                    getErrorMessage(
                                        "Permission denied: Unable to create the model controller. Details: {}",
                                        modelId,
                                        isHidden
                                    ),
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
                            "Failed to find model to create the corresponding model controller with the provided model ID",
                            RestStatus.NOT_FOUND
                        )
                    )
            ));
        } catch (Exception e) {
            log.error("Failed to create model controller", e);
            actionListener.onFailure(e);
        }
    }

    private void indexAndCreateController(
        MLModel mlModel,
        MLController controller,
        ActionListener<MLCreateControllerResponse> actionListener
    ) {
        Boolean isHidden = mlModel.getIsHidden();
        mlIndicesHandler.initMLControllerIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                actionListener.onFailure(new RuntimeException("Failed to create model controller index."));
                return;
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<IndexResponse> indexResponseListener = ActionListener.wrap(indexResponse -> {
                    String modelId = indexResponse.getId();
                    MLCreateControllerResponse response = new MLCreateControllerResponse(modelId, indexResponse.getResult().name());
                    String errorMessage = getErrorMessage("Model controller saved into index, result:{}", modelId, isHidden);
                    log.info(errorMessage, indexResponse.getResult());
                    if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                        mlModelManager.updateModel(modelId, isHidden, Map.of(MLModel.IS_CONTROLLER_ENABLED_FIELD, true));
                    }
                    if (!ArrayUtils.isEmpty(mlModelCacheHelper.getWorkerNodes(modelId))) {
                        log
                            .info(
                                getErrorMessage(
                                    "The model is deployed. Start to deploy the model controller into cache.",
                                    modelId,
                                    isHidden
                                )
                            );
                        String[] targetNodeIds = mlModelManager.getWorkerNodes(modelId, mlModel.getAlgorithm());
                        MLDeployControllerNodesRequest deployControllerNodesRequest = new MLDeployControllerNodesRequest(
                            targetNodeIds,
                            controller.getModelId()
                        );
                        client
                            .execute(MLDeployControllerAction.INSTANCE, deployControllerNodesRequest, ActionListener.wrap(nodesResponse -> {
                                if (nodesResponse != null && isDeployControllerSuccessOnAllNodes(nodesResponse)) {
                                    log
                                        .info(
                                            getErrorMessage(
                                                "Successfully created model controller and deployed it into cache",
                                                modelId,
                                                isHidden
                                            )
                                        );
                                    actionListener.onResponse(response);
                                } else {
                                    String[] nodeIds = getDeployControllerFailedNodesList(nodesResponse);
                                    String msg =
                                        "Successfully created the model controller index, but deployment of the model controller to the cache failed on the following nodes "
                                            + Arrays.toString(nodeIds)
                                            + ". Please retry.";
                                    msg = getErrorMessage(msg, modelId, isHidden);

                                    actionListener.onFailure(new RuntimeException(msg));
                                    log.error(msg);
                                }
                            }, e -> {
                                log.error("Failed to deploy model controller for the given model", e);
                                actionListener.onFailure(e);
                            }));
                    } else {
                        actionListener.onResponse(response);
                    }
                }, actionListener::onFailure);

                IndexRequest indexRequest = new IndexRequest(ML_CONTROLLER_INDEX).id(controller.getModelId());
                indexRequest.source(controller.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.runBefore(indexResponseListener, context::restore));
            } catch (Exception e) {
                log.error("Failed to save model controller", e);
                actionListener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to init model controller index", e);
            actionListener.onFailure(e);
        }));
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
