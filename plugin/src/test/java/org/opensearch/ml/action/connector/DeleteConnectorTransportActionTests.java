/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteConnectorTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

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

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteConnectorTransportAction deleteConnectorTransportAction;
    MLConnectorDeleteRequest mlConnectorDeleteRequest;
    ThreadContext threadContext;
    MLModel model;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId("connector_id").build();

        Settings settings = Settings.builder().build();
        deleteConnectorTransportAction = spy(
            new DeleteConnectorTransportAction(transportService, actionFilters, client, xContentRegistry, connectorAccessControlHelper)
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testDeleteConnector_Success() throws IOException {
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
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteConnector_ModelIndexNotFoundSuccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<Exception> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("ml_model index not found!"));
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteConnector_ConnectorNotFound() throws IOException {
        when(deleteResponse.getResult()).thenReturn(DocWriteResponse.Result.NOT_FOUND);

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
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteConnector_BlockedByModel() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        SearchResponse searchResponse = getNonEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 models are still using this connector, please delete or update the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_UserHasNoAccessException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You are not allowed to delete this connector", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchFailure() throws IOException {
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
        assertEquals("Search Failed!", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_SearchException() throws IOException {
        when(client.threadPool()).thenThrow(new RuntimeException("Thread Context Error!"));

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Thread Context Error!", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteConnector_ResourceNotFoundException() throws IOException {
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
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void test_ValidationFailedException() throws IOException {
        GetResponse getResponse = prepareMLConnector();
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).search(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        deleteConnectorTransportAction.doExecute(null, mlConnectorDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
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

    private SearchResponse getNonEmptySearchResponse() {
        SearchHit[] hits = new SearchHit[1];
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
