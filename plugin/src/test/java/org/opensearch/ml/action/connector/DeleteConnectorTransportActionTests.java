/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.action.DocWriteResponse.Result.NOT_FOUND;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteConnectorTransportActionTests extends OpenSearchTestCase {
    private static final String CONNECTOR_ID = "connector_id";

    private static TestThreadPool testThreadPool = new TestThreadPool(
        TransportCreateConnectorActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    DeleteConnectorTransportAction deleteConnectorTransportAction;
    MLConnectorDeleteRequest mlConnectorDeleteRequest;
    ThreadContext threadContext;
    MLModel model;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(CONNECTOR_ID).build();
        when(deleteResponse.getId()).thenReturn(CONNECTOR_ID);
        when(deleteResponse.getShardId()).thenReturn(mock(ShardId.class));
        when(deleteResponse.getShardInfo()).thenReturn(mock(ShardInfo.class));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Settings settings = Settings.builder().build();
        deleteConnectorTransportAction = spy(
            new DeleteConnectorTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                connectorAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), isA(ActionListener.class));

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(any())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    public void testDeleteConnector_Success() throws InterruptedException {
        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(CONNECTOR_ID, captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    public void testDeleteConnector_ModelIndexNotFoundSuccess() throws IOException, InterruptedException {
        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<Exception> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("ml_model index not found!"));
            return null;
        }).when(client).search(any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(CONNECTOR_ID, captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    // TODO need to check if it has any value in it or not.
    public void testDeleteConnector_ConnectorNotFound() throws IOException, InterruptedException {
        when(deleteResponse.getResult()).thenReturn(NOT_FOUND);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(CONNECTOR_ID, captor.getValue().getId());
        assertEquals(NOT_FOUND, captor.getValue().getResult());
    }

    public void testDeleteConnector_BlockedByModel() throws IOException, InterruptedException {
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        SearchResponse searchResponse = getNonEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 models are still using this connector, please delete or update the models first: [model_ID]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_UserHasNoAccessException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You are not allowed to delete this connector", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchFailure() throws IOException {

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Search Failed!"));
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Search Failed!", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchException() throws IOException {

        when(client.threadPool()).thenThrow(new RuntimeException("Thread Context Error!"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());


        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Thread Context Error!", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_ResourceNotFoundException() throws IOException, InterruptedException {
        when(client.delete(any(DeleteRequest.class))).thenThrow(new ResourceNotFoundException("errorMessage"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());


        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void test_ValidationFailedException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_MultiTenancyEnabled_NoTenantId() throws InterruptedException {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create a request without a tenant ID
        MLConnectorDeleteRequest requestWithoutTenant = MLConnectorDeleteRequest.builder().connectorId(CONNECTOR_ID).build();

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteConnectorTransportAction.doExecute(null, requestWithoutTenant, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLConnector(String tenantId) throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test_connector").protocol("http").tenantId(tenantId).build();
        XContentBuilder content = connector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }

    private SearchResponse getEmptySearchResponse() {
        SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, true, false, null, 1);
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }

    private SearchResponse getNonEmptySearchResponse() throws IOException {
        SearchHit[] hits = new SearchHit[1];
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_ID\",\n"
            + "                    \"name\": \"test_model\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        hits[0] = model;
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponseSections searchSections = new SearchResponseSections(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            true,
            false,
            null,
            1
        );
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }
}
