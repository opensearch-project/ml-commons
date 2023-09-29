/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UpdateModelTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;
    NamedXContentRegistry xContentRegistry;
    ModelAccessControlHelper modelAccessControlHelper;
    ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public UpdateModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLUpdateModelAction.NAME, transportService, actionFilters, MLUpdateModelRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLUpdateModelRequest updateModelRequest = MLUpdateModelRequest.fromActionRequest(request);
        MLUpdateModelInput updateModelInput = updateModelRequest.getUpdateModelInput();
        String modelId = updateModelInput.getModelId();
        UpdateRequest updateRequest = new UpdateRequest(ML_MODEL_INDEX, modelId);

        try {
            updateRequest.doc(updateModelInput.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS));
        } catch (IOException e) {
            log.error("Failed to construct the update request with the input.", e);
            actionListener.onFailure(e);
        }
        updateRequest.docAsUpsert(true);
        User user = RestActionUtils.getUserContext(client);

        MLModelGetRequest mlModelGetRequest = new MLModelGetRequest(modelId, false);
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetRequest getModelRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getModelRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = MLNodeUtils.createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        String algorithmName = "";
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        if (r.getSource() != null && r.getSource().get(ALGORITHM_FIELD) != null) {
                            algorithmName = r.getSource().get(ALGORITHM_FIELD).toString();
                        } else {
                            actionListener.onFailure(new RuntimeException("ALGORITHM_FIELD not found for this model, model ID " + modelId));
                        }
                        MLModel mlModel = MLModel.parse(parser, algorithmName);
                        if (mlModel.getAlgorithm() == TEXT_EMBEDDING || mlModel.getAlgorithm() == REMOTE) {
                            modelAccessControlHelper
                                .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                                    if (Boolean.TRUE.equals(hasPermission)) {
                                        updateRemoteOrTextEmbeddingModel(
                                            modelId,
                                            updateModelInput,
                                            mlModel,
                                            user,
                                            updateRequest,
                                            actionListener,
                                            context
                                        );
                                    } else {
                                        actionListener
                                            .onFailure(
                                                new MLValidationException(
                                                    "User doesn't have privilege to perform this operation on this model, model ID "
                                                        + modelId
                                                )
                                            );
                                    }
                                }, exception -> {
                                    log.error("Permission denied: Unable to update the model with ID {}. Details: {}", modelId, exception);
                                    actionListener.onFailure(exception);
                                }));
                        } else {
                            actionListener
                                .onFailure(
                                    new MLValidationException(
                                        "User doesn't have privilege to perform this operation on this function category: "
                                            + mlModel.getAlgorithm().toString()
                                    )
                                );
                        }
                    } catch (Exception e) {
                        log.error("Failed to update ML model for model ID {}. Details {}:", modelId, e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener
                        .onFailure(new IllegalArgumentException("Failed to find model to update with the provided model id: " + modelId));
                }
            }, e -> actionListener.onFailure(new MLResourceNotFoundException("Fail to find model"))));
        } catch (Exception e) {
            log.error("Failed to update ML model for " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    private void updateModelWithRelinkStandAloneConnector(
        String modelId,
        String relinkModelGroupId,
        String relinkConnectorId,
        MLModel mlModel,
        User user,
        UpdateRequest updateRequest,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        if (Strings.hasLength(mlModel.getConnectorId())) {
            connectorAccessControlHelper
                .validateConnectorAccess(client, relinkConnectorId, ActionListener.wrap(hasRelinkConnectorPermission -> {
                    if (Boolean.TRUE.equals(hasRelinkConnectorPermission)) {
                        updateModelWithOrWithoutRelinkModelGroup(modelId, relinkModelGroupId, user, updateRequest, actionListener, context);
                    } else {
                        actionListener
                            .onFailure(
                                new MLValidationException(
                                    "You don't have permission to update the connector, connector id: " + relinkConnectorId
                                )
                            );
                    }
                }, exception -> {
                    log.error("Permission denied: Unable to update the connector with ID {}. Details: {}", relinkConnectorId, exception);
                    actionListener.onFailure(exception);
                }));
        } else {
            actionListener
                .onFailure(
                    new IllegalArgumentException("This remote does not have a connector_id field, maybe it uses an internal connector.")
                );
        }
    }

    private void updateRemoteOrTextEmbeddingModel(
        String modelId,
        MLUpdateModelInput updateModelInput,
        MLModel mlModel,
        User user,
        UpdateRequest updateRequest,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        String relinkModelGroupId = Strings.hasLength(updateModelInput.getModelGroupId()) ? updateModelInput.getModelGroupId() : null;
        String relinkConnectorId = Strings.hasLength(updateModelInput.getConnectorId()) ? updateModelInput.getConnectorId() : null;

        if (mlModel.getAlgorithm() == TEXT_EMBEDDING) {
            if (relinkConnectorId == null) {
                updateModelWithOrWithoutRelinkModelGroup(modelId, relinkModelGroupId, user, updateRequest, actionListener, context);
            } else {
                actionListener
                    .onFailure(new IllegalArgumentException("Trying to update the connector or connector_id field on a local model"));
            }
        } else {
            // mlModel.getAlgorithm() == REMOTE
            if (relinkConnectorId == null) {
                updateModelWithOrWithoutRelinkModelGroup(modelId, relinkModelGroupId, user, updateRequest, actionListener, context);
            } else {
                updateModelWithRelinkStandAloneConnector(
                    modelId,
                    relinkModelGroupId,
                    relinkConnectorId,
                    mlModel,
                    user,
                    updateRequest,
                    actionListener,
                    context
                );
            }
        }
    }

    private void updateModelWithOrWithoutRelinkModelGroup(
        String modelId,
        String relinkModelGroupId,
        User user,
        UpdateRequest updateRequest,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        if (relinkModelGroupId != null) {
            modelAccessControlHelper.validateModelGroupAccess(user, relinkModelGroupId, client, ActionListener.wrap(hasRelinkPermission -> {
                if (Boolean.TRUE.equals(hasRelinkPermission)) {
                    client.update(updateRequest, getUpdateResponseListener(modelId, actionListener, context));
                } else {
                    actionListener
                        .onFailure(
                            new MLValidationException(
                                "User Doesn't have privilege to re-link this model to the target model group due to no access to the target model group with model group ID "
                                    + relinkModelGroupId
                            )
                        );
                }
            }, exception -> {
                log.error("Permission denied: Unable to update the model with ID {}. Details: {}", modelId, exception);
                actionListener.onFailure(exception);
            }));
        } else {
            client.update(updateRequest, getUpdateResponseListener(modelId, actionListener, context));
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String modelId,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        return ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.info("Model id:{} failed update", modelId);
                actionListener.onResponse(updateResponse);
                return;
            }
            log.info("Completed Update Model Request, model id:{} updated", modelId);
            actionListener.onResponse(updateResponse);
        }, exception -> {
            log.error("Failed to update ML model: " + modelId, exception);
            actionListener.onFailure(exception);
        }), context::restore);
    }
}
