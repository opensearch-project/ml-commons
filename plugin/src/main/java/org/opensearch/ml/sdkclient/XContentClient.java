/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.opensearch.action.DocWriteResponse.Result.CREATED;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteCustomRequest;
import org.opensearch.sdk.DeleteCustomResponse;
import org.opensearch.sdk.GetCustomRequest;
import org.opensearch.sdk.GetCustomResponse;
import org.opensearch.sdk.PutCustomRequest;
import org.opensearch.sdk.PutCustomResponse;
import org.opensearch.sdk.SdkClient;

public class XContentClient extends SdkClient {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    public XContentClient(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public CompletionStage<PutCustomResponse> putCustom(PutCustomRequest request) {
        CompletableFuture<PutCustomResponse> future = new CompletableFuture<>();
        try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
            client
                .index(
                    new IndexRequest(request.index())
                        .setRefreshPolicy(IMMEDIATE)
                        .source(request.custom().toXContent(sourceBuilder, EMPTY_PARAMS)),
                    ActionListener
                        .wrap(
                            r -> future.complete(new PutCustomResponse.Builder().id(r.getId()).created(r.getResult() == CREATED).build()),
                            future::completeExceptionally
                        )
                );
        } catch (IOException ioe) {
            // Parsing error
            future.completeExceptionally(ioe);
        }
        return future;
    }

    @Override
    public CompletionStage<GetCustomResponse> getCustom(GetCustomRequest request) {
        CompletableFuture<GetCustomResponse> future = new CompletableFuture<>();
        client.get(new GetRequest(request.index(), request.id()), ActionListener.wrap(r -> {
            try {
                XContentParser parser = jsonXContent
                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getSourceAsString());
                future
                    .complete(
                        new GetCustomResponse.Builder().id(r.getId()).custom(request.clazz().cast(new Object()).parse(parser)).build()
                    );
            } catch (IOException e) {
                // Parsing error
                future.completeExceptionally(e);
            }
        }, future::completeExceptionally));
        return future;
    }

    @Override
    public CompletionStage<DeleteCustomResponse> deleteCustom(DeleteCustomRequest request) {
        CompletableFuture<DeleteCustomResponse> future = new CompletableFuture<>();
        client
            .delete(
                new DeleteRequest(request.index(), request.id()),
                ActionListener
                    .wrap(
                        r -> future.complete(new DeleteCustomResponse.Builder().id(r.getId()).deleted(r.getResult() == DELETED).build()),
                        future::completeExceptionally
                    )
            );
        return future;
    }
}
