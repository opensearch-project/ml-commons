/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.time.Instant;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of creating a prompt in the system index.
 */
@Log4j2
public class TransportCreatePromptAction extends HandledTransportAction<ActionRequest, MLCreatePromptResponse> {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final SdkClient sdkClient;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportCreatePromptAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLCreatePromptAction.NAME, transportService, actionFilters, MLCreatePromptRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Creates a system index for prompt, if needed. Creates a new prompt and store it into the system index. Notify the
     * listener with the MLCreatePromptResponse with prompt id. Otherwise, failure exception is notified to the listener.
     *
     * @param task The task
     * @param request MLCreatePromptRequest
     * @param listener a listener to be notified of the response
     */
    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreatePromptResponse> listener) {
        MLCreatePromptRequest mlCreatePromptRequest = MLCreatePromptRequest.fromActionRequest(request);
        MLCreatePromptInput mlCreatePromptInput = mlCreatePromptRequest.getMlCreatePromptInput();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlCreatePromptInput.getTenantId(), listener)) {
            return;
        }

        try {
            MLPrompt mlPrompt = MLPrompt
                .builder()
                .name(mlCreatePromptInput.getName())
                .description(mlCreatePromptInput.getDescription())
                .prompt(mlCreatePromptInput.getPrompt())
                .tenantId(mlCreatePromptInput.getTenantId())
                .tag(mlCreatePromptInput.getTag())
                .createTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .build();

            indexPrompt(mlPrompt, listener);
        } catch (Exception e) {
            log.error("Failed to create a Prompt", e);
            listener.onFailure(e);
        }
    }

    /**
     * Create a system index for prompt, if needed. Store the prompt into the system index.
     * Notify the listener based on the response of the putDataObjectAsync op.
     *
     * @param prompt A prompt to be stored into the system index
     * @param listener ActionListener to be notified of the response
     */
    private void indexPrompt(MLPrompt prompt, ActionListener<MLCreatePromptResponse> listener) {
        log.info("prompt created, indexing into the prompt system index");
        mlIndicesHandler.initMLPromptIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                listener.onFailure(new RuntimeException("No response to create ML Prompt Index"));
                return;
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                PutDataObjectRequest putRequest = buildPromptPutRequest(prompt);
                sdkClient
                        .putDataObjectAsync(putRequest)
                        .whenComplete((putResponse, throwable) -> {
                            context.restore();
                            handlePromptPutResponse(putResponse, throwable, listener);
                        }
                );
            }
        }, e -> {
            log.error("Failed to init ML prompt index", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Create a PutDataObjectRequest to store the prompt into the system index.
     *
     * @param prompt A prompt to be stored into the system index
     * @return PutDataObjectRequest
     */
    private PutDataObjectRequest buildPromptPutRequest(MLPrompt prompt) {
        return PutDataObjectRequest
                .builder()
                .tenantId(prompt.getTenantId())
                .index(ML_PROMPT_INDEX)
                .dataObject(prompt)
                .build();
    }

    /**
     * If the prompt is successfully stored into the system index, notify the listener with the MLCreatePromptResponse with prompt id.
     * Otherwise, failure exception is notified to the listener.
     *
     * @param putResponse PutDataObjectResponse received from putDataObjectAsync op
     * @param throwable Throwable received from putDataObjectAsync op. It is null if no exception thrown.
     * @param listener ActionListener to be notified of the response
     */
    private void handlePromptPutResponse(
        PutDataObjectResponse putResponse,
        Throwable throwable,
        ActionListener<MLCreatePromptResponse> listener
    ) {
        if (putResponse == null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handlePromptPutFailure(cause, listener);
        }
        try {
            IndexResponse indexResponse = IndexResponse.fromXContent(putResponse.parser());
            log.info("Prompt creation result: {}, prompt id: {}", indexResponse.getResult(), indexResponse.getId());
            listener.onResponse(new MLCreatePromptResponse(indexResponse.getId()));
        } catch (Exception e) {
            handlePromptPutFailure(e, listener);
        }
    }

    /**
     * Notify the listener of the failure exception.
     *
     * @param cause The failure exception
     * @param listener ActionListener to be notified of the response
     */
    private void handlePromptPutFailure(Exception cause, ActionListener<MLCreatePromptResponse> listener) {
        log.error("Failed to save ML Prompt", cause);
        listener.onFailure(cause);
    }
}
