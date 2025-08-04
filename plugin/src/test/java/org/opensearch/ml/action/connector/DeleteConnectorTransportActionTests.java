/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;

import java.io.IOException;
import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteConnectorTransportActionTests extends OpenSearchTestCase {

    private static final String CONNECTOR_ID = "connector_id";
    DeleteResponse deleteResponse = new DeleteResponse(new ShardId(ML_CONNECTOR_INDEX, "_na_", 0), CONNECTOR_ID, 1, 0, 2, true);

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
    NamedXContentRegistry xContentRegistry;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(CONNECTOR_ID).build();
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
    }

    public void testDeleteConnector_Success() {

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    public void testDeleteConnector_ModelIndexNotFoundSuccess() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<Exception> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("ml_model index not found!"));
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    public void testDeleteConnector_BlockedByModel() throws IOException {
        SearchResponse searchResponse = getNonEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 models are still using this connector, please delete or update the models first: [model_ID]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_UserHasNoAccessException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You are not allowed to delete this connector", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchFailure() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Search Failed!"));
            return null;
        }).when(client).search(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new ResourceNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search indices [.plugins-ml-model]", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchException() {
        when(client.threadPool()).thenThrow(new RuntimeException("Thread Context Error!"));

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Thread Context Error!", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_ResourceNotFoundException() throws InterruptedException {
        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new ResourceNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete data object from index .plugins-ml-connector", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_IndexNotFoundException() throws InterruptedException {
        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find connector", argumentCaptor.getValue().getMessage());
    }

    public void test_ValidationFailedException() throws IOException {
        GetResponse getResponse = prepareMLConnector();
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).search(any(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_MultiTenancyEnabled_NoTenantId() throws InterruptedException {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create a request without a tenant ID
        MLConnectorDeleteRequest requestWithoutTenant = MLConnectorDeleteRequest.builder().connectorId(CONNECTOR_ID).build();

        deleteConnectorTransportAction.doExecute(null, requestWithoutTenant, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLConnector() throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test_connector").protocol("http").build();
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
