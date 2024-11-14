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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
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
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.sdkclient.util.JsonTransformer;
import org.opensearch.sdk.BulkDataObjectRequest;
import org.opensearch.sdk.BulkDataObjectResponse;
import org.opensearch.sdk.DataObjectRequest;
import org.opensearch.sdk.DataObjectResponse;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientDelegate;
import org.opensearch.sdk.SdkClientUtils;
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
public class RemoteClusterIndicesClient implements SdkClientDelegate {

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
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(
        PutDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try {
                IndexRequest.Builder<?> builder = new IndexRequest.Builder<>()
                    .index(request.index())
                    .opType(request.overwriteIfExists() ? OpType.Index : OpType.Create)
                    .document(request.dataObject())
                    .tDocumentSerializer(new JsonTransformer.XContentObjectJsonpSerializer());
                if (!Strings.isNullOrEmpty(request.id())) {
                    builder.id(request.id());
                }
                IndexRequest<?> indexRequest = builder.build();
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
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(
        GetDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
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
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(
        UpdateDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
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
                if (request.retryOnConflict() > 0) {
                    updateRequestBuilder.retryOnConflict(request.retryOnConflict());
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
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(
        DeleteDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
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
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(
        BulkDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<BulkDataObjectResponse>) () -> {
            try {
                log.info("Performing {} bulk actions on indices {}", request.requests().size(), request.getIndices());
                List<BulkOperation> operations = new ArrayList<>();
                for (DataObjectRequest dataObjectRequest : request.requests()) {
                    addBulkOperation(dataObjectRequest, operations);
                }
                BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).refresh(Refresh.True).build();
                BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest);
                log
                    .info(
                        "Bulk action complete for {} items: {}",
                        bulkResponse.items().size(),
                        bulkResponse.errors() ? "has failures" : "success"
                    );
                DataObjectResponse[] responses = bulkResponseItemsToArray(bulkResponse.items());
                return bulkResponse.ingestTook() == null
                    ? new BulkDataObjectResponse(responses, bulkResponse.took(), bulkResponse.errors(), createParser(bulkResponse))
                    : new BulkDataObjectResponse(
                        responses,
                        bulkResponse.took(),
                        bulkResponse.ingestTook().longValue(),
                        bulkResponse.errors(),
                        createParser(bulkResponse)
                    );
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse data object in a bulk response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }), executor);
    }

    private void addBulkOperation(DataObjectRequest dataObjectRequest, List<BulkOperation> operations) {
        if (dataObjectRequest instanceof PutDataObjectRequest) {
            addBulkPutOperation((PutDataObjectRequest) dataObjectRequest, operations);
        } else if (dataObjectRequest instanceof UpdateDataObjectRequest) {
            addBulkUpdateOperation((UpdateDataObjectRequest) dataObjectRequest, operations);
        } else if (dataObjectRequest instanceof DeleteDataObjectRequest) {
            addBulkDeleteOperation((DeleteDataObjectRequest) dataObjectRequest, operations);
        } else {
            throw new IllegalArgumentException("Invalid type for bulk request");
        }
    }

    private void addBulkPutOperation(PutDataObjectRequest putRequest, List<BulkOperation> operations) {
        if (putRequest.overwriteIfExists()) {
            // Use index operation
            operations.add(BulkOperation.of(op -> op.index(i -> {
                i
                    .index(putRequest.index())
                    .document(putRequest.dataObject())
                    .tDocumentSerializer(new JsonTransformer.XContentObjectJsonpSerializer());
                if (!Strings.isNullOrEmpty(putRequest.id())) {
                    i.id(putRequest.id());
                }
                return i;
            })));
        } else {
            // Use create operation
            operations.add(BulkOperation.of(op -> op.create(c -> {
                c
                    .index(putRequest.index())
                    .document(putRequest.dataObject())
                    .tDocumentSerializer(new JsonTransformer.XContentObjectJsonpSerializer());
                if (!Strings.isNullOrEmpty(putRequest.id())) {
                    c.id(putRequest.id());
                }
                return c;
            })));
        }
    }

    private void addBulkUpdateOperation(UpdateDataObjectRequest updateRequest, List<BulkOperation> operations) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            updateRequest.dataObject().toXContent(builder, ToXContent.EMPTY_PARAMS);
            Map<String, Object> docMap = JsonXContent.jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, builder.toString())
                .map();
            operations.add(BulkOperation.of(op -> op.update(u -> {
                u.index(updateRequest.index()).id(updateRequest.id()).document(docMap);
                if (updateRequest.ifSeqNo() != null) {
                    u.ifSeqNo(updateRequest.ifSeqNo());
                }
                if (updateRequest.ifPrimaryTerm() != null) {
                    u.ifPrimaryTerm(updateRequest.ifPrimaryTerm());
                }
                if (updateRequest.retryOnConflict() > 0) {
                    u.retryOnConflict(updateRequest.retryOnConflict());
                }
                return u;
            })));
        } catch (IOException e) {
            // Rethrow unchecked exception on XContent parsing error
            throw new OpenSearchStatusException("Failed to parse data object in a bulk update request", RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void addBulkDeleteOperation(DeleteDataObjectRequest deleteRequest, List<BulkOperation> operations) {
        operations.add(BulkOperation.of(op -> op.delete(d -> d.index(deleteRequest.index()).id(deleteRequest.id()))));
    }

    private DataObjectResponse[] bulkResponseItemsToArray(List<BulkResponseItem> items) throws IOException {
        DataObjectResponse[] responses = new DataObjectResponse[items.size()];
        int i = 0;
        for (BulkResponseItem itemResponse : items) {
            switch (itemResponse.operationType()) {
                case Index:
                case Create:
                    responses[i++] = PutDataObjectResponse
                        .builder()
                        .id(itemResponse.id())
                        .parser(createParser(itemResponse))
                        .failed(itemResponse.error() != null)
                        .build();
                    break;
                case Update:
                    responses[i++] = UpdateDataObjectResponse
                        .builder()
                        .id(itemResponse.id())
                        .parser(createParser(itemResponse))
                        .failed(itemResponse.error() != null)
                        .build();
                    break;
                case Delete:
                    responses[i++] = DeleteDataObjectResponse
                        .builder()
                        .id(itemResponse.id())
                        .parser(createParser(itemResponse))
                        .failed(itemResponse.error() != null)
                        .build();
                    break;
                default:
                    throw new OpenSearchStatusException("Invalid operation type for bulk response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return responses;
    }

    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(
        SearchDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<SearchDataObjectResponse>) () -> {
            try {
                log.info("Searching {}", Arrays.toString(request.indices()));
                // work around https://github.com/opensearch-project/opensearch-java/issues/1150
                String json = SdkClientUtils
                    .lowerCaseEnumValues(
                        MatchPhraseQueryBuilder.ZERO_TERMS_QUERY_FIELD.getPreferredName(),
                        request.searchSourceBuilder().toString()
                    );
                JsonParser parser = mapper.jsonProvider().createParser(new StringReader(json));
                SearchRequest searchRequest = SearchRequest._DESERIALIZER.deserialize(parser, mapper);
                if (Boolean.TRUE.equals(isMultiTenancyEnabled)) {
                    if (request.tenantId() == null) {
                        throw new OpenSearchStatusException("Tenant ID is required when multitenancy is enabled.", RestStatus.BAD_REQUEST);
                    }
                    TermQuery tenantIdFilterQuery = new TermQuery.Builder()
                        .field(CommonValue.TENANT_ID)
                        .value(FieldValue.of(request.tenantId()))
                        .build();
                    Query existingQuery = searchRequest.query();
                    BoolQuery boolQuery = new BoolQuery.Builder()
                        .must(existingQuery == null ? new MatchAllQuery.Builder().build().toQuery() : existingQuery)
                        .filter(tenantIdFilterQuery.toQuery())
                        .build();
                    searchRequest = searchRequest.toBuilder().index(Arrays.asList(request.indices())).query(boolQuery.toQuery()).build();
                } else {
                    searchRequest = searchRequest.toBuilder().index(Arrays.asList(request.indices())).build();
                }
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
}
