/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.sdkclient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.sdk.BulkDataObjectRequest;
import org.opensearch.sdk.BulkDataObjectResponse;
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
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DDBOpenSearchClientTests extends OpenSearchTestCase {

    private static final String RANGE_KEY = "_id";
    private static final String HASH_KEY = "_tenant_id";
    private static final String SEQ_NUM = "_seq_no";
    private static final String SOURCE = "_source";

    private static final String TEST_ID = "123";
    private static final String TENANT_ID = "TEST_TENANT_ID";
    private static final String TEST_INDEX = "test_index";
    private static final String TEST_INDEX_2 = "test_index_2";
    private static final String TEST_SYSTEM_INDEX = ".test_index";
    private SdkClient sdkClient;

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private RemoteClusterIndicesClient remoteClusterIndicesClient;
    @Captor
    private ArgumentCaptor<PutItemRequest> putItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<GetItemRequest> getItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<DeleteItemRequest> deleteItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<UpdateItemRequest> updateItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<SearchDataObjectRequest> searchDataObjectRequestArgumentCaptor;
    private TestDataObject testDataObject;

    private static TestThreadPool testThreadPool = new TestThreadPool(
        DDBOpenSearchClientTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.wrapSdkClientDelegate(new DDBOpenSearchClient(dynamoDbClient, remoteClusterIndicesClient), true);
        testDataObject = new TestDataObject("foo");
    }

    @Test
    public void testPutDataObject_HappyCase() throws IOException {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .overwriteIfExists(false)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());
        Assert.assertEquals(TEST_ID, response.id());

        IndexResponse indexActionResponse = IndexResponse.fromXContent(response.parser());
        assertEquals(TEST_ID, indexActionResponse.getId());
        assertEquals(DocWriteResponse.Result.CREATED, indexActionResponse.getResult());
        assertEquals(0, indexActionResponse.getSeqNo());

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, putItemRequest.tableName());
        Assert.assertEquals(TEST_ID, putItemRequest.item().get(RANGE_KEY).s());
        Assert.assertEquals(TENANT_ID, putItemRequest.item().get(HASH_KEY).s());
        Assert.assertEquals("0", putItemRequest.item().get(SEQ_NUM).n());
        Assert.assertEquals("foo", putItemRequest.item().get(SOURCE).m().get("data").s());
    }

    @Test
    public void testPutDataObject_ExistingDocument_UpdatesSequenceNumber() throws IOException {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito
            .when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(Map.of(SEQ_NUM, AttributeValue.builder().n("5").build())).build());
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());
        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        IndexResponse indexActionResponse = IndexResponse.fromXContent(response.parser());
        assertEquals(6, indexActionResponse.getSeqNo());
        Assert.assertEquals("6", putItemRequest.item().get(SEQ_NUM).n());
    }

    @Test
    public void testPutDataObject_ExistingDocument_DisableOverwrite() {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .overwriteIfExists(false)
            .dataObject(testDataObject)
            .build();
        Mockito
            .when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(Map.of(SEQ_NUM, AttributeValue.builder().n("5").build())).build());
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        CompletableFuture<PutDataObjectResponse> response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();
        CompletionException ce = assertThrows(CompletionException.class, () -> response.join());
        assertEquals(OpenSearchStatusException.class, ce.getCause().getClass());
    }

    @Test
    public void testPutDataObject_WithComplexData() {
        ComplexDataObject complexDataObject = ComplexDataObject
            .builder()
            .testString("testString")
            .testNumber(123)
            .testBool(true)
            .testList(Arrays.asList("123", "hello", null))
            .testObject(testDataObject)
            .build();
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(complexDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        sdkClient.putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());
        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("testString", putItemRequest.item().get(SOURCE).m().get("testString").s());
        Assert.assertEquals("123", putItemRequest.item().get(SOURCE).m().get("testNumber").n());
        Assert.assertEquals(true, putItemRequest.item().get(SOURCE).m().get("testBool").bool());
        Assert.assertEquals("123", putItemRequest.item().get(SOURCE).m().get("testList").l().get(0).s());
        Assert.assertEquals("hello", putItemRequest.item().get(SOURCE).m().get("testList").l().get(1).s());
        Assert.assertEquals(null, putItemRequest.item().get(SOURCE).m().get("testList").l().get(2).s());
        Assert.assertEquals("foo", putItemRequest.item().get(SOURCE).m().get("testObject").m().get("data").s());
        Assert.assertEquals(TENANT_ID, putItemRequest.item().get(SOURCE).m().get(CommonValue.TENANT_ID).s());
    }

    @Test
    public void testPutDataObject_NullId_SetsDefaultTenantId() {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertNotNull(putItemRequest.item().get(RANGE_KEY).s());
        Assert.assertNotNull(response.id());
    }

    @Test
    public void testPutDataObject_DDBException_ThrowsException() {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenThrow(new RuntimeException("Test exception"));
        CompletableFuture<PutDataObjectResponse> future = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(RuntimeException.class, ce.getCause().getClass());
    }

    @Test
    public void testGetDataObject_HappyCase() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map
                            .entry(
                                SOURCE,
                                AttributeValue
                                    .builder()
                                    .m(Map.ofEntries(Map.entry("data", AttributeValue.builder().s("foo").build())))
                                    .build()
                            ),
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("1").build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).getItem(getItemRequestArgumentCaptor.capture());
        GetItemRequest getItemRequest = getItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, getItemRequest.tableName());
        Assert.assertEquals(TENANT_ID, getItemRequest.key().get(HASH_KEY).s());
        Assert.assertEquals(TEST_ID, getItemRequest.key().get(RANGE_KEY).s());
        Assert.assertEquals(TEST_ID, response.id());
        Assert.assertEquals("foo", response.source().get("data"));
        XContentParser parser = response.parser();
        GetResponse getResponse = GetResponse.fromXContent(parser);
        Assert.assertEquals(1, getResponse.getSeqNo());
        XContentParser dataParser = XContentHelper
            .createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                getResponse.getSourceAsBytesRef(),
                XContentType.JSON
            );
        ensureExpectedToken(XContentParser.Token.START_OBJECT, dataParser.nextToken(), dataParser);
        Assert.assertEquals("foo", TestDataObject.parse(dataParser).data());
    }

    @Test
    public void testGetDataObject_ComplexDataObject() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SOURCE, AttributeValue.builder().m(getComplexDataSource()).build()),
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("1").build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).getItem(getItemRequestArgumentCaptor.capture());

        GetResponse getResponse = GetResponse.fromXContent(response.parser());
        XContentParser parser = JsonXContent.jsonXContent
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, getResponse.getSourceAsString());
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        ComplexDataObject complexDataObject = ComplexDataObject.parse(parser);
        assertEquals("testString", complexDataObject.getTestString());
        assertEquals(123, complexDataObject.getTestNumber());
        assertEquals("testString", complexDataObject.getTestList().get(0));
        assertEquals("foo", complexDataObject.getTestObject().data());
        assertEquals(true, complexDataObject.isTestBool());
    }

    @Test
    public void testGetDataObject_NoExistingDoc() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        GetItemResponse getItemResponse = GetItemResponse.builder().build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Assert.assertEquals(TEST_ID, response.id());
        assertTrue(response.source().isEmpty());
        assertFalse(GetResponse.fromXContent(response.parser()).isExists());
    }

    @Test
    public void testGetDataObject_UseDefaultTenantIdIfNull() {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).build();
        GetItemResponse getItemResponse = GetItemResponse.builder().build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        sdkClient.getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        Mockito.verify(dynamoDbClient).getItem(getItemRequestArgumentCaptor.capture());
        GetItemRequest getItemRequest = getItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("DEFAULT_TENANT", getItemRequest.key().get(HASH_KEY).s());
    }

    @Test
    public void testGetDataObject_DDBException_ThrowsOSException() {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenThrow(new RuntimeException("Test exception"));
        CompletableFuture<GetDataObjectResponse> future = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();
        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(RuntimeException.class, ce.getCause().getClass());
    }

    @Test
    public void testDeleteDataObject_HappyCase() throws IOException {
        DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest.builder().id(TEST_ID).index(TEST_INDEX).tenantId(TENANT_ID).build();
        Mockito
            .when(dynamoDbClient.deleteItem(deleteItemRequestArgumentCaptor.capture()))
            .thenReturn(DeleteItemResponse.builder().attributes(Map.of(SEQ_NUM, AttributeValue.builder().n("5").build())).build());
        DeleteDataObjectResponse deleteResponse = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        DeleteItemRequest deleteItemRequest = deleteItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, deleteItemRequest.tableName());
        Assert.assertEquals(TENANT_ID, deleteItemRequest.key().get(HASH_KEY).s());
        Assert.assertEquals(TEST_ID, deleteItemRequest.key().get(RANGE_KEY).s());
        Assert.assertEquals(TEST_ID, deleteResponse.id());

        DeleteResponse deleteActionResponse = DeleteResponse.fromXContent(deleteResponse.parser());
        assertEquals(TEST_ID, deleteActionResponse.getId());
        assertEquals(6, deleteActionResponse.getSeqNo());
        assertEquals(DocWriteResponse.Result.DELETED, deleteActionResponse.getResult());
        assertEquals(0, deleteActionResponse.getShardInfo().getFailed());
        assertEquals(0, deleteActionResponse.getShardInfo().getSuccessful());
        assertEquals(0, deleteActionResponse.getShardInfo().getTotal());

    }

    @Test
    public void testUpdateDataObjectAsync_HappyCase() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .retryOnConflict(1)
            .dataObject(testDataObject)
            .build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map
                            .entry(
                                SOURCE,
                                AttributeValue.builder().m(Map.of("old_key", AttributeValue.builder().s("old_value").build())).build()
                            )
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        Mockito.when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture())).thenReturn(UpdateItemResponse.builder().build());
        UpdateDataObjectResponse updateResponse = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        assertEquals(TEST_ID, updateResponse.id());
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TEST_ID, updateRequest.id());
        assertEquals(TEST_INDEX, updateItemRequest.tableName());
        assertEquals(TEST_ID, updateItemRequest.key().get(RANGE_KEY).s());
        assertEquals(TENANT_ID, updateItemRequest.key().get(HASH_KEY).s());
        assertEquals("foo", updateItemRequest.expressionAttributeValues().get(":source").m().get("data").s());
        assertEquals("old_value", updateItemRequest.expressionAttributeValues().get(":source").m().get("old_key").s());
    }

    @Test
    public void testUpdateDataObjectAsync_HappyCaseWithMap() throws Exception {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .dataObject(Map.of("foo", "bar"))
            .ifSeqNo(10)
            .ifPrimaryTerm(10)
            .build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map
                            .entry(
                                SOURCE,
                                AttributeValue.builder().m(Map.of("old_key", AttributeValue.builder().s("old_value").build())).build()
                            )
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        Mockito
            .when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture()))
            .thenReturn(UpdateItemResponse.builder().attributes(Map.of(SEQ_NUM, AttributeValue.builder().n("5").build())).build());
        UpdateDataObjectResponse updateResponse = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        assertEquals(TEST_ID, updateResponse.id());
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TEST_INDEX, updateItemRequest.tableName());
        assertEquals(TEST_ID, updateItemRequest.key().get(RANGE_KEY).s());
        assertEquals(TENANT_ID, updateItemRequest.key().get(HASH_KEY).s());
        assertTrue(updateItemRequest.expressionAttributeNames().containsKey("#seqNo"));
        assertTrue(updateItemRequest.expressionAttributeNames().containsKey("#source"));
        assertTrue(updateItemRequest.expressionAttributeValues().containsKey(":incr"));
        assertTrue(updateItemRequest.expressionAttributeValues().containsKey(":source"));
        assertEquals("bar", updateItemRequest.expressionAttributeValues().get(":source").m().get("foo").s());
        assertEquals("old_value", updateItemRequest.expressionAttributeValues().get(":source").m().get("old_key").s());
        assertTrue(updateItemRequest.expressionAttributeValues().containsKey(":currentSeqNo"));
        assertNotNull(updateItemRequest.conditionExpression());
        UpdateResponse response = UpdateResponse.fromXContent(updateResponse.parser());
        Assert.assertEquals(5, response.getSeqNo());

    }

    @Test
    public void testUpdateDataObjectAsync_NullTenantId_UsesDefaultTenantId() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map.entry(SOURCE, AttributeValue.builder().m(Collections.emptyMap()).build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        Mockito.when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture())).thenReturn(UpdateItemResponse.builder().build());
        sdkClient.updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TENANT_ID, updateItemRequest.key().get(HASH_KEY).s());
    }

    @Test
    public void testUpdateDataObject_DDBException_ThrowsException() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenThrow(new RuntimeException("Test exception"));
        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(RuntimeException.class, ce.getCause().getClass());
    }

    @Test
    public void testUpdateDataObject_VersionCheck() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .ifSeqNo(5)
            .ifPrimaryTerm(2)
            .build();

        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map.entry(SOURCE, AttributeValue.builder().m(Collections.emptyMap()).build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        ConditionalCheckFailedException conflictException = ConditionalCheckFailedException.builder().build();
        when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture())).thenThrow(conflictException);

        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(OpenSearchStatusException.class, cause.getClass());
        assertEquals(RestStatus.CONFLICT, ((OpenSearchStatusException) cause).status());
    }

    @Test
    public void updateDataObjectAsync_VersionCheckRetrySuccess() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .retryOnConflict(1)
            .dataObject(testDataObject)
            .build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map.entry(SOURCE, AttributeValue.builder().m(Collections.emptyMap()).build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        ConditionalCheckFailedException conflictException = ConditionalCheckFailedException.builder().build();
        // throw conflict exception on first time, return on second time, throw on third time (never get here)
        Mockito
            .when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture()))
            .thenThrow(conflictException)
            .thenReturn(UpdateItemResponse.builder().build())
            .thenThrow(conflictException);
        UpdateDataObjectResponse updateResponse = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        assertEquals(TEST_ID, updateResponse.id());
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TEST_ID, updateRequest.id());
        assertEquals(TEST_INDEX, updateItemRequest.tableName());
        assertEquals(TEST_ID, updateItemRequest.key().get(RANGE_KEY).s());
        assertEquals(TENANT_ID, updateItemRequest.key().get(HASH_KEY).s());
        assertEquals("foo", updateItemRequest.expressionAttributeValues().get(":source").m().get("data").s());
    }

    @Test
    public void updateDataObjectAsync_VersionCheckRetryFailure() {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .retryOnConflict(1)
            .dataObject(testDataObject)
            .build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build()),
                        Map.entry(SOURCE, AttributeValue.builder().m(Collections.emptyMap()).build())
                    )
            )
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        ConditionalCheckFailedException conflictException = ConditionalCheckFailedException.builder().build();
        // throw conflict exception on first two times, return on third time (that never executes)
        Mockito
            .when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture()))
            .thenThrow(conflictException)
            .thenThrow(conflictException)
            .thenReturn(UpdateItemResponse.builder().build());

        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(OpenSearchStatusException.class, cause.getClass());
        assertEquals(RestStatus.CONFLICT, ((OpenSearchStatusException) cause).status());
    }

    @Test
    public void testBulkDataObject_HappyCase() {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .id(TEST_ID + "1")
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID + "2")
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest.builder().id(TEST_ID + "3").tenantId(TENANT_ID).build();
        BulkDataObjectRequest bulkRequest = BulkDataObjectRequest
            .builder()
            .globalIndex(TEST_INDEX)
            .build()
            .add(putRequest)
            .add(updateRequest)
            .add(deleteRequest);

        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SOURCE, AttributeValue.builder().m(Map.of("data", AttributeValue.builder().s("foo").build())).build()),
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build())
                    )
            )
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

        BulkDataObjectResponse response = sdkClient
            .bulkDataObjectAsync(bulkRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(3, response.getResponses().length);
        assertTrue(response.getResponses()[0] instanceof PutDataObjectResponse);
        assertTrue(response.getResponses()[1] instanceof UpdateDataObjectResponse);
        assertTrue(response.getResponses()[2] instanceof DeleteDataObjectResponse);

        assertEquals(TEST_ID + "1", response.getResponses()[0].id());
        assertEquals(TEST_ID + "2", response.getResponses()[1].id());
        assertEquals(TEST_ID + "3", response.getResponses()[2].id());

        assertTrue(response.getTookInMillis() >= 0);
    }

    @Test
    public void testBulkDataObject_WithFailures() {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .id(TEST_ID + "1")
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .id(TEST_ID + "2")
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest.builder().id(TEST_ID + "3").tenantId(TENANT_ID).build();
        BulkDataObjectRequest bulkRequest = BulkDataObjectRequest
            .builder()
            .globalIndex(TEST_INDEX)
            .build()
            .add(putRequest)
            .add(updateRequest)
            .add(deleteRequest);

        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry(SOURCE, AttributeValue.builder().m(Map.of("data", AttributeValue.builder().s("foo").build())).build()),
                        Map.entry(SEQ_NUM, AttributeValue.builder().n("0").build())
                    )
            )
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);
        Exception cause = new OpenSearchStatusException("Update failed with conflict", RestStatus.CONFLICT);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(cause);
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

        BulkDataObjectResponse response = sdkClient
            .bulkDataObjectAsync(bulkRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(3, response.getResponses().length);
        assertFalse(response.getResponses()[0].isFailed());
        assertNull(response.getResponses()[0].cause());
        assertTrue(response.getResponses()[0] instanceof PutDataObjectResponse);
        assertTrue(response.getResponses()[1].isFailed());
        assertTrue(response.getResponses()[1].cause() instanceof OpenSearchStatusException);
        assertEquals("Update failed with conflict", response.getResponses()[1].cause().getMessage());
        assertEquals(RestStatus.CONFLICT, response.getResponses()[1].status());
        assertTrue(response.getResponses()[1] instanceof UpdateDataObjectResponse);
        assertFalse(response.getResponses()[2].isFailed());
        assertNull(response.getResponses()[0].cause());
        assertTrue(response.getResponses()[2] instanceof DeleteDataObjectResponse);
    }

    @Test
    public void searchDataObjectAsync_HappyCase() {
        SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource();
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_INDEX, TEST_INDEX_2)
            .tenantId(TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        @SuppressWarnings("unchecked")
        CompletionStage<SearchDataObjectResponse> searchDataObjectResponse = Mockito.mock(CompletionStage.class);
        Mockito
            .when(remoteClusterIndicesClient.searchDataObjectAsync(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
            .thenReturn(searchDataObjectResponse);
        CompletionStage<SearchDataObjectResponse> searchResponse = sdkClient.searchDataObjectAsync(searchDataObjectRequest);

        assertEquals(searchDataObjectResponse, searchResponse);
        Mockito
            .verify(remoteClusterIndicesClient)
            .searchDataObjectAsync(searchDataObjectRequestArgumentCaptor.capture(), Mockito.any(), Mockito.anyBoolean());
        Assert.assertEquals(TENANT_ID, searchDataObjectRequestArgumentCaptor.getValue().tenantId());
        Assert.assertEquals(TEST_INDEX, searchDataObjectRequestArgumentCaptor.getValue().indices()[0]);
        Assert.assertEquals(TEST_INDEX_2, searchDataObjectRequestArgumentCaptor.getValue().indices()[1]);
        Assert.assertEquals(searchSourceBuilder, searchDataObjectRequestArgumentCaptor.getValue().searchSourceBuilder());
    }

    @Test
    public void searchDataObjectAsync_SystemIndex() {
        SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource();
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_SYSTEM_INDEX)
            .tenantId(TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        @SuppressWarnings("unchecked")
        CompletionStage<SearchDataObjectResponse> searchDataObjectResponse = Mockito.mock(CompletionStage.class);
        Mockito
            .when(remoteClusterIndicesClient.searchDataObjectAsync(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
            .thenReturn(searchDataObjectResponse);
        CompletionStage<SearchDataObjectResponse> searchResponse = sdkClient.searchDataObjectAsync(searchDataObjectRequest);

        assertEquals(searchDataObjectResponse, searchResponse);
        Mockito
            .verify(remoteClusterIndicesClient)
            .searchDataObjectAsync(searchDataObjectRequestArgumentCaptor.capture(), Mockito.any(), Mockito.anyBoolean());
        Assert.assertEquals("test_index", searchDataObjectRequestArgumentCaptor.getValue().indices()[0]);
    }

    private Map<String, AttributeValue> getComplexDataSource() {
        return Map
            .ofEntries(
                Map.entry("testString", AttributeValue.builder().s("testString").build()),
                Map.entry("testNumber", AttributeValue.builder().n("123").build()),
                Map.entry("testBool", AttributeValue.builder().bool(true).build()),
                Map.entry("testList", AttributeValue.builder().l(Arrays.asList(AttributeValue.builder().s("testString").build())).build()),
                Map.entry("testObject", AttributeValue.builder().m(Map.of("data", AttributeValue.builder().s("foo").build())).build())
            );
    }
}
