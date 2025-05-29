/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.prompt.MLPromptManager.handleFailure;
import static org.opensearch.ml.prompt.MLPromptManager.validateTags;

import java.time.Instant;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of creating a prompt in the system index.
 */
@Log4j2
public class TransportCreatePromptAction extends HandledTransportAction<MLCreatePromptRequest, MLCreatePromptResponse> {
    private static final String INITIAL_VERSION = "1";
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
     * Creates a new prompt and notify the listener with the response that contains prompt id, after it is successfully
     * stored into the system index.
     *
     * @param task The task
     * @param mlCreatePromptRequest MLCreatePromptRequest that contains the metadata needed to create a new prompt
     * @param listener a listener to be notified of the response
     * <p>
     *      This method is called by the TransportService to execute the action request on the node that is
     *      handling the request. It first validates incoming request and then retrieve all the metadata
     *      needed from the request body to create a new prompt. For an initial create api request, it creates
     *      a system index for prompt.
     *      The method also stores the successfully create prompt into the system index and then notify
     *      the listener with the MLCreatePromptResponse with prompt id, if there is no failure. Otherwise,
     *      failure exception is notified to the listener.
     * </p>
     */
    @Override
    protected void doExecute(Task task, MLCreatePromptRequest mlCreatePromptRequest, ActionListener<MLCreatePromptResponse> listener) {
        MLCreatePromptInput mlCreatePromptInput = mlCreatePromptRequest.getMlCreatePromptInput();
        if (mlCreatePromptInput.getTags() != null && !validateTags(mlCreatePromptInput.getTags())) {
            handleFailure(
                new IllegalArgumentException("Number of tags must not exceed 20 and length of each tag must not exceed 35"),
                null,
                listener,
                "Tags Exceeds max number of tags or max length of tag"
            );
        }
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlCreatePromptInput.getTenantId(), listener)) {
            return;
        }

        try {
            String version = mlCreatePromptInput.getVersion();
            MLPrompt mlPrompt = MLPrompt
                .builder()
                .name(mlCreatePromptInput.getName())
                .description(mlCreatePromptInput.getDescription())
                .version(version == null ? INITIAL_VERSION : version)
                .prompt(mlCreatePromptInput.getPrompt())
                .tags(mlCreatePromptInput.getTags())
                .tenantId(mlCreatePromptInput.getTenantId())
                .createTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .build();

            indexPrompt(mlPrompt, listener);
        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to create a MLPrompt");
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
                Exception exception = new RuntimeException("No response to create ML Prompt Index");
                handleFailure(exception, null, listener, "Failed to create a system index for prompt");
                return;
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                PutDataObjectRequest putRequest = buildPromptPutRequest(prompt);
                sdkClient.putDataObjectAsync(putRequest).whenComplete((putResponse, throwable) -> {
                    context.restore();
                    handlePromptPutResponse(putResponse, throwable, listener);
                });
            }
        }, e -> { handleFailure(e, null, listener, "Failed to init ML prompt index"); }));
    }

    /**
     * Create a PutDataObjectRequest to store the prompt into the system index.
     *
     * @param prompt A prompt to be stored into the system index
     * @return PutDataObjectRequest
     */
    private PutDataObjectRequest buildPromptPutRequest(MLPrompt prompt) {
        return PutDataObjectRequest.builder().tenantId(prompt.getTenantId()).index(ML_PROMPT_INDEX).dataObject(prompt).build();
    }

    /**
     * Handles the response from the putDataObjectAsync based on whether exception is thrown or not.
     *
     * @param putResponse PutDataObjectResponse received from putDataObjectAsync op
     * @param throwable Throwable received from putDataObjectAsync op. It is null if no exception thrown.
     * @param listener ActionListener to be notified of the response
     *
     * @implNote This method uses Throwable object to check if the response is successful or not.
     *           If the throwable object is null, then the prompt is successfully stored into the
     *           system index and notify the listener with the MLCreatePromptResponse with prompt id.
     *           Otherwise, failure exception is notified to the listener.
     */
    private void handlePromptPutResponse(
        PutDataObjectResponse putResponse,
        Throwable throwable,
        ActionListener<MLCreatePromptResponse> listener
    ) {
        if (putResponse == null || throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleFailure(cause, null, listener, "Prompt Put Response cannot be null");
        }
        try {
            IndexResponse indexResponse = IndexResponse.fromXContent(putResponse.parser());
            log.info("Prompt creation result: {}, prompt id: {}", indexResponse.getResult(), indexResponse.getId());
            listener.onResponse(new MLCreatePromptResponse(indexResponse.getId()));
        } catch (Exception e) {
            handleFailure(e, null, listener, "Failed to parse PutDataObjectResponse into Index Response from xContent");
        }
    }
}
