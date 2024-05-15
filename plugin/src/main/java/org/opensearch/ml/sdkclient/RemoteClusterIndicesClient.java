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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;

/**
 * An implementation of {@link SdkClient} that stores data in a remote OpenSearch cluster using the OpenSearch Java Client.
 */
public class RemoteClusterIndicesClient implements SdkClient {

    private OpenSearchClient openSearchClient;

    /**
     * Instantiate this object with an OpenSearch Java client.
     * @param openSearchClient The client to wrap
     */
    public RemoteClusterIndicesClient(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
    }

    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
        CompletableFuture<PutDataObjectResponse> future = new CompletableFuture<>();
        IndexRequest<?> indexRequest = new IndexRequest.Builder<>().index(request.index()).document(request.dataObject()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                IndexResponse indexResponse = openSearchClient.index(indexRequest);
                future
                    .complete(
                        new PutDataObjectResponse.Builder().id(indexResponse.id()).created(indexResponse.result() == Created).build()
                    );
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request) {
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        GetRequest getRequest = new GetRequest.Builder().index(request.index()).id(request.id()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                @SuppressWarnings("rawtypes")
                GetResponse<Map> getResponse = openSearchClient.get(getRequest, Map.class);
                String source = getResponse.fields().get("_source").toJson().toString();
                XContentParser parser = JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, source);
                future.complete(new GetDataObjectResponse.Builder().id(getResponse.id()).parser(parser).build());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
        CompletableFuture<DeleteDataObjectResponse> future = new CompletableFuture<>();
        DeleteRequest deleteRequest = new DeleteRequest.Builder().index(request.index()).id(request.id()).build();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                DeleteResponse deleteResponse = openSearchClient.delete(deleteRequest);
                future
                    .complete(
                        new DeleteDataObjectResponse.Builder().id(deleteResponse.id()).deleted(deleteResponse.result() == Deleted).build()
                    );
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return null;
        });
        return future;
    }
}
