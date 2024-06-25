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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
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
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public class DDBOpenSearchClientTests extends OpenSearchTestCase {

    private static final String TEST_ID = "123";
    private static final String TENANT_ID = "TEST_TENANT_ID";
    private static final String TEST_INDEX = "test_index";
    private SdkClient sdkClient;

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Captor
    private ArgumentCaptor<PutItemRequest> putItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<GetItemRequest> getItemRequestArgumentCaptor;
    @Captor
    private ArgumentCaptor<DeleteItemRequest> deleteItemRequestArgumentCaptor;
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

        sdkClient = new DDBOpenSearchClient(dynamoDbClient);
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
        Assert.assertEquals(true, response.created());

        PutItemRequest putItemRequest = putItemRequestArgumentCaptor.getValue();
        Assert.assertEquals(TEST_INDEX, putItemRequest.tableName());
        Assert.assertEquals(TEST_ID, putItemRequest.item().get("id").s());
        Assert.assertEquals(TENANT_ID, putItemRequest.item().get("tenant_id").s());
        XContentBuilder sourceBuilder = XContentFactory.jsonBuilder();
        XContentBuilder builder = testDataObject.toXContent(sourceBuilder, ToXContent.EMPTY_PARAMS);
        Assert.assertEquals(builder.toString(), putItemRequest.item().get("source").s());
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
        XContentBuilder sourceBuilder = XContentFactory.jsonBuilder();
        XContentBuilder builder = testDataObject.toXContent(sourceBuilder, ToXContent.EMPTY_PARAMS);
        GetItemResponse getItemResponse = GetItemResponse
            .builder()
            .item(Map.ofEntries(Map.entry("source", AttributeValue.builder().s(builder.toString()).build())))
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
        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
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
    public void testDeleteDataObject_HappyCase() {
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
        Assert.assertTrue(deleteResponse.deleted());
    }

    @Test
    public void testDeleteDataObject_NullTenantId_UsesDefaultTenantId() {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().id(TEST_ID).index(TEST_INDEX).build();
        Mockito.when(dynamoDbClient.deleteItem(deleteItemRequestArgumentCaptor.capture())).thenReturn(DeleteItemResponse.builder().build());
        DeleteDataObjectResponse deleteResponse = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();
        DeleteItemRequest deleteItemRequest = deleteItemRequestArgumentCaptor.getValue();
        Assert.assertEquals("DEFAULT_TENANT", deleteItemRequest.key().get("tenant_id").s());
    }
}
