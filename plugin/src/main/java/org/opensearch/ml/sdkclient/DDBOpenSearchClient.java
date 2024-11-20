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
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.sdkclient.util.JsonTransformer;
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
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/**
 * DDB implementation of {@link SdkClient}. DDB table name will be mapped to index name.
 *
 */
@Log4j2
public class DDBOpenSearchClient extends AbstractSdkClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Long DEFAULT_SEQUENCE_NUMBER = 0L;
    private static final Long DEFAULT_PRIMARY_TERM = 1L;
    private static final String RANGE_KEY = "_id";
    private static final String HASH_KEY = "_tenant_id";

    private static final String SOURCE = "_source";
    private static final String SEQ_NO_KEY = "_seq_no";

    // TENANT_ID hash key requires non-null value
    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private DynamoDbClient dynamoDbClient;
    private RemoteClusterIndicesClient remoteClusterIndicesClient;

    /**
     * Default constructor
     *
     * @param dynamoDbClient AWS DDB client to perform CRUD operations on a DDB table.
     * @param remoteClusterIndicesClient Remote opensearch client to perform search operations. Documents written to DDB
     *                                  needs to be synced offline with remote opensearch.
     */
    public DDBOpenSearchClient(DynamoDbClient dynamoDbClient, RemoteClusterIndicesClient remoteClusterIndicesClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.remoteClusterIndicesClient = remoteClusterIndicesClient;
    }

    /**
     * DDB implementation to write data objects to DDB table. Tenant ID will be used as hash key and document ID will
     * be used as range key. If tenant ID is not defined a default tenant ID will be used. If document ID is not defined
     * a random UUID will be generated. Data object will be written as a nested DDB attribute.
     *
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(
        PutDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        final String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final String tableName = request.index();
        final GetItemRequest getItemRequest = buildGetItemRequest(tenantId, id, request.index());
        return executePrivilegedAsync(() -> {
            try {
                GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
                Long sequenceNumber = initOrIncrementSeqNo(getItemResponse);
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> sourceMap = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
                if (request.tenantId() != null) {
                    sourceMap.put(TENANT_ID, AttributeValue.builder().s(tenantId).build());
                }
                Map<String, AttributeValue> item = new HashMap<>();
                item.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
                item.put(RANGE_KEY, AttributeValue.builder().s(id).build());
                item.put(SOURCE, AttributeValue.builder().m(sourceMap).build());
                item.put(SEQ_NO_KEY, AttributeValue.builder().n(sequenceNumber.toString()).build());
                Builder builder = PutItemRequest.builder().tableName(tableName).item(item);
                if (!request.overwriteIfExists()
                    && getItemResponse != null
                    && getItemResponse.item() != null
                    && !getItemResponse.item().isEmpty()) {
                    throw new OpenSearchStatusException("Existing data object for ID: " + request.id(), RestStatus.CONFLICT);
                }
                final PutItemRequest putItemRequest = builder.build();

                dynamoDbClient.putItem(putItemRequest);
                String simulatedIndexResponse = simulateOpenSearchResponse(
                    request.index(),
                    id,
                    source,
                    sequenceNumber,
                    Map.of("result", "created")
                );
                return PutDataObjectResponse.builder().id(id).parser(createParser(simulatedIndexResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse data object  " + request.id(), RestStatus.BAD_REQUEST);
            }
        }, executor);
    }

    /**
     * Fetches data document from DDB. Default tenant ID will be used if tenant ID is not specified.
     *
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(
        GetDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        final GetItemRequest getItemRequest = buildGetItemRequest(request.tenantId(), request.id(), request.index());
        return executePrivilegedAsync(() -> {
            try {
                final GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
                ObjectNode sourceObject;
                boolean found;
                String sequenceNumberString = null;
                if (getItemResponse == null || getItemResponse.item() == null || getItemResponse.item().isEmpty()) {
                    found = false;
                    sourceObject = null;
                } else {
                    found = true;
                    sourceObject = JsonTransformer.convertDDBAttributeValueMapToObjectNode(getItemResponse.item().get(SOURCE).m());
                    if (getItemResponse.item().containsKey(SEQ_NO_KEY)) {
                        sequenceNumberString = getItemResponse.item().get(SEQ_NO_KEY).n();
                    }
                }
                final String source = OBJECT_MAPPER.writeValueAsString(sourceObject);
                final Long sequenceNumber = sequenceNumberString == null || sequenceNumberString.isEmpty()
                    ? null
                    : Long.parseLong(sequenceNumberString);
                String simulatedGetResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    source,
                    sequenceNumber,
                    Map.of("found", found)
                );
                XContentParser parser = JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, simulatedGetResponse);
                // This would consume parser content so we need to create a new parser for the map
                Map<String, Object> sourceAsMap = GetResponse
                    .fromXContent(
                        JsonXContent.jsonXContent
                            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, simulatedGetResponse)
                    )
                    .getSourceAsMap();
                return GetDataObjectResponse.builder().id(request.id()).parser(parser).source(sourceAsMap).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }, executor);
    }

    /**
     * Makes use of DDB update request to update data object.
     *
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(
        UpdateDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        return executePrivilegedAsync(() -> {
            try {
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);

                Long sequenceNumber = updateItemWithRetryOnConflict(tenantId, jsonNode, request);
                String simulatedUpdateResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    source,
                    sequenceNumber,
                    Map.of("result", "updated")
                );
                return UpdateDataObjectResponse.builder().id(request.id()).parser(createParser(simulatedUpdateResponse)).build();
            } catch (IOException e) {
                log.error("Error updating {} in {}: {}", request.id(), request.index(), e.getMessage(), e);
                // Rethrow unchecked exception on update IOException
                throw new OpenSearchStatusException(
                    "Parsing error updating data object " + request.id() + " in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }, executor);
    }

    private Long updateItemWithRetryOnConflict(String tenantId, JsonNode jsonNode, UpdateDataObjectRequest request) {
        Map<String, AttributeValue> updateItem = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
        updateItem.remove(TENANT_ID);
        updateItem.remove(RANGE_KEY);
        Map<String, AttributeValue> updateKey = new HashMap<>();
        updateKey.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
        updateKey.put(RANGE_KEY, AttributeValue.builder().s(request.id()).build());
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#seqNo", SEQ_NO_KEY);
        expressionAttributeNames.put("#source", SOURCE);
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":incr", AttributeValue.builder().n("1").build());
        int retriesRemaining = request.retryOnConflict();
        do {
            try {
                // Fetch current item and extract data object
                Map<String, AttributeValue> currentItem = dynamoDbClient
                    .getItem(GetItemRequest.builder().tableName(request.index()).key(updateKey).build())
                    .item();
                Map<String, AttributeValue> dataObject = new HashMap<>(currentItem.get(SOURCE).m());
                // Update existing with changes
                dataObject.putAll(updateItem);
                expressionAttributeValues.put(":source", AttributeValue.builder().m(dataObject).build());
                // Use seqNo from the object we got to make sure we're updating the same thing
                if (request.ifSeqNo() != null) {
                    expressionAttributeValues.put(":currentSeqNo", AttributeValue.builder().n(Long.toString(request.ifSeqNo())).build());
                } else {
                    expressionAttributeValues.put(":currentSeqNo", currentItem.get(SEQ_NO_KEY));
                }
                UpdateItemRequest.Builder updateItemRequestBuilder = UpdateItemRequest.builder().tableName(request.index()).key(updateKey);
                updateItemRequestBuilder.updateExpression("SET #seqNo = #seqNo + :incr, #source = :source ");
                updateItemRequestBuilder.conditionExpression("#seqNo = :currentSeqNo");
                updateItemRequestBuilder
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues);
                UpdateItemRequest updateItemRequest = updateItemRequestBuilder.build();
                UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);
                if (updateItemResponse != null
                    && updateItemResponse.attributes() != null
                    && updateItemResponse.attributes().containsKey(SEQ_NO_KEY)) {
                    return Long.parseLong(updateItemResponse.attributes().get(SEQ_NO_KEY).n());
                }
            } catch (ConditionalCheckFailedException ccfe) {
                if (retriesRemaining < 1) {
                    // Throw exception if retries exhausted
                    String message = "Document version conflict updating " + request.id() + " in index " + request.index();
                    log.error(message + ": {}", ccfe.getMessage(), ccfe);
                    throw new OpenSearchStatusException(message, RestStatus.CONFLICT);
                }
            }
        } while (retriesRemaining-- > 0);
        return null; // Should never get here
    }

    /**
     * Deletes data document from DDB. Default tenant ID will be used if tenant ID is not specified.
     *
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(
        DeleteDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final DeleteItemRequest deleteItemRequest = DeleteItemRequest
            .builder()
            .tableName(request.index())
            .key(
                Map
                    .ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                    )
            )
            .build();
        return executePrivilegedAsync(() -> {
            try {
                DeleteItemResponse deleteItemResponse = dynamoDbClient.deleteItem(deleteItemRequest);
                Long sequenceNumber = null;
                if (deleteItemResponse.attributes() != null && deleteItemResponse.attributes().containsKey(SEQ_NO_KEY)) {
                    sequenceNumber = Long.parseLong(deleteItemResponse.attributes().get(SEQ_NO_KEY).n()) + 1;
                }
                String simulatedDeleteResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    null,
                    sequenceNumber,
                    Map.of("result", "deleted")
                );
                return DeleteDataObjectResponse.builder().id(request.id()).parser(createParser(simulatedDeleteResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }, executor);
    }

    @Override
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(
        BulkDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return executePrivilegedAsync(() -> {
            log.info("Performing {} bulk actions on table {}", request.requests().size(), request.getIndices());

            List<DataObjectResponse> responses = new ArrayList<>();

            // TODO: Ideally if we only have put and delete requests we can use DynamoDB BatchWriteRequest.
            long startNanos = System.nanoTime();
            for (DataObjectRequest dataObjectRequest : request.requests()) {
                try {
                    if (dataObjectRequest instanceof PutDataObjectRequest) {
                        responses
                            .add(
                                putDataObjectAsync((PutDataObjectRequest) dataObjectRequest, executor, isMultiTenancyEnabled)
                                    .toCompletableFuture()
                                    .join()
                            );
                    } else if (dataObjectRequest instanceof UpdateDataObjectRequest) {
                        responses
                            .add(
                                updateDataObjectAsync((UpdateDataObjectRequest) dataObjectRequest, executor, isMultiTenancyEnabled)
                                    .toCompletableFuture()
                                    .join()
                            );
                    } else if (dataObjectRequest instanceof DeleteDataObjectRequest) {
                        responses
                            .add(
                                deleteDataObjectAsync((DeleteDataObjectRequest) dataObjectRequest, executor, isMultiTenancyEnabled)
                                    .toCompletableFuture()
                                    .join()
                            );
                    }
                } catch (CompletionException e) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(e);
                    RestStatus status = ExceptionsHelper.status(cause);
                    if (dataObjectRequest instanceof PutDataObjectRequest) {
                        responses
                            .add(
                                new PutDataObjectResponse.Builder()
                                    .index(dataObjectRequest.index())
                                    .id(dataObjectRequest.id())
                                    .failed(true)
                                    .cause(cause)
                                    .status(status)
                                    .build()
                            );
                    } else if (dataObjectRequest instanceof UpdateDataObjectRequest) {
                        responses
                            .add(
                                new UpdateDataObjectResponse.Builder()
                                    .index(dataObjectRequest.index())
                                    .id(dataObjectRequest.id())
                                    .failed(true)
                                    .cause(cause)
                                    .status(status)
                                    .build()
                            );
                    } else if (dataObjectRequest instanceof DeleteDataObjectRequest) {
                        responses
                            .add(
                                new DeleteDataObjectResponse.Builder()
                                    .index(dataObjectRequest.index())
                                    .id(dataObjectRequest.id())
                                    .failed(true)
                                    .cause(cause)
                                    .status(status)
                                    .build()
                            );
                    }
                    log.error("Error in bulk operation for id {}: {}", dataObjectRequest.id(), e.getCause().getMessage(), e.getCause());
                }
            }
            long endNanos = System.nanoTime();
            long tookMillis = TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);

            log.info("Bulk action complete for {} items, took {} ms", responses.size(), tookMillis);
            return buildBulkDataObjectResponse(responses, tookMillis);
        }, executor);
    }

    private BulkDataObjectResponse buildBulkDataObjectResponse(List<DataObjectResponse> responses, long tookMillis) {
        // Reconstruct BulkResponse to leverage its parser and hasFailed methods
        BulkItemResponse[] responseArray = new BulkItemResponse[responses.size()];
        try {
            for (int id = 0; id < responses.size(); id++) {
                responseArray[id] = buildBulkItemResponse(responses, id);
            }
            BulkResponse br = new BulkResponse(responseArray, tookMillis);
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                br.toXContent(builder, ToXContent.EMPTY_PARAMS);
                return new BulkDataObjectResponse(
                    responses.toArray(new DataObjectResponse[0]),
                    tookMillis,
                    br.hasFailures(),
                    createParser(builder.toString())
                );
            }
        } catch (IOException e) {
            // Rethrow unchecked exception on XContent parsing error
            throw new OpenSearchStatusException("Failed to parse bulk response", RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BulkItemResponse buildBulkItemResponse(List<DataObjectResponse> responses, int bulkId) throws IOException {
        DataObjectResponse response = responses.get(bulkId);
        OpType opType = null;
        if (response instanceof PutDataObjectResponse) {
            opType = OpType.INDEX;
        } else if (response instanceof UpdateDataObjectResponse) {
            opType = OpType.UPDATE;
        } else if (response instanceof DeleteDataObjectResponse) {
            opType = OpType.DELETE;
        }
        // If failed, parser is null, so shortcut response here
        if (response.isFailed()) {
            return new BulkItemResponse(bulkId, opType, new BulkItemResponse.Failure(response.index(), response.id(), response.cause()));
        }
        DocWriteResponse writeResponse = null;
        if (response instanceof PutDataObjectResponse) {
            writeResponse = IndexResponse.fromXContent(response.parser());
        } else if (response instanceof UpdateDataObjectResponse) {
            writeResponse = UpdateResponse.fromXContent(response.parser());
        } else if (response instanceof DeleteDataObjectResponse) {
            writeResponse = DeleteResponse.fromXContent(response.parser());
        }
        return new BulkItemResponse(bulkId, opType, writeResponse);
    }

    /**
     * DDB data needs to be synced with opensearch cluster. {@link RemoteClusterIndicesClient} will then be used to
     * search data in opensearch cluster.
     *
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(
        SearchDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        List<String> indices = Arrays.stream(request.indices()).map(this::getIndexName).collect(Collectors.toList());

        SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest(
            indices.toArray(new String[0]),
            request.tenantId(),
            request.searchSourceBuilder()
        );
        return this.remoteClusterIndicesClient.searchDataObjectAsync(searchDataObjectRequest, executor, isMultiTenancyEnabled);
    }

    private String getIndexName(String index) {
        // System index is not supported in remote index. Replacing '.' from index name.
        return (index.length() > 1 && index.charAt(0) == '.') ? index.substring(1) : index;
    }

    private XContentParser createParser(String json) throws IOException {
        return jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, json);
    }

    private GetItemRequest buildGetItemRequest(String requestTenantId, String documentId, String index) {
        final String tenantId = requestTenantId != null ? requestTenantId : DEFAULT_TENANT;
        return GetItemRequest
            .builder()
            .tableName(index)
            .key(
                Map
                    .ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(documentId).build())
                    )
            )
            .build();
    }

    private Long initOrIncrementSeqNo(GetItemResponse getItemResponse) {
        Long sequenceNumber = DEFAULT_SEQUENCE_NUMBER;
        if (getItemResponse != null && getItemResponse.item() != null && getItemResponse.item().containsKey(SEQ_NO_KEY)) {
            sequenceNumber = Long.parseLong(getItemResponse.item().get(SEQ_NO_KEY).n()) + 1;
        }
        return sequenceNumber;
    }

    private String simulateOpenSearchResponse(
        String index,
        String id,
        String source,
        Long sequenceNumber,
        Map<String, Object> additionalFields
    ) {
        Long seqNo = UNASSIGNED_SEQ_NO;
        Long primaryTerm = UNASSIGNED_PRIMARY_TERM;
        if (sequenceNumber != null) {
            seqNo = sequenceNumber;
            primaryTerm = DEFAULT_PRIMARY_TERM;
        }
        StringBuilder sb = new StringBuilder("{");
        // Fields with a DDB counterpart
        sb.append("\"_index\":\"").append(index).append("\",");
        sb.append("\"_id\":\"").append(id).append("\",");
        // Fields we must simulate using default values
        sb.append("\"_primary_term\":").append(primaryTerm).append(",");
        sb.append("\"_seq_no\":").append(seqNo).append(",");
        sb.append("\"_version\":").append(-1).append(",");
        sb.append("\"_shards\":").append(Strings.toString(MediaTypeRegistry.JSON, new ShardInfo())).append(",");
        // Finish up
        additionalFields
            .entrySet()
            .stream()
            .forEach(
                e -> sb
                    .append("\"")
                    .append(e.getKey())
                    .append("\":")
                    .append(e.getValue() instanceof String ? ("\"" + e.getValue() + "\"") : e.getValue())
                    .append(",")
            );
        sb.append("\"_source\":").append(source).append("}");
        return sb.toString();
    }
}
