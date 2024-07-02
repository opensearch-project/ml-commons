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
import org.opensearch.ml.sdkclient.util.JsonTransformer;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * DDB implementation of {@link SdkClient}. DDB table name will be mapped to index name.
 *
 */
@Log4j2
public class DDBOpenSearchClient implements SdkClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static final String HASH_KEY = "tenant_id";
    private static final String RANGE_KEY = "id";

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
     */
    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
        final String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final String tableName = getTableName(request.index());
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try {
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> item = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
                item.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
                item.put(RANGE_KEY, AttributeValue.builder().s(id).build());
                final PutItemRequest putItemRequest = PutItemRequest.builder().tableName(tableName).item(item).build();

                dynamoDbClient.putItem(putItemRequest);
                String simulatedIndexResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    source,
                    Map.of("result", "created")
                );
                return new PutDataObjectResponse.Builder().id(id).parser(createParser(simulatedIndexResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse data object  " + request.id(), RestStatus.BAD_REQUEST);
            }
        }), executor);
    }

    /**
     * Fetches data document from DDB. Default tenant ID will be used if tenant ID is not specified.
     *
     */
    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final GetItemRequest getItemRequest = GetItemRequest
            .builder()
            .tableName(getTableName(request.index()))
            .key(
                Map
                    .ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                    )
            )
            .build();
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                final GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
                ObjectNode sourceObject;
                boolean found;
                if (getItemResponse == null || getItemResponse.item() == null || getItemResponse.item().isEmpty()) {
                    found = false;
                    sourceObject = null;
                } else {
                    found = true;
                    sourceObject = JsonTransformer.convertDDBAttributeValueMapToObjectNode(getItemResponse.item());
                }
                final String source = OBJECT_MAPPER.writeValueAsString(sourceObject);
                String simulatedGetResponse = simulateOpenSearchResponse(request.index(), request.id(), source, Map.of("found", found));
                XContentParser parser = JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, simulatedGetResponse);
                // This would consume parser content so we need to create a new parser for the map
                Map<String, Object> sourceAsMap = GetResponse
                    .fromXContent(
                        JsonXContent.jsonXContent
                            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, simulatedGetResponse)
                    )
                    .getSourceAsMap();
                return new GetDataObjectResponse.Builder().id(request.id()).parser(parser).source(sourceAsMap).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException("Failed to parse response", RestStatus.INTERNAL_SERVER_ERROR);
            }
        }), executor);
    }

    /**
     * Makes use of DDB update request to update data object.
     *
     */
    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<UpdateDataObjectResponse>) () -> {
            try {
                String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> updateItem = JsonTransformer.convertJsonObjectToDDBAttributeMap(jsonNode);
                updateItem.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
                updateItem.put(RANGE_KEY, AttributeValue.builder().s(request.id()).build());
                UpdateItemRequest updateItemRequest = UpdateItemRequest
                    .builder()
                    .tableName(getTableName(request.index()))
                    .key(updateItem)
                    .build();
                dynamoDbClient.updateItem(updateItemRequest);

                String simulatedUpdateResponse = simulateOpenSearchResponse(request.index(), request.id(), source, Map.of("found", true));
                return new UpdateDataObjectResponse.Builder().id(request.id()).parser(createParser(simulatedUpdateResponse)).build();
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
     */
    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final DeleteItemRequest deleteItemRequest = DeleteItemRequest
            .builder()
            .tableName(getTableName(request.index()))
            .key(
                Map
                    .ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                    )
            )
            .build();
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
            try {
                dynamoDbClient.deleteItem(deleteItemRequest);
                String simulatedDeleteResponse = simulateOpenSearchResponse(
                    request.index(),
                    request.id(),
                    null,
                    Map.of("result", "deleted")
                );
                return new DeleteDataObjectResponse.Builder().id(request.id()).parser(createParser(simulatedDeleteResponse)).build();
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
     * @param request
     * @param executor
     * @return Search data object response
     */
    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor) {
        List<String> indices = Arrays.stream(request.indices()).map(this::getTableName).collect(Collectors.toList());

        SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest(
            indices.toArray(new String[0]),
            request.tenantId(),
            request.searchSourceBuilder()
        );
        return this.remoteClusterIndicesClient.searchDataObjectAsync(searchDataObjectRequest, executor);
    }

    private String getTableName(String index) {
        // Table name will be same as index name. As DDB table name does not support dot(.)
        // it will be removed from name.
        return index.replaceAll("\\.", "");
    }

    private XContentParser createParser(String json) throws IOException {
        return jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, json);
    }

    private String simulateOpenSearchResponse(String index, String id, String source, Map<String, Object> additionalFields) {
        StringBuilder sb = new StringBuilder("{");
        // Fields with a DDB counterpart
        sb.append("\"_index\":\"").append(index).append("\",");
        sb.append("\"_id\":\"").append(id).append("\",");
        // Fields we must simulate using default values
        sb.append("\"_primary_term\":").append(UNASSIGNED_PRIMARY_TERM).append(",");
        sb.append("\"_seq_no\":").append(UNASSIGNED_SEQ_NO).append(",");
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
