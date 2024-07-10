/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

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
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateRequest.Builder;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClientImpl;
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
public class RemoteClusterIndicesClient extends SdkClientImpl {

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
                return PutDataObjectResponse.builder().id(indexResponse.id()).parser(createParser(indexResponse)).build();
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
                log.info("Get found status for id {}: {}", getResponse.id(), getResponse.found());
                Map<String, Object> source = getResponse.source();
                return GetDataObjectResponse.builder().id(getResponse.id()).parser(createParser(getResponse)).source(source).build();
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
                Builder<Map<String, Object>, Map<String, Object>> updateRequestBuilder =
                    new UpdateRequest.Builder<Map<String, Object>, Map<String, Object>>()
                        .index(request.index())
                        .id(request.id())
                        .doc(docMap);
                if (request.ifSeqNo() != null) {
                    updateRequestBuilder.ifSeqNo(request.ifSeqNo());
                }
                if (request.ifPrimaryTerm() != null) {
                    updateRequestBuilder.ifPrimaryTerm(request.ifPrimaryTerm());
                }
                UpdateRequest<Map<String, Object>, ?> updateRequest = updateRequestBuilder.build();
                log.info("Updating {} in {}", request.id(), request.index());
                UpdateResponse<Map<String, Object>> updateResponse = openSearchClient.update(updateRequest, MAP_DOCTYPE);
                log.info("Update status for id {}: {}", updateResponse.id(), updateResponse.result());
                return UpdateDataObjectResponse.builder().id(updateResponse.id()).parser(createParser(updateResponse)).build();
            } catch (OpenSearchException ose) {
                String errorType = ose.status() == RestStatus.CONFLICT.getStatus() ? "Document Version Conflict" : "Failed";
                log.error("{} updating {} in {}: {}", errorType, request.id(), request.index(), ose.getMessage(), ose);
                // Rethrow
                throw new OpenSearchStatusException(
                    errorType + " updating " + request.id() + " in index " + request.index(),
                    RestStatus.fromCode(ose.status())
                );
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
                return DeleteDataObjectResponse.builder().id(deleteResponse.id()).parser(createParser(deleteResponse)).build();
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
                return SearchDataObjectResponse.builder().parser(createParser(searchResponse)).build();
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

    private XContentParser createParser(JsonpSerializable obj) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(stringWriter)) {
            mapper.serialize(obj, generator);
        }
        return jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, stringWriter.toString());
    }

    @Override
    public Object getImplementation() {
        return this.openSearchClient;
    }
}
