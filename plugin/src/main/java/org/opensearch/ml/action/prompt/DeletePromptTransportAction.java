/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

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

    @Override
    protected void doExecute(Task task, MLPromptDeleteRequest mlPromptDeleteRequest, ActionListener<DeleteResponse> actionListener) {
        String promptId = mlPromptDeleteRequest.getPromptId();
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

    private void executeDeletePrompt(MLPrompt mlPrompt, String promptId, String tenantId, ActionListener<DeleteResponse> listener) {
        DeleteDataObjectRequest deleteDataObjectRequest = DeleteDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(promptId).build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.deleteDataObjectAsync(deleteDataObjectRequest).whenComplete((deleteDataObjectResponse, throwable) -> {
                context.restore();
                handleDeleteResponse(deleteDataObjectResponse, throwable, promptId, listener);
            });
        }
    }

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

    private void handleFailure(Exception exception, String promptId, ActionListener<DeleteResponse> listener, String likelyCause) {
        log.error(likelyCause, promptId, exception);
        listener.onFailure(exception);
    }
}
