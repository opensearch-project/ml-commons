/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.opensearch.client.opensearch._types.Result.Created;
import static org.opensearch.client.opensearch._types.Result.Deleted;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.sdk.Custom;
import org.opensearch.sdk.DeleteCustomRequest;
import org.opensearch.sdk.DeleteCustomResponse;
import org.opensearch.sdk.GetCustomRequest;
import org.opensearch.sdk.GetCustomResponse;
import org.opensearch.sdk.PutCustomRequest;
import org.opensearch.sdk.PutCustomResponse;
import org.opensearch.sdk.SdkClient;

public class RemoteClusterIndicesClient implements SdkClient {

    private OpenSearchClient openSearchClient;

    public RemoteClusterIndicesClient(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
    }

    @Override
    public CompletionStage<PutCustomResponse> putCustomAsync(PutCustomRequest request) {
        CompletableFuture<PutCustomResponse> future = new CompletableFuture<>();
        IndexRequest<?> indexRequest = new IndexRequest.Builder<>().index(request.index()).document(request.custom()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                IndexResponse indexResponse = openSearchClient.index(indexRequest);
                future.complete(new PutCustomResponse.Builder().id(indexResponse.id()).created(indexResponse.result() == Created).build());
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }

    @Override
    public CompletionStage<GetCustomResponse> getCustomAsync(GetCustomRequest request) {
        CompletableFuture<GetCustomResponse> future = new CompletableFuture<>();
        GetRequest getRequest = new GetRequest.Builder().index(request.index()).id(request.id()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                GetResponse<? extends Custom> getResponse = openSearchClient.get(getRequest, request.clazz());
                future.complete(new GetCustomResponse.Builder().id(getResponse.id()).custom(getResponse.source()).build());
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }

    @Override
    public CompletionStage<DeleteCustomResponse> deleteCustomAsync(DeleteCustomRequest request) {
        CompletableFuture<DeleteCustomResponse> future = new CompletableFuture<>();
        DeleteRequest deleteRequest = new DeleteRequest.Builder().index(request.index()).id(request.id()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                DeleteResponse deleteResponse = openSearchClient.delete(deleteRequest);
                future
                    .complete(
                        new DeleteCustomResponse.Builder().id(deleteResponse.id()).deleted(deleteResponse.result() == Deleted).build()
                    );
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }
}
