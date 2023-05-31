/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchModelTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;

    @Mock
    NamedXContentRegistry namedXContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    SearchRequest searchRequest;

    @Mock
    ActionListener<SearchResponse> actionListener;

    @Mock
    ThreadPool threadPool;

    private SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

    MLSearchHandler mlSearchHandler;
    SearchModelTransportAction searchModelTransportAction;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ClusterService clusterService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlSearchHandler = spy(new MLSearchHandler(client, namedXContentRegistry, modelAccessControlHelper, clusterService));
        searchModelTransportAction = new SearchModelTransportAction(transportService, actionFilters, mlSearchHandler);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        when(searchRequest.source()).thenReturn(searchSourceBuilder);
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);
    }

    public void test_DoExecute_admin() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(1)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_without_groupIds() {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(1)).search(any(), any());
    }

    public void test_DoExecute_searchModel_indexNotFound_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(1)).onFailure(any(IndexNotFoundException.class));
    }

    public void test_DoExecute_searchModel_MLResourceNotFoundException_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new MLResourceNotFoundException("ml resource not found exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(1)).onFailure(any(OpenSearchStatusException.class));
    }

    public void test_DoExecute_addBackendRoles_boolQuery() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("name", "model_IT")));
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_termQuery() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name", "model_IT"));
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    private SearchResponse createModelGroupSearchResponse() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"name\": \"model_group_IT\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }
}
