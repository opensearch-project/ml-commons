/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.prompt.AbstractPromptManagement.init;
import static org.opensearch.ml.prompt.MLPromptManager.handleFailure;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptGetAction;
import org.opensearch.ml.common.transport.prompt.MLPromptGetRequest;
import org.opensearch.ml.common.transport.prompt.MLPromptGetResponse;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.prompt.AbstractPromptManagement;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of retrieving a prompt from the system index.
 */
@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetPromptTransportAction extends HandledTransportAction<MLPromptGetRequest, MLPromptGetResponse> {
    Client client;
    SdkClient sdkClient;
    EncryptorImpl encryptor;
    MLPromptManager mlPromptManager;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetPromptTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        EncryptorImpl encryptor,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLPromptManager mlPromptManager
    ) {
        super(MLPromptGetAction.NAME, transportService, actionFilters, MLPromptGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlPromptManager = mlPromptManager;
    }

    /**
     * Get the prompt object from the system index based on the prompt id.
     * Notify the listener of the response, if successfully retrieved the prompt. Otherwise, failure exception is
     * notified to the listener.
     *
     * @param task The task
     * @param mlPromptGetRequest The MLPromptGetRequest to be executed
     * @param actionListener The listener to be notified of the response
     */
    @Override
    protected void doExecute(Task task, MLPromptGetRequest mlPromptGetRequest, ActionListener<MLPromptGetResponse> actionListener) {
        String promptId = mlPromptGetRequest.getPromptId();
        String tenantId = mlPromptGetRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_PROMPT_INDEX)
            .id(promptId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        mlPromptManager.getPromptAsync(getDataObjectRequest, promptId, ActionListener.wrap(mlPrompt -> {
            try {
                mlPrompt.decrypt(mlPrompt.getPromptManagementType(), encryptor::decrypt, tenantId);
                AbstractPromptManagement promptManagement = init(mlPrompt.getPromptManagementType(), mlPrompt.getPromptExtraConfig());
                promptManagement.getPrompt(mlPrompt);
                actionListener.onResponse(MLPromptGetResponse.builder().mlPrompt(mlPrompt).build());
            } catch (Exception e) {
                log.error("Failed to process " + mlPrompt.getPromptManagementType() + " Prompt");
                actionListener.onFailure(e);
            }
        }, e -> handleFailure(e, promptId, actionListener, "Failed to get MLPrompt")));
    }
}
