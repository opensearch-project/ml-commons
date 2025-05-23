/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.util.Objects;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.transport.client.Client;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * MLPromptManager is responsible for managing MLPrompt.
 */
@Log4j2
public class MLPromptManager {
    private final Client client;
    private final SdkClient sdkClient;

    public MLPromptManager(@NonNull Client client, @NonNull SdkClient sdkClient) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
        this.sdkClient = Objects.requireNonNull(sdkClient, "SdkClient cannot be null");
    }

    /**
     * Gets a prompt with the provided clients asynchronously.
     *
     * @param getDataObjectRequest The get request
     * @param promptId The prompt ID
     * @param listener the action listener to complete with the GetResponse or Exception
     */
    public void getPromptAsync(GetDataObjectRequest getDataObjectRequest, String promptId, ActionListener<MLPrompt> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((getAsyncResponse, throwable) -> {
                context.restore();
                handleAsyncResponse(getAsyncResponse, throwable, promptId, listener);
            });
        }
    }

    /**
     * Handles the get prompt async response
     *
     * @param getAsyncResponse the get prompt async response
     * @param throwable the throwable from the get prompt async response
     * @param promptId the prompt id of prompts that needed to be retrieved
     * @param listener the listener to be notified when the get prompt async response is handled
     */
    private void handleAsyncResponse(
        GetDataObjectResponse getAsyncResponse,
        Throwable throwable,
        String promptId,
        ActionListener<MLPrompt> listener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleFailure(cause, promptId, listener, "Failed to get ML prompt {}");
            return;
        }
        processResponse(getAsyncResponse, promptId, listener);

    }

    /**
     * Handles the Exception thrown and notify the listener with the failure.
     *
     * @param exception the exception from the get prompt async response
     * @param promptId the prompt id of prompts that needed to be retrieved
     * @param listener the listener to be notified when the throwable is handled
     */
    private void handleFailure(Exception exception, String promptId, ActionListener<MLPrompt> listener, String likelyCause) {
        if (ExceptionsHelper.unwrap(exception, IndexNotFoundException.class) != null) {
            log.error("Failed to get prompt index", exception);
            listener
                .onFailure(
                    new OpenSearchStatusException("Failed to find prompt with the provided prompt id: " + promptId, RestStatus.NOT_FOUND)
                );
        } else {
            log.error(likelyCause, promptId, exception);
            listener.onFailure(exception);
        }
    }

    /**
     * Parse the successfully retrieved GetDataObjectResponse to get the MLPrompt object, and notify the listener with it.
     *
     * @param getAsyncResponse the GetDataObjectResponse that needs to be parsed
     * @param promptId the prompt id used to retrieve the prompt
     * @param listener the listener to be notified with the MLPrompt object
     */
    private void processResponse(GetDataObjectResponse getAsyncResponse, String promptId, ActionListener<MLPrompt> listener) {
        GetResponse getResponse = getAsyncResponse.getResponse();
        try (
            XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            MLPrompt mlPrompt = MLPrompt.parse(parser);
            listener.onResponse(mlPrompt);
        } catch (Exception e) {
            handleFailure(e, promptId, listener, "Failed to parse GetDataObjectResponse for prompt {}");
        }
    }
}
