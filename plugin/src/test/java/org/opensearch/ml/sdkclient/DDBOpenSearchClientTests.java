/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.sdkclient;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Arrays;
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
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
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
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DDBOpenSearchClientTests extends OpenSearchTestCase {

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
        LocalClusterIndicesClientTests.class.getName(),
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

        sdkClient = new DDBOpenSearchClient(dynamoDbClient, remoteClusterIndicesClient);
        testDataObject = new TestDataObject("foo");
    }

    @Test
    public void testPutDataObject_HappyCase() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
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

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, putItemRequest.tableName());
        Assert.assertEquals(TEST_ID, putItemRequest.item().get("id").s());
        Assert.assertEquals(TENANT_ID, putItemRequest.item().get("tenant_id").s());
        Assert.assertEquals("foo", putItemRequest.item().get("data").s());
    }

    @Test
    public void testPutDataObject_WithComplexData() throws IOException {
        ComplexDataObject complexDataObject = ComplexDataObject
            .builder()
            .testString("testString")
            .testNumber(123)
            .testBool(true)
            .testList(Arrays.asList("123", "hello", null))
            .testObject(testDataObject)
            .build();
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .tenantId(TENANT_ID)
            .dataObject(complexDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());
        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("testString", putItemRequest.item().get("testString").s());
        Assert.assertEquals("123", putItemRequest.item().get("testNumber").n());
        Assert.assertEquals(true, putItemRequest.item().get("testBool").bool());
        Assert.assertEquals("123", putItemRequest.item().get("testList").l().get(0).s());
        Assert.assertEquals("hello", putItemRequest.item().get("testList").l().get(1).s());
        Assert.assertEquals(null, putItemRequest.item().get("testList").l().get(2).s());
        Assert.assertEquals("foo", putItemRequest.item().get("testObject").m().get("data").s());
    }

    @Test
    public void testPutDataObject_NullTenantId_SetsDefaultTenantId() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        sdkClient.putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("DEFAULT_TENANT", putItemRequest.item().get("tenant_id").s());
    }

    @Test
    public void testPutDataObject_NullId_SetsDefaultTenantId() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();
        Mockito.when(dynamoDbClient.putItem(Mockito.any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).putItem(putItemRequestArgumentCaptor.capture());

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertNotNull(putItemRequest.item().get("id").s());
        Assert.assertNotNull(response.id());
    }

    @Test
    public void testPutDataObject_DDBException_ThrowsException() {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
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
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(Map.ofEntries(Map.entry("data", AttributeValue.builder().s("foo").build())))
            .build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        Mockito.verify(dynamoDbClient).getItem(getItemRequestArgumentCaptor.capture());
        GetItemRequest getItemRequest = getItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, getItemRequest.tableName());
        Assert.assertEquals(TENANT_ID, getItemRequest.key().get("tenant_id").s());
        Assert.assertEquals(TEST_ID, getItemRequest.key().get("id").s());
        Assert.assertEquals(TEST_ID, response.id());
        Assert.assertEquals("foo", response.source().get("data"));
        XContentParser parser = response.parser();
        XContentParser dataParser = XContentHelper
            .createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                GetResponse.fromXContent(parser).getSourceAsBytesRef(),
                XContentType.JSON
            );
        ensureExpectedToken(XContentParser.Token.START_OBJECT, dataParser.nextToken(), dataParser);
        Assert.assertEquals("foo", TestDataObject.parse(dataParser).data());
    }

    @Test
    public void testGetDataObject_ComplexDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(
                Map
                    .ofEntries(
                        Map.entry("testString", AttributeValue.builder().s("testString").build()),
                        Map.entry("testNumber", AttributeValue.builder().n("123").build()),
                        Map.entry("testBool", AttributeValue.builder().bool(true).build()),
                        Map
                            .entry(
                                "testList",
                                AttributeValue.builder().l(Arrays.asList(AttributeValue.builder().s("testString").build())).build()
                            ),
                        Map
                            .entry(
                                "testObject",
                                AttributeValue.builder().m(ImmutableMap.of("data", AttributeValue.builder().s("foo").build())).build()
                            )
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
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
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
    public void testGetDataObject_UseDefaultTenantIdIfNull() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();
        GetItemResponse getItemResponse = GetItemResponse.builder().build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenReturn(getItemResponse);
        sdkClient.getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        Mockito.verify(dynamoDbClient).getItem(getItemRequestArgumentCaptor.capture());
        GetItemRequest getItemRequest = getItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("DEFAULT_TENANT", getItemRequest.key().get("tenant_id").s());
    }

    @Test
    public void testGetDataObject_DDBException_ThrowsOSException() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).tenantId(TENANT_ID).build();
        Mockito.when(dynamoDbClient.getItem(Mockito.any(GetItemRequest.class))).thenThrow(new RuntimeException("Test exception"));
        CompletableFuture<GetDataObjectResponse> future = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();
        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(RuntimeException.class, ce.getCause().getClass());
    }

    @Test
    public void testDeleteDataObject_HappyCase() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .build();
        Mockito.when(dynamoDbClient.deleteItem(deleteItemRequestArgumentCaptor.capture())).thenReturn(DeleteItemResponse.builder().build());
        DeleteDataObjectResponse deleteResponse = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        DeleteItemRequest deleteItemRequest = deleteItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, deleteItemRequest.tableName());
        Assert.assertEquals(TENANT_ID, deleteItemRequest.key().get("tenant_id").s());
        Assert.assertEquals(TEST_ID, deleteItemRequest.key().get("id").s());
        Assert.assertEquals(TEST_ID, deleteResponse.id());

        DeleteResponse deleteActionResponse = DeleteResponse.fromXContent(deleteResponse.parser());
        assertEquals(TEST_ID, deleteActionResponse.getId());
        assertEquals(DocWriteResponse.Result.DELETED, deleteActionResponse.getResult());
        assertEquals(0, deleteActionResponse.getShardInfo().getFailed());
        assertEquals(0, deleteActionResponse.getShardInfo().getSuccessful());
        assertEquals(0, deleteActionResponse.getShardInfo().getTotal());
    }

    @Test
    public void testDeleteDataObject_NullTenantId_UsesDefaultTenantId() {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().id(TEST_ID).index(TEST_INDEX).build();
        Mockito.when(dynamoDbClient.deleteItem(deleteItemRequestArgumentCaptor.capture())).thenReturn(DeleteItemResponse.builder().build());
        sdkClient.deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        DeleteItemRequest deleteItemRequest = deleteItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("DEFAULT_TENANT", deleteItemRequest.key().get("tenant_id").s());
    }

    @Test
    public void updateDataObjectAsync_HappyCase() {
        UpdateDataObjectRequest updateRequest = new UpdateDataObjectRequest.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture())).thenReturn(UpdateItemResponse.builder().build());
        UpdateDataObjectResponse updateResponse = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        assertEquals(TEST_ID, updateResponse.id());
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TEST_ID, updateRequest.id());
        assertEquals(TEST_INDEX, updateItemRequest.tableName());
        assertEquals(TEST_ID, updateItemRequest.key().get("id").s());
        assertEquals(TENANT_ID, updateItemRequest.key().get("tenant_id").s());
        assertEquals("foo", updateItemRequest.key().get("data").s());

    }

    @Test
    public void updateDataObjectAsync_NullTenantId_UsesDefaultTenantId() {
        UpdateDataObjectRequest updateRequest = new UpdateDataObjectRequest.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .tenantId(TENANT_ID)
            .dataObject(testDataObject)
            .build();
        Mockito.when(dynamoDbClient.updateItem(updateItemRequestArgumentCaptor.capture())).thenReturn(UpdateItemResponse.builder().build());
        sdkClient.updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();
        UpdateItemRequest updateItemRequest = updateItemRequestArgumentCaptor.getValue();
        assertEquals(TENANT_ID, updateItemRequest.key().get("tenant_id").s());
    }

    @Test
    public void searchDataObjectAsync_HappyCase() {
        SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource();
        SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest.Builder()
            .indices(TEST_INDEX, TEST_INDEX_2)
            .tenantId(TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        CompletionStage<SearchDataObjectResponse> searchDataObjectResponse = Mockito.mock(CompletionStage.class);
        Mockito.when(remoteClusterIndicesClient.searchDataObjectAsync(Mockito.any(), Mockito.any())).thenReturn(searchDataObjectResponse);
        CompletionStage<SearchDataObjectResponse> searchResponse = sdkClient.searchDataObjectAsync(searchDataObjectRequest);

        assertEquals(searchDataObjectResponse, searchResponse);
        Mockito.verify(remoteClusterIndicesClient).searchDataObjectAsync(searchDataObjectRequestArgumentCaptor.capture(), Mockito.any());
        Assert.assertEquals(TENANT_ID, searchDataObjectRequestArgumentCaptor.getValue().tenantId());
        Assert.assertEquals(TEST_INDEX, searchDataObjectRequestArgumentCaptor.getValue().indices()[0]);
        Assert.assertEquals(TEST_INDEX_2, searchDataObjectRequestArgumentCaptor.getValue().indices()[1]);
        Assert.assertEquals(searchSourceBuilder, searchDataObjectRequestArgumentCaptor.getValue().searchSourceBuilder());
    }

    @Test
    public void searchDataObjectAsync_SystemIndex() {
        SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource();
        SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest.Builder()
            .indices(TEST_SYSTEM_INDEX)
            .tenantId(TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        CompletionStage<SearchDataObjectResponse> searchDataObjectResponse = Mockito.mock(CompletionStage.class);
        Mockito.when(remoteClusterIndicesClient.searchDataObjectAsync(Mockito.any(), Mockito.any())).thenReturn(searchDataObjectResponse);
        CompletionStage<SearchDataObjectResponse> searchResponse = sdkClient.searchDataObjectAsync(searchDataObjectRequest);

        assertEquals(searchDataObjectResponse, searchResponse);
        Mockito.verify(remoteClusterIndicesClient).searchDataObjectAsync(searchDataObjectRequestArgumentCaptor.capture(), Mockito.any());
        Assert.assertEquals("test_index", searchDataObjectRequestArgumentCaptor.getValue().indices()[0]);
    }

}
