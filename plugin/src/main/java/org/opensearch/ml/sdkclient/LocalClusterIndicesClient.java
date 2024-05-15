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
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;

/**
 * An implementation of {@link SdkClient} that stores data in a local OpenSearch cluster using the Node Client.
 */
public class LocalClusterIndicesClient implements SdkClient {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    /**
     * Instantiate this object with an OpenSearch client.
     * @param client The client to wrap
     * @param xContentRegistry the registry of XContent objects
     */
    public LocalClusterIndicesClient(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
        CompletableFuture<PutDataObjectResponse> future = new CompletableFuture<>();
        try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
            client
                .index(
                    new IndexRequest(request.index())
                        .setRefreshPolicy(IMMEDIATE)
                        .source(request.dataObject().toXContent(sourceBuilder, EMPTY_PARAMS)),
                    ActionListener
                        .wrap(
                            r -> future
                                .complete(new PutDataObjectResponse.Builder().id(r.getId()).created(r.getResult() == CREATED).build()),
                            future::completeExceptionally
                        )
                );
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request) {
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        try {
            client.get(new GetRequest(request.index(), request.id()), ActionListener.wrap(r -> {
                try {
                    XContentParser parser = jsonXContent
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getSourceAsString());
                    future.complete(new GetDataObjectResponse.Builder().id(r.getId()).parser(parser).build());
                } catch (IOException e) {
                    // Parsing error
                    future.completeExceptionally(e);
                }
            }, future::completeExceptionally));
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
        CompletableFuture<DeleteDataObjectResponse> future = new CompletableFuture<>();
        try {
            client
                .delete(
                    new DeleteRequest(request.index(), request.id()),
                    ActionListener
                        .wrap(
                            r -> future
                                .complete(
                                    new DeleteDataObjectResponse.Builder()
                                        .id(r.getId())
                                        .shardId(r.getShardId())
                                        .deleted(r.getResult() == DELETED)
                                        .build()
                                ),
                            future::completeExceptionally
                        )
                );
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
