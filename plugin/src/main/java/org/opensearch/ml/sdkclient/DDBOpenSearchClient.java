package org.opensearch.ml.sdkclient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.json.JsonXContent;
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
import org.opensearch.sdk.SdkClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@AllArgsConstructor
@Log4j2
public class DDBOpenSearchClient implements SdkClient {

    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static final String HASH_KEY = "tenant_id";
    private static final String RANGE_KEY = "id";
    private static final String SOURCE = "source";

    private DynamoDbClient dynamoDbClient;
    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
        final String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final String tableName = getTableName(request.index());
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
                XContentBuilder builder = request.dataObject().toXContent(sourceBuilder, ToXContent.EMPTY_PARAMS);
                String source = builder.toString();

                final Map<String, AttributeValue> item = Map.ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(id).build()),
                        Map.entry(SOURCE, AttributeValue.builder().s(source).build())
                );
                final PutItemRequest putItemRequest = PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .build();

                    dynamoDbClient.putItem(putItemRequest);
                    return new PutDataObjectResponse.Builder().id(id).created(true).build();
            } catch (Exception e){
                log.error("Exception while inserting data into DDB: " + e.getMessage(), e);
                throw new OpenSearchException(e);
        }
        }), executor);
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(getTableName(request.index()))
                .key(Map.ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                        ))
                .build();
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                final GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
                if (getItemResponse == null || getItemResponse.item() == null || getItemResponse.item().isEmpty()) {
                    return new GetDataObjectResponse.Builder().id(request.id()).parser(Optional.empty()).build();
                }

                String source = getItemResponse.item().get(SOURCE).s();
                XContentParser parser = JsonXContent.jsonXContent
                        .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, source);
                return new GetDataObjectResponse.Builder().id(request.id()).parser(Optional.of(parser)).build();
            } catch (Exception e) {
                log.error("Exception while fetching data from DDB: " + e.getMessage(), e);
                throw new OpenSearchException(e);
            }
        }), executor);
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
        final String tenantId = request.tenantId() != null ? request.tenantId() : DEFAULT_TENANT;
        final DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(getTableName(request.index()))
                .key(Map.ofEntries(
                        Map.entry(HASH_KEY, AttributeValue.builder().s(tenantId).build()),
                        Map.entry(RANGE_KEY, AttributeValue.builder().s(request.id()).build())
                )).build();
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
            dynamoDbClient.deleteItem(deleteItemRequest);
            return new DeleteDataObjectResponse.Builder().id(request.id()).deleted(true).build();
        }), executor);
    }

    private String getTableName(String index) {
        // Table name will be same as index name. As DDB table name does not support dot(.)
        // it will be removed form name.
        return index.replaceAll("\\.", "");
    }
}
