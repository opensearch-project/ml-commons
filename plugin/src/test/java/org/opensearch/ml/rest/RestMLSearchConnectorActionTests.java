/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.utils.TestHelper.getSearchAllRestRequest;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.action.connector.SearchConnectorTransportAction;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLSearchConnectorActionTests extends OpenSearchTestCase {

    private RestMLSearchConnectorAction restMLSearchConnectorAction;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    NodeClient client;
    private ThreadPool threadPool;
    @Mock
    RestChannel channel;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        restMLSearchConnectorAction = new RestMLSearchConnectorAction(mlFeatureEnabledSetting);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        XContentBuilder builder = XContentFactory.jsonBuilder();

        doReturn(builder).when(channel).newBuilder();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);

            String connectorContent = "{\"name\":\"test_connector\",\"protocol\":\"http\",\"version\":1}";
            SearchHit connector = SearchHit.fromXContent(TestHelper.parser(connectorContent));
            SearchHits hits = new SearchHits(new SearchHit[] { connector }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
            SearchResponseSections searchSections = new SearchResponseSections(
                hits,
                InternalAggregations.EMPTY,
                null,
                false,
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
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).execute(eq(MLConnectorSearchAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLSearchConnectorAction mlSearchConnectorAction = new RestMLSearchConnectorAction(mlFeatureEnabledSetting);
        assertNotNull(mlSearchConnectorAction);
    }

    public void testGetName() {
        String actionName = restMLSearchConnectorAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_connector_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchConnectorAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertThat(postRoute.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/connectors/_search", postRoute.getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getSearchAllRestRequest();
        restMLSearchConnectorAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchActionRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchActionRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLConnectorSearchAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        MLSearchActionRequest mlSearchActionRequest = argumentCaptor.getValue();
        String[] indices = mlSearchActionRequest.indices();
        assertArrayEquals(new String[] { ML_CONNECTOR_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            mlSearchActionRequest.source().toString()
        );
        RestResponse restResponse = responseCaptor.getValue();
        assertNotEquals(RestStatus.REQUEST_TIMEOUT, restResponse.status());
    }

    public void testPrepareRequest_timeout() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);

            SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
            SearchResponseSections searchSections = new SearchResponseSections(
                hits,
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
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).execute(eq(MLConnectorSearchAction.INSTANCE), any(), any());

        RestRequest request = getSearchAllRestRequest();
        restMLSearchConnectorAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchActionRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchActionRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLConnectorSearchAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        MLSearchActionRequest mlSearchActionRequest = argumentCaptor.getValue();
        String[] indices = mlSearchActionRequest.indices();
        assertArrayEquals(new String[] { ML_CONNECTOR_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            mlSearchActionRequest.source().toString()
        );
        RestResponse restResponse = responseCaptor.getValue();
        assertEquals(RestStatus.REQUEST_TIMEOUT, restResponse.status());
    }

    public void testDoubleWrapper_handleIndexNotFound() {
        final IndexNotFoundException indexNotFoundException = new IndexNotFoundException("Index not found", ML_CONNECTOR_INDEX);
        final DummyActionListener actionListener = new DummyActionListener();

        SearchConnectorTransportAction.wrapListenerToHandleConnectorIndexNotFound(indexNotFoundException, actionListener);
        Assert.assertTrue(actionListener.success);
    }

    public void testDoubleWrapper_handleIndexNotFoundWrappedException() {
        final WrappedException wrappedException = new WrappedException();
        final DummyActionListener actionListener = new DummyActionListener();

        SearchConnectorTransportAction.wrapListenerToHandleConnectorIndexNotFound(wrappedException, actionListener);
        Assert.assertTrue(actionListener.success);
    }

    public void testDoubleWrapper_notRelatedException() {
        final RuntimeException exception = new RuntimeException("some random exception");
        final DummyActionListener actionListener = new DummyActionListener();

        SearchConnectorTransportAction.wrapListenerToHandleConnectorIndexNotFound(exception, actionListener);
        Assert.assertFalse(actionListener.success);
    }

    public class DummyActionListener implements ActionListener<SearchResponse> {
        public boolean success = false;

        @Override
        public void onResponse(SearchResponse searchResponse) {
            logger.info("success");
            this.success = true;
        }

        @Override
        public void onFailure(Exception e) {
            logger.error("failure", e);
            this.success = false;
        }
    }

    public static class WrappedException extends Exception implements OpenSearchWrapperException {
        @Override
        public synchronized Throwable getCause() {
            return new IndexNotFoundException("Index not found", ML_CONNECTOR_INDEX);
        }
    }
}
