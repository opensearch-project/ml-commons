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
import static org.opensearch.client.opensearch._types.Result.Updated;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;

import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import lombok.extern.log4j.Log4j2;

/**
 * An implementation of {@link SdkClient} that stores data in a remote OpenSearch cluster using the OpenSearch Java Client.
 */
@Log4j2
public class RemoteClusterIndicesClient implements SdkClient {

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_DOCTYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;

    private OpenSearchClient openSearchClient;
    private JsonpMapper mapper;

    /**
     * Instantiate this object with an OpenSearch Java client.
     * @param openSearchClient The client to wrap
     */
    public RemoteClusterIndicesClient(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
        this.mapper = openSearchClient._transport().jsonpMapper();
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
            } catch (IOException e) {
                log.error("Error putting data object in {}: {}", request.index(), e.getMessage(), e);
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException(
                    "Failed to parse data object to put in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                GetRequest getRequest = new GetRequest.Builder().index(request.index()).id(request.id()).build();
                log.info("Getting {} from {}", request.id(), request.index());
                GetResponse<Map<String, Object>> getResponse = openSearchClient.get(getRequest, MAP_DOCTYPE);
                Map<String, Object> source = getResponse.source();
                return new GetDataObjectResponse.Builder()
                    .id(getResponse.id())
                    .parser(jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, toJson(getResponse)))
                    .source(source)
                    .build();
            } catch (IOException e) {
                log.error("Error getting data object {} from {}: {}", request.id(), request.index(), e.getMessage(), e);
                // Rethrow unchecked exception on XContent parser creation error
                throw new OpenSearchStatusException(
                    "Failed to create parser for data object retrieved from index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<UpdateDataObjectResponse>) () -> {
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                request.dataObject().toXContent(builder, ToXContent.EMPTY_PARAMS);
                Map<String, Object> docMap = JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, builder.toString())
                    .map();
                UpdateRequest<Map<String, Object>, ?> updateRequest = new UpdateRequest.Builder<Map<String, Object>, Map<String, Object>>()
                    .index(request.index())
                    .id(request.id())
                    .doc(docMap)
                    .build();
                log.info("Updating {} in {}", request.id(), request.index());
                UpdateResponse<Map<String, Object>> updateResponse = openSearchClient.update(updateRequest, MAP_DOCTYPE);
                log.info("Update status for id {}: {}", updateResponse.id(), updateResponse.result());
                ShardInfo shardInfo = new ShardInfo(
                    updateResponse.shards().total().intValue(),
                    updateResponse.shards().successful().intValue()
                );
                return new UpdateDataObjectResponse.Builder()
                    .id(updateResponse.id())
                    .shardId(updateResponse.index())
                    .shardInfo(shardInfo)
                    .updated(updateResponse.result() == Updated)
                    .build();
            } catch (IOException e) {
                log.error("Error updating {} in {}: {}", request.id(), request.index(), e.getMessage(), e);
                // Rethrow unchecked exception on update IOException
                throw new OpenSearchStatusException(
                    "Parsing error updating data object " + request.id() + " in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
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
            } catch (IOException e) {
                log.error("Error deleting {} from {}: {}", request.id(), request.index(), e.getMessage(), e);
                // Rethrow unchecked exception on deletion IOException
                throw new OpenSearchStatusException(
                    "IOException occurred while deleting data object " + request.id() + " from index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<SearchDataObjectResponse>) () -> {
            try {
                log.info("Searching {}", Arrays.toString(request.indices()), null);
                JsonParser parser = mapper.jsonProvider().createParser(new StringReader(request.searchSourceBuilder().toString()));
                SearchRequest searchRequest = SearchRequest._DESERIALIZER.deserialize(parser, mapper);
                searchRequest = searchRequest.toBuilder().index(Arrays.asList(request.indices())).build();

                SearchResponse<?> searchResponse = openSearchClient.search(searchRequest, MAP_DOCTYPE);
                log.info("Search returned {} hits", searchResponse.hits().total().value());
                return new SearchDataObjectResponse.Builder()
                    .parser(
                        jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, toJson(searchResponse))
                    )
                    .build();
            } catch (IOException e) {
                log.error("Error searching {}: {}", Arrays.toString(request.indices()), e.getMessage(), e);
                // Rethrow unchecked exception on exception
                throw new OpenSearchStatusException(
                    "Failed to search indices " + Arrays.toString(request.indices()),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    private String toJson(JsonpSerializable obj) {
        StringWriter stringWriter = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(stringWriter)) {
            mapper.serialize(obj, generator);
        }
        return stringWriter.toString();
    }
}
