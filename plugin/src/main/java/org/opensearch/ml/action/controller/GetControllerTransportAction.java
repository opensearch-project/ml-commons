/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
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
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.transport.controller.MLControllerGetAction;
import org.opensearch.ml.common.transport.controller.MLControllerGetRequest;
import org.opensearch.ml.common.transport.controller.MLControllerGetResponse;
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
public class GetControllerTransportAction extends HandledTransportAction<ActionRequest, MLControllerGetResponse> {
    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;
    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public GetControllerTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLControllerGetAction.NAME, transportService, actionFilters, MLControllerGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLControllerGetResponse> actionListener) {
        MLControllerGetRequest controllerGetRequest = MLControllerGetRequest.fromActionRequest(request);
        String modelId = controllerGetRequest.getModelId();
        FetchSourceContext fetchSourceContext = getFetchSourceContext(controllerGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_CONTROLLER_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLControllerGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLController controller = MLController.parse(parser);
                        mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                            Boolean isHidden = mlModel.getIsHidden();
                            modelAccessControlHelper
                                .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                                    if (hasPermission) {
                                        wrappedListener.onResponse(MLControllerGetResponse.builder().controller(controller).build());
                                    } else {
                                        wrappedListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    getErrorMessage(
                                                        "User doesn't have privilege to perform this operation on this model controller.",
                                                        modelId,
                                                        isHidden
                                                    ),
                                                    RestStatus.FORBIDDEN
                                                )
                                            );
                                    }
                                }, exception -> {
                                    log
                                        .error(
                                            getErrorMessage(
                                                "Permission denied: Unable to create the model controller for the given model.",
                                                modelId,
                                                isHidden
                                            ),
                                            exception
                                        );
                                    wrappedListener.onFailure(exception);
                                }));
                        },
                            e -> wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "Failed to find model to get the corresponding model controller with the provided model ID",
                                        RestStatus.NOT_FOUND
                                    )
                                )
                        ));

                    } catch (Exception e) {
                        log.error("Failed to find model controller with the provided model ID", e);
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find model controller with the provided model ID",
                                RestStatus.NOT_FOUND
                            )
                        );
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    log.error("Failed to get model controller index", e);
                    wrappedListener.onFailure(new OpenSearchStatusException("Failed to find model controller", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get model controller for the provided model ID", e);
                    wrappedListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get model controller ", e);
            actionListener.onFailure(e);
        }
    }
}
