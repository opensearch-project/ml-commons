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
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.utils.TestHelper.getSearchAllRestRequest;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
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
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
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

public class RestMLSearchModelGroupActionTests extends OpenSearchTestCase {

    private RestMLSearchModelGroupAction restMLSearchModelGroupAction;

    NodeClient client;
    private ThreadPool threadPool;
    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        restMLSearchModelGroupAction = new RestMLSearchModelGroupAction(mlFeatureEnabledSetting);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        XContentBuilder builder = XContentFactory.jsonBuilder();

        doReturn(builder).when(channel).newBuilder();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);

            String modelGroupContent = "{\"name\":\"modelName\",\"description\":\"description\",\"model_access_mode\":\"public\"}";
            SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelGroupContent));
            SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
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
        }).when(client).execute(eq(MLModelGroupSearchAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLSearchModelGroupAction mlSearchModelGroupAction = new RestMLSearchModelGroupAction(mlFeatureEnabledSetting);
        assertNotNull(mlSearchModelGroupAction);
    }

    public void testGetName() {
        String actionName = restMLSearchModelGroupAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_model_group_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchModelGroupAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertThat(postRoute.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/model_groups/_search", postRoute.getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getSearchAllRestRequest();
        restMLSearchModelGroupAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLModelGroupSearchAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        SearchRequest searchRequest = argumentCaptor.getValue();
        String[] indices = searchRequest.indices();
        assertArrayEquals(new String[] { ML_MODEL_GROUP_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            searchRequest.source().toString()
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
        }).when(client).execute(eq(MLModelGroupSearchAction.INSTANCE), any(), any());

        RestRequest request = getSearchAllRestRequest();
        restMLSearchModelGroupAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLModelGroupSearchAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        SearchRequest searchRequest = argumentCaptor.getValue();
        String[] indices = searchRequest.indices();
        assertArrayEquals(new String[] { ML_MODEL_GROUP_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            searchRequest.source().toString()
        );
        RestResponse restResponse = responseCaptor.getValue();
        assertEquals(RestStatus.REQUEST_TIMEOUT, restResponse.status());
    }
}
