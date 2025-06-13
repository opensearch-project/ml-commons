/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

import java.util.Objects;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptDeleteAction;
import org.opensearch.ml.common.transport.prompt.MLPromptDeleteRequest;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of deleting a prompt from a system index.
 */
@Log4j2
public class DeletePromptTransportAction extends HandledTransportAction<MLPromptDeleteRequest, DeleteResponse> {
    private final Client client;
    private final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLPromptManager mlPromptManager;

    @Inject
    public DeletePromptTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLPromptManager mlPromptManager
    ) {
        super(MLPromptDeleteAction.NAME, transportService, actionFilters, MLPromptDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlPromptManager = mlPromptManager;
    }

    /**
     * Executes the received request by deleting the prompt from the system index based on the provided prompt id.
     * Notify the listener with the DeleteResponse if the operation is successful. Otherwise, failure exception
     * is notified to the listener.
     *
     * @param task The task
     * @param mlPromptDeleteRequest mlPromptDeleteRequest that contains the prompt id of a prompt that needs to be deleted
     * @param actionListener a listener to be notified of the response
     */
    @Override
    protected void doExecute(Task task, MLPromptDeleteRequest mlPromptDeleteRequest, ActionListener<DeleteResponse> actionListener) {
        String promptId = Objects.requireNonNull(mlPromptDeleteRequest.getPromptId(), "Prompt ID cannot be null");
        String tenantId = mlPromptDeleteRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_PROMPT_INDEX)
            .id(promptId)
            .tenantId(tenantId)
            .build();
        mlPromptManager
            .getPromptAsync(
                getDataObjectRequest,
                promptId,
                ActionListener
                    .wrap(
                        mlPrompt -> executeDeletePrompt(mlPrompt, promptId, tenantId, actionListener),
                        e -> handleFailure(e, promptId, actionListener, "Failed to get ML Prompt {}")
                    )
            );
    }

    /**
     * Delete the prompt based on the requested prompt id and hard delete by removing the corresponding doc from
     * the provided system index
     *
     * @param mlPrompt the MLPrompt that needs to be deleted
     * @param promptId The prompt ID of a prompt that needs to deleted
     * @param tenantId The tenant ID
     * @param listener a listener to be notified of the response
     */
    private void executeDeletePrompt(MLPrompt mlPrompt, String promptId, String tenantId, ActionListener<DeleteResponse> listener) {
        DeleteDataObjectRequest deleteDataObjectRequest = DeleteDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(promptId).build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
            sdkClient.deleteDataObjectAsync(deleteDataObjectRequest).whenComplete((deleteDataObjectResponse, throwable) -> {
                handleDeleteResponse(deleteDataObjectResponse, throwable, promptId, wrappedListener);
            });
        }
    }

    /**
     * Handles the response from the delete prompt request. If the response is successful, notify the listener
     * with the DeleteResponse. Otherwise, notify the failure exception to the listener.
     *
     * @param deleteDataObjectResponse The response from the delete prompt request
     * @param throwable The exception that occurred during the delete prompt request
     * @param listener The listener to be notified of the response
     */
    private void handleDeleteResponse(
        DeleteDataObjectResponse deleteDataObjectResponse,
        Throwable throwable,
        String promptId,
        ActionListener<DeleteResponse> listener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleFailure(cause, promptId, listener, "Failed to delete ML prompt {}");
            return;
        }
        DeleteResponse deleteResponse = deleteDataObjectResponse.deleteResponse();
        listener.onResponse(deleteResponse);
    }

    /**
     * Notify the listener of the failure exception. while fetching and deleting the prompt object from the system index
     *
     * @param exception The failure exception
     * @param promptId The prompt id
     * @param listener ActionListener to be notified of the response
     * @param likelyCause the likely cause message of failure
     */
    private void handleFailure(Exception exception, String promptId, ActionListener<DeleteResponse> listener, String likelyCause) {
        log.error(likelyCause, promptId, exception);
        listener.onFailure(exception);
    }
}
