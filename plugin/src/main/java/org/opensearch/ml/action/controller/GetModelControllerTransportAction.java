/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_CONTROLLER_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.controller.MLModelController;
import org.opensearch.ml.common.transport.controller.MLModelControllerGetAction;
import org.opensearch.ml.common.transport.controller.MLModelControllerGetRequest;
import org.opensearch.ml.common.transport.controller.MLModelControllerGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetModelControllerTransportAction extends HandledTransportAction<ActionRequest, MLModelControllerGetResponse> {
    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;
    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public GetModelControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelControllerGetAction.NAME, transportService, actionFilters, MLModelControllerGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelControllerGetResponse> actionListener) {
        MLModelControllerGetRequest modelControllerGetRequest = MLModelControllerGetRequest.fromActionRequest(request);
        String modelId = modelControllerGetRequest.getModelId();
        FetchSourceContext fetchSourceContext = getFetchSourceContext(modelControllerGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_MODEL_CONTROLLER_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModelControllerGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModelController modelController = MLModelController.parse(parser);
                        mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                            modelAccessControlHelper
                                .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                                    if (hasPermission) {
                                        wrappedListener
                                            .onResponse(MLModelControllerGetResponse.builder().mlModelController(modelController).build());
                                    } else {
                                        wrappedListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    "User doesn't have privilege to perform this operation on this model, model ID: "
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
                        },
                            e -> wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "Failed to find model to get the corresponding model controller with the provided model ID: "
                                            + modelId,
                                        RestStatus.NOT_FOUND
                                    )
                                )
                        ));

                    } catch (Exception e) {
                        log.error("Failed to parse ml model controller with model ID: " + r.getId(), e);
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find ml model controller with the provided model ID: " + modelId,
                                RestStatus.NOT_FOUND
                            )
                        );
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    log.error("Failed to get ml model controller index", e);
                    wrappedListener.onFailure(new OpenSearchStatusException("Failed to find ml model controller", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get ml model controller " + modelId, e);
                    wrappedListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get ml model controller " + modelId, e);
            actionListener.onFailure(e);
        }
    }
}
