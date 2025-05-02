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
        sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
            context.restore();
            log.debug("Completed Get Prompt Request, id:{}", promptId);
            if (throwable != null) {
                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                    log.error("Failed to get prompt index", cause);
                    listener.onFailure(new OpenSearchStatusException("Failed to find prompt", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get ML prompt {}", promptId, cause);
                    listener.onFailure(cause);
                }
            } else {
                try {
                    GetResponse gr = r.parser() == null ? null : GetResponse.fromXContent(r.parser());
                    if (gr != null && gr.isExists()) {
                        try (
                                XContentParser parser = jsonXContent
                                        .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                        ) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            MLPrompt mlPrompt = MLPrompt.parse(parser);
                            listener.onResponse(mlPrompt);
                        } catch (Exception e) {
                            log.error("Failed to parse ml prompt {}", r.id(), e);
                            listener.onFailure(e);
                        }
                    } else {
                        listener
                                .onFailure(
                                        new OpenSearchStatusException(
                                                "Failed to find prompt with the provided prompt id: " + promptId,
                                                RestStatus.NOT_FOUND
                                        )
                                );
                    }
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            }
        });
    }
}
