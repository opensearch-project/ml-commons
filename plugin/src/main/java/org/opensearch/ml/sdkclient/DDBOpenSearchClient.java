/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

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
            String source = Strings.toString(MediaTypeRegistry.JSON, request.dataObject());
            try {
                JsonNode jsonNode = OBJECT_MAPPER.readTree(source);
                Map<String, AttributeValue> item = convertJsonObjectToItem(jsonNode);
                item.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
                item.put(RANGE_KEY, AttributeValue.builder().s(id).build());
                final PutItemRequest putItemRequest = PutItemRequest.builder().tableName(tableName).item(item).build();

                dynamoDbClient.putItem(putItemRequest);
                return new PutDataObjectResponse.Builder().id(id).created(true).build();
            } catch (IOException e) {
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
                    sourceObject = convertToObjectNode((getItemResponse.item()));
                }

                final String source = OBJECT_MAPPER.writeValueAsString(sourceObject);
                String simulatedGetResponse = "{\"_index\":\""
                    + request.index()
                    + "\",\"_id\":\""
                    + request.id()
                    + "\",\"found\":"
                    + found
                    + ",\"_source\":"
                    + source
                    + "}";

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
                throw new OpenSearchStatusException("Failed to parse data object  " + request.id(), RestStatus.BAD_REQUEST);
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
                Map<String, AttributeValue> updateItem = convertJsonObjectToItem(jsonNode);
                updateItem.put(HASH_KEY, AttributeValue.builder().s(tenantId).build());
                updateItem.put(RANGE_KEY, AttributeValue.builder().s(request.id()).build());
                UpdateItemRequest updateItemRequest = UpdateItemRequest
                    .builder()
                    .tableName(getTableName(request.index()))
                    .key(updateItem)
                    .build();
                dynamoDbClient.updateItem(updateItemRequest);

                return new UpdateDataObjectResponse.Builder().id(request.id()).shardId(request.index()).updated(true).build();
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
            dynamoDbClient.deleteItem(deleteItemRequest);
            return new DeleteDataObjectResponse.Builder().id(request.id()).deleted(true).build();
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
        return this.remoteClusterIndicesClient.searchDataObjectAsync(request, executor);
    }

    private String getTableName(String index) {
        // Table name will be same as index name. As DDB table name does not support dot(.)
        // it will be removed from name.
        return index.replaceAll("\\.", "");
    }

    @VisibleForTesting
    static Map<String, AttributeValue> convertJsonObjectToItem(JsonNode jsonNode) {
        Map<String, AttributeValue> item = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();

            if (field.getValue().isTextual()) {
                item.put(field.getKey(), AttributeValue.builder().s(field.getValue().asText()).build());
            } else if (field.getValue().isNumber()) {
                item.put(field.getKey(), AttributeValue.builder().n(field.getValue().asText()).build());
            } else if (field.getValue().isBoolean()) {
                item.put(field.getKey(), AttributeValue.builder().bool(field.getValue().asBoolean()).build());
            } else if (field.getValue().isNull()) {
                item.put(field.getKey(), AttributeValue.builder().nul(true).build());
            } else if (field.getValue().isObject()) {
                item.put(field.getKey(), AttributeValue.builder().m(convertJsonObjectToItem(field.getValue())).build());
            } else if (field.getValue().isArray()) {
                item.put(field.getKey(), AttributeValue.builder().l(convertJsonArrayToAttributeValueList(field.getValue())).build());
            } else {
                throw new IllegalArgumentException("Unsupported field type: " + field.getValue());
            }
        }

        return item;
    }

    @VisibleForTesting
    static List<AttributeValue> convertJsonArrayToAttributeValueList(JsonNode jsonArray) {
        List<AttributeValue> attributeValues = new ArrayList<>();

        for (JsonNode element : jsonArray) {
            if (element.isTextual()) {
                attributeValues.add(AttributeValue.builder().s(element.asText()).build());
            } else if (element.isNumber()) {
                attributeValues.add(AttributeValue.builder().n(element.asText()).build());
            } else if (element.isBoolean()) {
                attributeValues.add(AttributeValue.builder().bool(element.asBoolean()).build());
            } else if (element.isNull()) {
                attributeValues.add(AttributeValue.builder().nul(true).build());
            } else if (element.isObject()) {
                attributeValues.add(AttributeValue.builder().m(convertJsonObjectToItem(element)).build());
            } else if (element.isArray()) {
                attributeValues.add(AttributeValue.builder().l(convertJsonArrayToAttributeValueList(element)).build());
            } else {
                throw new IllegalArgumentException("Unsupported field type: " + element);
            }

        }

        return attributeValues;
    }

    @VisibleForTesting
    static ObjectNode convertToObjectNode(Map<String, AttributeValue> item) {
        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();

        item.forEach((key, value) -> {
            switch (value.type()) {
                case S:
                    objectNode.put(key, value.s());
                    break;
                case N:
                    objectNode.put(key, value.n());
                    break;
                case BOOL:
                    objectNode.put(key, value.bool());
                    break;
                case L:
                    objectNode.put(key, convertToArrayNode(value.l()));
                    break;
                case M:
                    objectNode.set(key, convertToObjectNode(value.m()));
                    break;
                case NUL:
                    objectNode.putNull(key);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported AttributeValue type: " + value.type());
            }
        });

        return objectNode;

    }

    @VisibleForTesting
    static ArrayNode convertToArrayNode(final List<AttributeValue> attributeValueList) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        attributeValueList.forEach(attribute -> {
            switch (attribute.type()) {
                case S:
                    arrayNode.add(attribute.s());
                    break;
                case N:
                    arrayNode.add(attribute.n());
                    break;
                case BOOL:
                    arrayNode.add(attribute.bool());
                    break;
                case L:
                    arrayNode.add(convertToArrayNode(attribute.l()));
                    break;
                case M:
                    arrayNode.add(convertToObjectNode(attribute.m()));
                    break;
                case NUL:
                    arrayNode.add((JsonNode) null);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported AttributeValue type: " + attribute.type());
            }
        });
        return arrayNode;

    }
}
