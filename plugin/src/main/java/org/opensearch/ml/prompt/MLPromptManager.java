/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

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
import org.opensearch.ml.common.MLPrompt;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import java.util.Set;

import lombok.extern.log4j.Log4j2;

/**
 * MLPromptManager is responsible for managing MLPrompt.
 */
@Log4j2
public class MLPromptManager {
    private static final Set<String> ROLES = Set.of("user", "system");
    private final Client client;
    private final SdkClient sdkClient;
    private final ThreadPool threadPool;
    private final MLIndicesHandler mlIndicesHandler;

    public MLPromptManager(Client client, SdkClient sdkClient, ThreadPool threadpool, MLIndicesHandler mlIndicesHandler) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.threadPool = threadpool;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    /**
     * Gets a prompt with the provided clients asynchronously.
     *
     * @param sdkClient The SDKClient
     * @param client The OpenSearch client for thread pool management
     * @param context The Stored Context.  Executing this method will restore this context.
     * @param getDataObjectRequest The get request
     * @param promptId The prompt ID
     * @param listener the action listener to complete with the GetResponse or Exception
     */
    public void getPromptAsync(
            SdkClient sdkClient,
            Client client,
            ThreadContext.StoredContext context,
            GetDataObjectRequest getDataObjectRequest,
            String promptId,
            ActionListener<MLPrompt> listener
    ) {
        sdkClient.
                getDataObjectAsync(getDataObjectRequest)
                .whenComplete((getAsyncResponse, throwable) -> {
                    context.restore();
                    handleAsyncResponse(getAsyncResponse, throwable, promptId, listener);
                });
    }

    private void handleAsyncResponse(GetDataObjectResponse getAsyncResponse, Throwable throwable, String promptId, ActionListener<MLPrompt> listener) {
        if (throwable != null) {
            handleThrowable(throwable, promptId, listener);
            return;
        }
        processResponse(getAsyncResponse, promptId, listener);
    }

    private void handleThrowable(Throwable throwable, String promptId, ActionListener<MLPrompt> listener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
            log.error("Failed to get prompt index", cause);
            listener.onFailure(new OpenSearchStatusException("Failed to find prompt with the provided prompt id: " + promptId, RestStatus.NOT_FOUND));
        } else {
            log.error("Failed to get ML prompt {}", promptId, cause);
            listener.onFailure(cause);
        }
    }

    private void processResponse(GetDataObjectResponse getAsyncResponse, String promptId, ActionListener<MLPrompt> listener) {
        try {
            GetResponse getResponse = getAsyncResponse.parser() == null ? null : GetResponse.fromXContent(getAsyncResponse.parser());
            if (getResponse == null || !getResponse.isExists()) {
                listener.onFailure(new OpenSearchStatusException("Failed to find prompt with the provided prompt id: " + promptId, RestStatus.NOT_FOUND));
                return;
            }
            try (
                    XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
            ) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                MLPrompt mlPrompt = MLPrompt.parse(parser);
                listener.onResponse(mlPrompt);
            }
        } catch (Exception e) {
            log.error("Failed to parse GetDataObjectResponse for prompt {}", promptId, e);
            listener.onFailure(e);
        }
    }
}
