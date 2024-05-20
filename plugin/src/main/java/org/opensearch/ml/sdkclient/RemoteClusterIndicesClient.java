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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchException;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * An implementation of {@link SdkClient} that stores data in a remote OpenSearch cluster using the OpenSearch Java Client.
 */
@Log4j2
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
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try {
                IndexRequest<?> indexRequest = new IndexRequest.Builder<>().index(request.index()).document(request.dataObject()).build();
                log.info("Indexing data object in {}", request.index());
                IndexResponse indexResponse = openSearchClient.index(indexRequest);
                log.info("Creation status for id {}: {}", indexResponse.id(), indexResponse.result());
                return new PutDataObjectResponse.Builder().id(indexResponse.id()).created(indexResponse.result() == Created).build();
            } catch (Exception e) {
                throw new OpenSearchException("Error occurred while indexing data object", e);
            }
        }), executor);
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                GetRequest getRequest = new GetRequest.Builder().index(request.index()).id(request.id()).build();
                log.info("Getting {} from {}", request.id(), request.index());
                @SuppressWarnings("rawtypes")
                GetResponse<Map> getResponse = openSearchClient.get(getRequest, Map.class);
                if (!getResponse.found()) {
                    return new GetDataObjectResponse.Builder().id(getResponse.id()).build();
                }
                String json = new ObjectMapper().setSerializationInclusion(Include.NON_NULL).writeValueAsString(getResponse.source());
                log.info("Retrieved data object");
                XContentParser parser = JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
                return new GetDataObjectResponse.Builder().id(getResponse.id()).parser(Optional.of(parser)).build();
            } catch (Exception e) {
                throw new OpenSearchException(e);
            }
        }), executor);
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
            try {
                DeleteRequest deleteRequest = new DeleteRequest.Builder().index(request.index()).id(request.id()).build();
                log.info("Deleting {} from {}", request.id(), request.index());
                DeleteResponse deleteResponse = openSearchClient.delete(deleteRequest);
                log.info("Deletion status for id {}: {}", deleteResponse.id(), deleteResponse.result());
                ShardInfo shardInfo = new ShardInfo(
                    deleteResponse.shards().total().intValue(),
                    deleteResponse.shards().successful().intValue()
                );
                return new DeleteDataObjectResponse.Builder()
                    .id(deleteResponse.id())
                    .shardId(deleteResponse.index())
                    .shardInfo(shardInfo)
                    .deleted(deleteResponse.result() == Deleted)
                    .build();
            } catch (Exception e) {
                throw new OpenSearchException("Error occurred while deleting data object", e);
            }
        }), executor);
    }
}
