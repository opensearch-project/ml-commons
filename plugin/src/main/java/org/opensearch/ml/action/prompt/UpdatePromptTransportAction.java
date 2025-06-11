/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.prompt.AbstractPromptManagement.init;
import static org.opensearch.ml.prompt.MLPromptManager.MLPromptNameAlreadyExists;
import static org.opensearch.ml.prompt.MLPromptManager.TAG_RESTRICTION_ERR_MESSAGE;
import static org.opensearch.ml.prompt.MLPromptManager.UNIQUE_NAME_ERR_MESSAGE;
import static org.opensearch.ml.prompt.MLPromptManager.handleFailure;
import static org.opensearch.ml.prompt.MLPromptManager.validateTags;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.prompt.AbstractPromptManagement;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of updating a prompt and storing changed prompt into the system index.
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePromptTransportAction extends HandledTransportAction<MLUpdatePromptRequest, UpdateResponse> {
    Client client;
    SdkClient sdkClient;
    MLPromptManager mlPromptManager;
    EncryptorImpl encryptor;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public UpdatePromptTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLPromptManager mlPromptManager,
        EncryptorImpl encryptor
    ) {
        super(MLUpdatePromptAction.NAME, transportService, actionFilters, MLUpdatePromptRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlPromptManager = mlPromptManager;
        this.encryptor = encryptor;
    }

    /**
     * Executes the received request by updating the prompt and storing the changed prompt into the system index.
     * Notify the listener with the UpdateResponse if the operation is successful. Otherwise, failure exception
     * is notified to the listener.
     *
     * @param task The task
     * @param mlUpdatePromptRequest MLUpdatePromptRequest that contains contents that need to be changed
     * @param actionListener a listener to be notified of the response
     */
    @Override
    protected void doExecute(Task task, MLUpdatePromptRequest mlUpdatePromptRequest, ActionListener<UpdateResponse> actionListener) {
        MLUpdatePromptInput mlUpdatePromptInput = mlUpdatePromptRequest.getMlUpdatePromptInput();
        String promptId = mlUpdatePromptRequest.getPromptId();
        String tenantId = mlUpdatePromptInput.getTenantId();
        if (mlUpdatePromptInput.getTags() != null && !validateTags(mlUpdatePromptInput.getTags())) {
            handleFailure(
                new IllegalArgumentException(TAG_RESTRICTION_ERR_MESSAGE),
                promptId,
                actionListener,
                "Tags Exceeds max number of tags or max length of tag"
            );
            return;
        }
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        try {
            if (StringUtils.isNotBlank(mlUpdatePromptInput.getName())) {
                SearchResponse searchResponse = mlPromptManager
                        .validateUniquePromptName(mlUpdatePromptInput.getName(), mlUpdatePromptInput.getTenantId());
                if (searchResponse != null
                        && searchResponse.getHits().getTotalHits() != null
                        && searchResponse.getHits().getTotalHits().value() != 0) {
                    SearchHit hit = searchResponse.getHits().getAt(0);
                    String id = hit.getId();
                    actionListener.onFailure(new IllegalArgumentException(UNIQUE_NAME_ERR_MESSAGE + id));
                }
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
                    ActionListener.wrap(
                        mlPrompt -> handleGetPrompt(mlPrompt, mlUpdatePromptInput, promptId, tenantId, actionListener),
                        e -> handleFailure(e, promptId, actionListener, "Failed to get ML Prompt {}")
                    )
                );
        } catch (Exception exception) {
            handleFailure(exception, promptId, actionListener, "Failed to search ML Prompt Index");
        }
    }

    /**
     * Update the content of MLPrompt that is needed and increment the version
     *
     * @param mlPrompt MLPrompt that needs to be updated
     * @param mlUpdatePromptInput MLUpdatePromptInput that contains contents that need to be updated
     * @param promptId the prompt id
     * @param tenantId the tenant id
     * @param listener a listener to be notified of the response
     */
    private void handleGetPrompt(
        MLPrompt mlPrompt,
        MLUpdatePromptInput mlUpdatePromptInput,
        String promptId,
        String tenantId,
        ActionListener<UpdateResponse> listener
    ) {
        PromptExtraConfig extraConfig = mlPrompt.getPromptExtraConfig();
        String promptManagementType = mlPrompt.getPromptManagementType();
        mlPrompt.setPromptId(promptId);
        mlPrompt.decrypt(mlPrompt.getPromptManagementType(), encryptor::decrypt, tenantId);
        AbstractPromptManagement promptManagement = init(promptManagementType, extraConfig);
        UpdateDataObjectRequest updateDataObjectRequest = promptManagement.updatePrompt(mlUpdatePromptInput, mlPrompt);

        mlPromptManager.updatePromptIndex(updateDataObjectRequest, promptId, listener);
    }
}
