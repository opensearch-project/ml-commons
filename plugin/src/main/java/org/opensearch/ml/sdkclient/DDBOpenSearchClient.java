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

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.sdkclient.util.JsonTransformer;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientDelegate;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
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
public class DDBOpenSearchClient implements SdkClientDelegate {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Long DEFAULT_SEQUENCE_NUMBER = 0L;
    private static final Long DEFAULT_PRIMARY_TERM = 1L;
    private static final String RANGE_KEY = "_id";

    private static final String SOURCE = "_source";
    private static final String SEQ_NO_KEY = "_seq_no";

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
        final String tenantId = request.tenantId() != null ? request.tenantId() : CommonValue.DEFAULT_TENANT;
        final String tableName = request.index();
        final GetItemRequest getItemRequest = buildGetItemRequest(tenantId, id, request.index());
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try {
                GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
                Long sequenceNumber = initOrIncrementSeqNo(getItemResponse);
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> sourceMap = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
                Map<String, AttributeValue> item = new HashMap<>();
                item.put(CommonValue.TENANT_ID, AttributeValue.builder().s(tenantId).build());
                item.put(RANGE_KEY, AttributeValue.builder().s(id).build());
                item.put(SOURCE, AttributeValue.builder().m(sourceMap).build());
                item.put(SEQ_NO_KEY, AttributeValue.builder().n(sequenceNumber.toString()).build());
                Builder builder = PutItemRequest.builder().tableName(tableName).item(item);
                if (!request.overwriteIfExists() && getItemResponse != null && getItemResponse.item() != null) {
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
        }), executor);
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
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
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
        }), executor);
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
        final String tenantId = request.tenantId() != null ? request.tenantId() : CommonValue.DEFAULT_TENANT;
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<UpdateDataObjectResponse>) () -> {
            try {
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> updateItem = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
                updateItem.remove(CommonValue.TENANT_ID);
                updateItem.remove(RANGE_KEY);
                Map<String, AttributeValueUpdate> updateAttributeValue = new HashMap<>();
                updateAttributeValue
                    .put(
                        SOURCE,
                        AttributeValueUpdate
                            .builder()
                            .action(AttributeAction.PUT)
                            .value(AttributeValue.builder().m(updateItem).build())
                            .build()
                    );
                Map<String, AttributeValue> updateKey = new HashMap<>();
                updateKey.put(CommonValue.TENANT_ID, AttributeValue.builder().s(tenantId).build());
                updateKey.put(RANGE_KEY, AttributeValue.builder().s(request.id()).build());
                UpdateItemRequest.Builder updateItemRequestBuilder = UpdateItemRequest
                    .builder()
                    .tableName(request.index())
                    .key(updateKey)
                    .attributeUpdates(updateAttributeValue);
                updateItemRequestBuilder
                    .updateExpression("SET #seqNo = #seqNo + :incr")
                    .expressionAttributeNames(Map.of("#seqNo", SEQ_NO_KEY))
                    .expressionAttributeValues(Map.of(":incr", AttributeValue.builder().n("1").build()));
                if (request.ifSeqNo() != null) {
                    // Get current document version and put in attribute map. Ignore primary term on DDB.
                    updateItemRequestBuilder
                        .conditionExpression("#seqNo = :currentSeqNo")
                        .expressionAttributeNames(Map.of("#seqNo", SEQ_NO_KEY))
                        .expressionAttributeValues(
                            Map.of(":currentSeqNo", AttributeValue.builder().n(Long.toString(request.ifSeqNo())).build())
                        );
                }
                UpdateItemRequest updateItemRequest = updateItemRequestBuilder.build();
                UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);
                Long sequenceNumber = null;
                if (updateItemResponse != null
                    && updateItemResponse.attributes() != null
                    && updateItemResponse.attributes().containsKey(SEQ_NO_KEY)) {
                    sequenceNumber = Long.parseLong(updateItemResponse.attributes().get(SEQ_NO_KEY).n());
                }
                String simulatedUpdateResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    source,
                    sequenceNumber,
                    Map.of("result", "updated")
                );
                return UpdateDataObjectResponse.builder().id(request.id()).parser(createParser(simulatedUpdateResponse)).build();
            } catch (ConditionalCheckFailedException ccfe) {
                log.error("Document version conflict updating {} in {}: {}", request.id(), request.index(), ccfe.getMessage(), ccfe);
                // Rethrow
                throw new OpenSearchStatusException(
                    "Document version conflict updating " + request.id() + " in index " + request.index(),
                    RestStatus.CONFLICT
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
        final String tenantId = request.tenantId() != null ? request.tenantId() : CommonValue.DEFAULT_TENANT;
        final DeleteItemRequest deleteItemRequest = DeleteItemRequest
            .builder()
            .tableName(request.index())
            .key(
                Map
                    .ofEntries(
                        Map.entry(CommonValue.TENANT_ID, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                    )
            )
            .build();
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
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
        }), executor);
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
        List<String> indices = Arrays.stream(request.indices()).collect(Collectors.toList());

        SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest(
            indices.toArray(new String[0]),
            request.tenantId(),
            request.searchSourceBuilder()
        );
        return this.remoteClusterIndicesClient.searchDataObjectAsync(searchDataObjectRequest, executor, isMultiTenancyEnabled);
    }

    private XContentParser createParser(String json) throws IOException {
        return jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, json);
    }

    private GetItemRequest buildGetItemRequest(String requestTenantId, String documentId, String index) {
        final String tenantId = requestTenantId != null ? requestTenantId : CommonValue.DEFAULT_TENANT;
        return GetItemRequest
            .builder()
            .tableName(index)
            .key(
                Map
                    .ofEntries(
                        Map.entry(CommonValue.TENANT_ID, AttributeValue.builder().s(tenantId).build()),
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
