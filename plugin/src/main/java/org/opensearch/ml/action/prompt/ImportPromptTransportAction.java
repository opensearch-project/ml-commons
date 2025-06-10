/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.common.prompt.MLPrompt.MLPROMPT;
import static org.opensearch.ml.prompt.AbstractPromptManagement.init;
import static org.opensearch.ml.prompt.MLPromptManager.handleFailure;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLImportPromptAction;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;
import org.opensearch.ml.common.transport.prompt.MLImportPromptRequest;
import org.opensearch.ml.common.transport.prompt.MLImportPromptResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.prompt.AbstractPromptManagement;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ImportPromptTransportAction extends HandledTransportAction<MLImportPromptRequest, MLImportPromptResponse> {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final SdkClient sdkClient;
    private final MLEngine mlEngine;
    private final MLPromptManager mlPromptManager;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public ImportPromptTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        SdkClient sdkClient,
        MLEngine mlEngine,
        MLPromptManager mlPromptManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLImportPromptAction.NAME, transportService, actionFilters, MLImportPromptRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlEngine = mlEngine;
        this.mlPromptManager = mlPromptManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLImportPromptRequest mlImportPromptRequest, ActionListener<MLImportPromptResponse> listener) {
        MLImportPromptInput mlImportPromptInput = mlImportPromptRequest.getMlImportPromptInput();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlImportPromptInput.getTenantId(), listener)) {
            return;
        }
        String promptManagementType = mlImportPromptInput.getPromptManagementType();
        String publicKey = mlImportPromptInput.getPublicKey();
        String accessKey = mlImportPromptInput.getAccessKey();
        String tenantId = mlImportPromptInput.getTenantId();

        // TODO: pass in PromptManagementType inside request body
        try {
            AbstractPromptManagement promptManagement = init(
                promptManagementType,
                PromptExtraConfig.builder().publicKey(publicKey).accessKey(accessKey).build()
            );
            List<MLPrompt> mlPromptList = promptManagement.importPrompts(mlImportPromptInput);
            Map<String, String> responseBody = new HashMap<>();
            AtomicInteger remainingMLPrompts = new AtomicInteger(mlPromptList.size());
            for (MLPrompt mlPrompt : mlPromptList) {
                mlPrompt.encrypt(promptManagementType, mlEngine::encrypt, tenantId);
                handleDuplicateName(
                    mlPrompt,
                    tenantId,
                    ActionListener.wrap(
                        promptId ->
                        {
                            if (promptId == null) {
                                indexPrompt(mlPrompt, responseBody, remainingMLPrompts, listener);
                            } else {
                                updateImportResponseBody(promptId, mlPrompt.getName(), responseBody, remainingMLPrompts, listener);
                            }
                        }, listener::onFailure
                    ));
            }
        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to import " + promptManagementType + " Prompts into System Index");
        }
    }

    private void indexPrompt(
        MLPrompt prompt,
        Map<String, String> responseBody,
        AtomicInteger remainingMLPrompts,
        ActionListener<MLImportPromptResponse> listener
    ) {
        log.info("prompt created, indexing into the prompt system index");
        mlIndicesHandler.initMLPromptIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                Exception exception = new RuntimeException("No response to create ML Prompt Index");
                handleFailure(exception, null, listener, "Failed to create a system index for prompt");
                return;
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                PutDataObjectRequest putRequest = buildPromptPutRequest(prompt);
                sdkClient.putDataObjectAsync(putRequest).whenComplete((putResponse, throwable) -> {
                    context.restore();
                    handlePromptPutResponse(putResponse, throwable, prompt.getName(), responseBody, remainingMLPrompts, listener);
                });
            }
        }, e -> { handleFailure(e, null, listener, "Failed to init ML prompt index"); }));
    }

    private PutDataObjectRequest buildPromptPutRequest(MLPrompt prompt) {
        return PutDataObjectRequest.builder().tenantId(prompt.getTenantId()).index(ML_PROMPT_INDEX).dataObject(prompt).build();
    }

    private void handlePromptPutResponse(
        PutDataObjectResponse putResponse,
        Throwable throwable,
        String name,
        Map<String, String> responseBody,
        AtomicInteger remainingMLPrompts,
        ActionListener<MLImportPromptResponse> listener
    ) {
        if (putResponse == null || throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleFailure(cause, null, listener, "Prompt Put Response cannot be null");
        }
        try {
            IndexResponse indexResponse = IndexResponse.fromXContent(putResponse.parser());
            log.info("Prompt creation result: {}, prompt id: {}", indexResponse.getResult(), indexResponse.getId());
            updateImportResponseBody(indexResponse.getId(), name, responseBody, remainingMLPrompts, listener);
        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to parse PutDataObjectResponse into Index Response from xContent");
        }
    }

    private void updateImportResponseBody(
        String promptId,
        String name,
        Map<String, String> responseBody,
        AtomicInteger remainingMLPrompts,
        ActionListener<MLImportPromptResponse> listener
    ) {
        responseBody.put(name, promptId);
        remainingMLPrompts.set(remainingMLPrompts.get() - 1);
        if (remainingMLPrompts.get() == 0) {
            listener.onResponse(new MLImportPromptResponse(responseBody));
        }
    }

    private void handleDuplicateName(MLPrompt importingPrompt, String tenantId, ActionListener<String> wrappedListener) throws IOException {
        String name = importingPrompt.getName();
        SearchResponse searchResponse = mlPromptManager.validateUniquePromptName(name, tenantId);
        if (searchResponse != null
                && searchResponse.getHits().getTotalHits() != null
                && searchResponse.getHits().getTotalHits().value() != 0) {
            String promptId = searchResponse.getHits().getAt(0).getId();
            GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
                    .builder()
                    .index(ML_PROMPT_INDEX)
                    .id(promptId)
                    .tenantId(tenantId)
                    .build();
            MLPrompt existingMLPrompt = mlPromptManager.getPrompt(getDataObjectRequest);

            // check the prompt management type
            String promptManagementType = existingMLPrompt.getPromptManagementType();
            if (promptManagementType.equalsIgnoreCase(MLPROMPT)) {
                throw new IllegalArgumentException("Provided name: " + name + " is already being used by ML Prompt with id: " + promptId);
            } else if (promptManagementType.equalsIgnoreCase(LANGFUSE)) {
                // update the existing langfuse prompt with new content if the version des not match
                UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest.builder()
                    .index(ML_PROMPT_INDEX)
                    .id(promptId)
                    .tenantId(tenantId)
                    .dataObject(importingPrompt)
                    .build();
                mlPromptManager.updatePromptIndex(updateDataObjectRequest, promptId, ActionListener.wrap(
                    updateResponse -> {
                        log.info("{} Prompt with promptId: {} updated successfully", promptManagementType, promptId);
                        wrappedListener.onResponse(promptId);
                    }, wrappedListener::onFailure
                ));
            }
        } else {
            // provided name is unique, good to be imported
            wrappedListener.onResponse(null);
        }
    }
}
