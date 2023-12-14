package org.opensearch.ml.action.controller;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_CONTROLLER_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
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
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerRequest;
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerResponse;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLModelController;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CreateModelControllerTransportAction extends HandledTransportAction<ActionRequest, MLCreateModelControllerResponse> {
    MLIndicesHandler mlIndicesHandler;
    Client client;
    MLModelManager mlModelManager;
    ClusterService clusterService;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public CreateModelControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelManager mlModelManager
    ) {
        super(MLCreateModelControllerAction.NAME, transportService, actionFilters, MLCreateModelControllerRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateModelControllerResponse> actionListener) {
        MLCreateModelControllerRequest createModelControllerRequest = MLCreateModelControllerRequest.FromActionRequest(request);
        MLModelController mlModelController = createModelControllerRequest.getModelControllerInput();
        String modelId = mlModelController.getModelId();
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLCreateModelControllerResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                FunctionName functionName = mlModel.getAlgorithm();
                if (functionName == TEXT_EMBEDDING || functionName == REMOTE) {
                    modelAccessControlHelper
                        .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                            if (hasPermission) {
                                String[] targetNodeIds = getAllNodes();
                                MLDeployModelControllerNodesRequest deployModelControllerNodesRequest =
                                    new MLDeployModelControllerNodesRequest(targetNodeIds, mlModelController.getModelId());
                                indexAndCreateModelController(mlModelController, deployModelControllerNodesRequest, wrappedListener);
                            } else {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User doesn't have privilege to perform this operation on this model, model ID " + modelId,
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
                            "Failed to find model to create the corresponding model controller with the provided model id: " + modelId,
                            RestStatus.NOT_FOUND
                        )
                    )
            ));
        } catch (Exception e) {
            log.error("Failed to create model controller for " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    private void indexAndCreateModelController(
        MLModelController mlModelController,
        MLDeployModelControllerNodesRequest deployModelControllerNodesRequest,
        ActionListener<MLCreateModelControllerResponse> actionListener
    ) {
        log.info("Indexing the ML model controller into system index");
        mlIndicesHandler.initMLModelControllerIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                actionListener.onFailure(new RuntimeException("No response to create ML model controller index"));
                return;
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<IndexResponse> indexResponseListener = ActionListener.wrap(indexResponse -> {
                    String modelId = indexResponse.getId();
                    MLCreateModelControllerResponse response = new MLCreateModelControllerResponse(
                        modelId,
                        indexResponse.getResult().name()
                    );
                    log.info("Model controller for model id {} saved into index, result:{}", modelId, indexResponse.getResult());
                    mlModelManager.updateModel(modelId, Map.of(MLModel.IS_MODEL_CONTROLLER_ENABLED_FIELD, true));
                    client
                        .execute(
                            MLDeployModelControllerAction.INSTANCE,
                            deployModelControllerNodesRequest,
                            ActionListener.wrap(strResponse -> {
                                log.info("Successfully deploy model controller for model {}", modelId);
                                actionListener.onResponse(response);
                            }, e -> {
                                log.error("Failed to deploy model controller for model: {}" + modelId, e);
                                actionListener.onFailure(e);
                            })
                        );
                }, actionListener::onFailure);

                IndexRequest indexRequest = new IndexRequest(ML_MODEL_CONTROLLER_INDEX).id(mlModelController.getModelId());
                indexRequest
                    .source(mlModelController.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.runBefore(indexResponseListener, context::restore));
            } catch (Exception e) {
                log.error("Failed to save ML model controller", e);
                actionListener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to init ML model controller index", e);
            actionListener.onFailure(e);
        }));
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
