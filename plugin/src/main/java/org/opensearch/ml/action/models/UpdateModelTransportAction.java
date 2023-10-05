/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UpdateModelTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;
    ModelAccessControlHelper modelAccessControlHelper;
    ConnectorAccessControlHelper connectorAccessControlHelper;
    MLModelManager mlModelManager;
    MLModelGroupManager mlModelGroupManager;

    @Inject
    public UpdateModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelManager mlModelManager,
        MLModelGroupManager mlModelGroupManager
    ) {
        super(MLUpdateModelAction.NAME, transportService, actionFilters, MLUpdateModelRequest::new);
        this.client = client;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlModelManager = mlModelManager;
        this.mlModelGroupManager = mlModelGroupManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLUpdateModelRequest updateModelRequest = MLUpdateModelRequest.fromActionRequest(request);
        MLUpdateModelInput updateModelInput = updateModelRequest.getUpdateModelInput();
        String modelId = updateModelInput.getModelId();
        User user = RestActionUtils.getUserContext(client);

        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
                FunctionName functionName = mlModel.getAlgorithm();
                MLModelState mlModelState = mlModel.getModelState();
                if (functionName == TEXT_EMBEDDING || functionName == REMOTE) {
                    modelAccessControlHelper
                        .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(hasPermission -> {
                            if (hasPermission) {
                                if (!mlModelState.equals(MLModelState.LOADED)
                                    && !mlModelState.equals(MLModelState.LOADING)
                                    && !mlModelState.equals(MLModelState.PARTIALLY_LOADED)
                                    && !mlModelState.equals(MLModelState.DEPLOYED)
                                    && !mlModelState.equals(MLModelState.DEPLOYING)
                                    && !mlModelState.equals(MLModelState.PARTIALLY_DEPLOYED)) {
                                    updateRemoteOrTextEmbeddingModel(modelId, updateModelInput, mlModel, user, actionListener);
                                } else {
                                    actionListener
                                        .onFailure(
                                            new MLValidationException(
                                                "ML Model "
                                                    + modelId
                                                    + " is in deploying or deployed state, please undeploy the models first!"
                                            )
                                        );
                                }
                            } else {
                                actionListener
                                    .onFailure(
                                        new MLValidationException(
                                            "User doesn't have privilege to perform this operation on this model, model ID " + modelId
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
                                    + functionName.toString()
                            )
                        );
                }
            },
                e -> actionListener
                    .onFailure(new MLResourceNotFoundException("Failed to find model to update with the provided model id: " + modelId))
            ), () -> context.restore()));
        } catch (Exception e) {
            log.error("Failed to update ML model for " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    private void updateRemoteOrTextEmbeddingModel(
        String modelId,
        MLUpdateModelInput updateModelInput,
        MLModel mlModel,
        User user,
        ActionListener<UpdateResponse> actionListener
    ) {
        String newModelGroupId = Strings.hasLength(updateModelInput.getModelGroupId()) ? updateModelInput.getModelGroupId() : null;
        String relinkConnectorId = Strings.hasLength(updateModelInput.getConnectorId()) ? updateModelInput.getConnectorId() : null;

        if (mlModel.getAlgorithm() == TEXT_EMBEDDING) {
            if (relinkConnectorId == null) {
                updateModelWithRegisteringNewModelGroup(modelId, newModelGroupId, user, updateModelInput, actionListener);
            } else {
                actionListener
                    .onFailure(new IllegalArgumentException("Trying to update the connector or connector_id field on a local model"));
            }
        } else {
            // mlModel.getAlgorithm() == REMOTE
            if (relinkConnectorId == null) {
                updateModelWithRegisteringNewModelGroup(modelId, newModelGroupId, user, updateModelInput, actionListener);
            } else {
                updateModelWithRelinkStandAloneConnector(
                    modelId,
                    newModelGroupId,
                    relinkConnectorId,
                    mlModel,
                    user,
                    updateModelInput,
                    actionListener
                );
            }
        }
    }

    private void updateModelWithRelinkStandAloneConnector(
        String modelId,
        String newModelGroupId,
        String relinkConnectorId,
        MLModel mlModel,
        User user,
        MLUpdateModelInput updateModelInput,
        ActionListener<UpdateResponse> actionListener
    ) {
        if (Strings.hasLength(mlModel.getConnectorId())) {
            connectorAccessControlHelper
                .validateConnectorAccess(client, relinkConnectorId, ActionListener.wrap(hasRelinkConnectorPermission -> {
                    if (hasRelinkConnectorPermission) {
                        updateModelWithRegisteringNewModelGroup(modelId, newModelGroupId, user, updateModelInput, actionListener);
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

    private void updateModelWithRegisteringNewModelGroup(
        String modelId,
        String newModelGroupId,
        User user,
        MLUpdateModelInput updateModelInput,
        ActionListener<UpdateResponse> actionListener
    ) {
        UpdateRequest updateRequest = new UpdateRequest(ML_MODEL_INDEX, modelId);
        if (newModelGroupId != null) {
            modelAccessControlHelper.validateModelGroupAccess(user, newModelGroupId, client, ActionListener.wrap(hasRelinkPermission -> {
                if (hasRelinkPermission) {
                    mlModelGroupManager.getModelGroup(newModelGroupId, ActionListener.wrap(newModelGroup -> {
                        String updatedVersion = incrementLatestVersion(newModelGroup);
                        updateModelInput.setVersion(updatedVersion);
                        newModelGroup.setLatestVersion(Integer.parseInt(updatedVersion));
                        updateRequestConstructor(modelId, updateRequest, updateModelInput, actionListener);
                    },
                        exception -> actionListener
                            .onFailure(
                                new MLResourceNotFoundException(
                                    "Failed to find the model group with the provided model group id in the update model input, MODEL_GROUP_ID: "
                                        + newModelGroupId
                                )
                            )
                    ));
                } else {
                    actionListener
                        .onFailure(
                            new MLValidationException(
                                "User Doesn't have privilege to re-link this model to the target model group due to no access to the target model group with model group ID "
                                    + newModelGroupId
                            )
                        );
                }
            }, exception -> {
                log.error("Permission denied: Unable to update the model with ID {}. Details: {}", modelId, exception);
                actionListener.onFailure(exception);
            }));
        } else {
            updateRequestConstructor(modelId, updateRequest, updateModelInput, actionListener);
        }
    }

    private void updateRequestConstructor(
        String modelId,
        UpdateRequest updateRequest,
        MLUpdateModelInput updateModelInput,
        ActionListener<UpdateResponse> actionListener
    ) {
        try {
            updateRequest.doc(updateModelInput.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS));
            updateRequest.docAsUpsert(true);
            client.update(updateRequest, getUpdateResponseListener(modelId, actionListener));
        } catch (IOException e) {
            log.error("Failed to build update request.");
            actionListener.onFailure(e);
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(String modelId, ActionListener<UpdateResponse> actionListener) {
        return ActionListener.wrap(updateResponse -> {
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
        });
    }

    private String incrementLatestVersion(MLModelGroup mlModelGroup) {
        return Integer.toString(mlModelGroup.getLatestVersion() + 1);
    }
}
