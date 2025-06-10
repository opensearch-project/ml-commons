/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.prompt.AbstractPromptManagement.init;
import static org.opensearch.ml.prompt.MLPromptManager.handleFailure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.index.IndexResponse;
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
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ImportPromptTransportAction extends HandledTransportAction<MLImportPromptRequest, MLImportPromptResponse> {
    public static final String DEFAULT_LIMIT = "20";

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

        String publicKey = mlImportPromptInput.getPublicKey();
        String accessKey = mlImportPromptInput.getAccessKey();
        String tenantId = mlImportPromptInput.getTenantId();

        // TODO: pass in PromptManagementType inside request body
        try {
            AbstractPromptManagement promptManagement = init(
                LANGFUSE,
                PromptExtraConfig.builder().publicKey(publicKey).accessKey(accessKey).build()
            );
            List<MLPrompt> mlPromptList = promptManagement.importPrompts(mlImportPromptInput);
            Map<String, String> responseBody = new HashMap<>();
            AtomicInteger remainingMLPrompts = new AtomicInteger(mlPromptList.size());
            for (MLPrompt mlPrompt : mlPromptList) {
                mlPrompt.encrypt(LANGFUSE, mlEngine::encrypt, tenantId);
                indexPrompt(mlPrompt, responseBody, remainingMLPrompts, listener);
            }

        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to import prompts");
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
            responseBody.put(name, indexResponse.getId());

            remainingMLPrompts.set(remainingMLPrompts.get() - 1);
            if (remainingMLPrompts.get() == 0) {
                listener.onResponse(new MLImportPromptResponse(responseBody));
            }
        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to parse PutDataObjectResponse into Index Response from xContent");
        }
    }
}
