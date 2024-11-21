/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.sdk.AbstractSdkClient;
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
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;
import org.opensearch.search.builder.SearchSourceBuilder;

import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import lombok.extern.log4j.Log4j2;

/**
 * An implementation of {@link SdkClient} that stores data in a local OpenSearch
 * cluster using the Node Client.
 */
@Log4j2
public class LocalClusterIndicesClient extends AbstractSdkClient {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    /**
     * Instantiate this object with an OpenSearch client.
     * 
     * @param client           The client to wrap
     * @param xContentRegistry the registry of XContent objects
     */
    @Inject
    public LocalClusterIndicesClient(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(
        PutDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            try {
                log.info("Indexing data object in {}", request.index());
                IndexRequest indexRequest = createIndexRequest(request).setRefreshPolicy(IMMEDIATE);
                IndexResponse indexResponse = client.index(indexRequest).actionGet();
                log.info("Creation status for id {}: {}", indexResponse.getId(), indexResponse.getResult());
                return PutDataObjectResponse.builder().id(indexResponse.getId()).parser(createParser(indexResponse)).build();
            } catch (IOException e) {
                throw new OpenSearchStatusException(
                    "Failed to parse data object to put in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }, executor);
    }

    private IndexRequest createIndexRequest(PutDataObjectRequest putDataObjectRequest) throws IOException {
        try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
            IndexRequest indexRequest = new IndexRequest(putDataObjectRequest.index())
                .opType(putDataObjectRequest.overwriteIfExists() ? OpType.INDEX : OpType.CREATE)
                .source(putDataObjectRequest.dataObject().toXContent(sourceBuilder, EMPTY_PARAMS));
            if (!Strings.isNullOrEmpty(putDataObjectRequest.id())) {
                indexRequest.id(putDataObjectRequest.id());
            }
            return indexRequest;
        }
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(
        GetDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            try {
                GetResponse getResponse = client
                    .get(new GetRequest(request.index(), request.id()).fetchSourceContext(request.fetchSourceContext()))
                    .actionGet();
                if (getResponse == null) {
                    return GetDataObjectResponse.builder().id(request.id()).parser(null).build();
                }
                return GetDataObjectResponse
                    .builder()
                    .id(getResponse.getId())
                    .parser(createParser(getResponse))
                    .source(getResponse.getSource())
                    .build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parser creation error
                throw new OpenSearchStatusException(
                    "Failed to create parser for data object retrieved from index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }, executor);
    }

    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(
        UpdateDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            try {
                log.info("Updating {} from {}", request.id(), request.index());
                UpdateRequest updateRequest = createUpdateRequest(request);
                UpdateResponse updateResponse = client.update(updateRequest).actionGet();
                if (updateResponse == null) {
                    log.info("Null UpdateResponse");
                    return UpdateDataObjectResponse.builder().id(request.id()).parser(null).build();
                }
                log.info("Update status for id {}: {}", updateResponse.getId(), updateResponse.getResult());
                return UpdateDataObjectResponse.builder().id(updateResponse.getId()).parser(createParser(updateResponse)).build();
            } catch (VersionConflictEngineException vcee) {
                log.error("Document version conflict updating {} in {}: {}", request.id(), request.index(), vcee.getMessage(), vcee);
                throw new OpenSearchStatusException(
                    "Document version conflict updating " + request.id() + " in index " + request.index(),
                    RestStatus.CONFLICT
                );
            } catch (IOException e) {
                throw new OpenSearchStatusException(
                    "Failed to parse data object to update in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }, executor);
    }

    private UpdateRequest createUpdateRequest(UpdateDataObjectRequest updateDataObjectRequest) throws IOException {
        try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
            UpdateRequest updateRequest = new UpdateRequest(updateDataObjectRequest.index(), updateDataObjectRequest.id())
                .doc(updateDataObjectRequest.dataObject().toXContent(sourceBuilder, EMPTY_PARAMS));
            if (updateDataObjectRequest.ifSeqNo() != null) {
                updateRequest.setIfSeqNo(updateDataObjectRequest.ifSeqNo());
            }
            if (updateDataObjectRequest.ifPrimaryTerm() != null) {
                updateRequest.setIfPrimaryTerm(updateDataObjectRequest.ifPrimaryTerm());
            }
            if (updateDataObjectRequest.retryOnConflict() > 0) {
                updateRequest.retryOnConflict(updateDataObjectRequest.retryOnConflict());
            }
            return updateRequest;
        }
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(
        DeleteDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            try {
                log.info("Deleting {} from {}", request.id(), request.index());
                DeleteRequest deleteRequest = createDeleteRequest(request).setRefreshPolicy(IMMEDIATE);
                DeleteResponse deleteResponse = client.delete(deleteRequest).actionGet();
                log.info("Deletion status for id {}: {}", deleteResponse.getId(), deleteResponse.getResult());
                return DeleteDataObjectResponse.builder().id(deleteResponse.getId()).parser(createParser(deleteResponse)).build();
            } catch (IOException e) {
                throw new OpenSearchStatusException(
                    "Failed to parse data object to deletion response in index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }, executor);
    }

    private DeleteRequest createDeleteRequest(DeleteDataObjectRequest deleteDataObjectRequest) {
        return new DeleteRequest(deleteDataObjectRequest.index(), deleteDataObjectRequest.id());
    }

    @Override
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(
        BulkDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            try {
                log.info("Performing {} bulk actions on indices {}", request.requests().size(), request.getIndices());
                BulkRequest bulkRequest = new BulkRequest();

                for (DataObjectRequest dataObjectRequest : request.requests()) {
                    if (dataObjectRequest instanceof PutDataObjectRequest) {
                        bulkRequest.add(createIndexRequest((PutDataObjectRequest) dataObjectRequest));
                    } else if (dataObjectRequest instanceof UpdateDataObjectRequest) {
                        bulkRequest.add(createUpdateRequest((UpdateDataObjectRequest) dataObjectRequest));
                    } else if (dataObjectRequest instanceof DeleteDataObjectRequest) {
                        bulkRequest.add(createDeleteRequest((DeleteDataObjectRequest) dataObjectRequest));
                    }
                }

                BulkResponse bulkResponse = client.bulk(bulkRequest.setRefreshPolicy(IMMEDIATE)).actionGet();
                return bulkResponseToDataObjectResponse(bulkResponse);
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse data object in a bulk response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }, executor);
    }

    private BulkDataObjectResponse bulkResponseToDataObjectResponse(BulkResponse bulkResponse) throws IOException {
        int responseCount = bulkResponse.getItems().length;
        log.info("Bulk action complete for {} items: {}", responseCount, bulkResponse.hasFailures() ? "has failures" : "success");
        DataObjectResponse[] responses = new DataObjectResponse[responseCount];
        for (int i = 0; i < responseCount; i++) {
            BulkItemResponse itemResponse = bulkResponse.getItems()[i];
            responses[i] = createDataObjectResponse(itemResponse);
        }
        return new BulkDataObjectResponse(
            responses,
            bulkResponse.getTook().millis(),
            bulkResponse.getIngestTookInMillis(),
            bulkResponse.hasFailures(),
            createParser(bulkResponse)
        );
    }

    private DataObjectResponse createDataObjectResponse(BulkItemResponse itemResponse) throws IOException {
        switch (itemResponse.getOpType()) {
            case INDEX:
            case CREATE:
                return PutDataObjectResponse
                    .builder()
                    .id(itemResponse.getId())
                    .parser(createParser(itemResponse))
                    .failed(itemResponse.isFailed())
                    .build();
            case UPDATE:
                return UpdateDataObjectResponse
                    .builder()
                    .id(itemResponse.getId())
                    .parser(createParser(itemResponse))
                    .failed(itemResponse.isFailed())
                    .build();
            case DELETE:
                return DeleteDataObjectResponse
                    .builder()
                    .id(itemResponse.getId())
                    .parser(createParser(itemResponse))
                    .failed(itemResponse.isFailed())
                    .build();
            default:
                throw new OpenSearchStatusException("Invalid operation type for bulk response", RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(
        SearchDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        SearchSourceBuilder searchSource = request.searchSourceBuilder();
        if (Boolean.TRUE.equals(isMultiTenancyEnabled)) {
            if (request.tenantId() == null) {
                return CompletableFuture
                    .failedFuture(
                        new OpenSearchStatusException("Tenant ID is required when multitenancy is enabled.", RestStatus.BAD_REQUEST)
                    );
            }
            QueryBuilder existingQuery = searchSource.query();
            TermQueryBuilder tenantIdTermQuery = QueryBuilders.termQuery(CommonValue.TENANT_ID, request.tenantId());
            if (existingQuery == null) {
                searchSource.query(tenantIdTermQuery);
            } else {
                BoolQueryBuilder boolQuery = existingQuery instanceof BoolQueryBuilder
                    ? (BoolQueryBuilder) existingQuery
                    : QueryBuilders.boolQuery().must(existingQuery);
                boolQuery.filter(tenantIdTermQuery);
                searchSource.query(boolQuery);
            }
            log.debug("Adding tenant id to search query", Arrays.toString(request.indices()));
        }
        log.info("Searching {}", Arrays.toString(request.indices()));
        return executePrivilegedAsync(() -> client.search(new SearchRequest(request.indices(), searchSource)), executor)
            .thenCompose(searchResponseFuture -> CompletableFuture.supplyAsync(searchResponseFuture::actionGet, executor))
            .thenApply(searchResponse -> {
                log.info("Search returned {} hits", searchResponse.getHits().getTotalHits());
                try {
                    return SearchDataObjectResponse.builder().parser(createParser(searchResponse)).build();
                } catch (IOException e) {
                    // Rethrow unchecked exception on XContent parsing error
                    throw new OpenSearchStatusException(
                        "Failed to search indices " + Arrays.toString(request.indices()),
                        RestStatus.INTERNAL_SERVER_ERROR
                    );
                }
            });
    }

    private XContentParser createParser(ToXContent obj) throws IOException {
        return jsonXContent
            .createParser(xContentRegistry, DeprecationHandler.IGNORE_DEPRECATIONS, Strings.toString(MediaTypeRegistry.JSON, obj));
    }
}
