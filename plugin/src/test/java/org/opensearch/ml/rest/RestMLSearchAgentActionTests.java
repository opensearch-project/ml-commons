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
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.utils.TestHelper.getSearchAllRestRequest;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLSearchAgentAction;
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

public class RestMLSearchAgentActionTests extends OpenSearchTestCase {

    private RestMLSearchAgentAction restMLSearchAgentAction;

    NodeClient client;
    private ThreadPool threadPool;
    @Mock
    RestChannel channel;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restMLSearchAgentAction = new RestMLSearchAgentAction(mlFeatureEnabledSetting);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        XContentBuilder builder = XContentFactory.jsonBuilder();

        doReturn(builder).when(channel).newBuilder();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);

            String agentContent = "{\"name\":\"Test_Agent\",\"type\":\"conversational\",\"llm\":\"xxx\"}";
            SearchHit agent = SearchHit.fromXContent(TestHelper.parser(agentContent));
            SearchHits hits = new SearchHits(new SearchHit[] { agent }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
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
        }).when(client).execute(eq(MLSearchAgentAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLSearchAgentAction mlSearchAgentAction = new RestMLSearchAgentAction(mlFeatureEnabledSetting);
        assertNotNull(mlSearchAgentAction);
    }

    public void testGetName() {
        String actionName = restMLSearchAgentAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_search_agent_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchAgentAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route postRoute = routes.get(0);
        assertEquals(RestRequest.Method.POST, postRoute.getMethod());
        assertThat(postRoute.getMethod(), Matchers.either(Matchers.is(RestRequest.Method.POST)).or(Matchers.is(RestRequest.Method.GET)));
        assertEquals("/_plugins/_ml/agents/_search", postRoute.getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getSearchAllRestRequest();
        restMLSearchAgentAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchActionRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchActionRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLSearchAgentAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        MLSearchActionRequest mlSearchActionRequest = argumentCaptor.getValue();
        String[] indices = mlSearchActionRequest.indices();
        assertArrayEquals(new String[] { ML_AGENT_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            mlSearchActionRequest.source().toString()
        );
        RestResponse agentResponse = responseCaptor.getValue();
        assertEquals(RestStatus.OK, agentResponse.status());
    }

    public void testPrepareRequest_disabled() throws Exception {
        RestRequest request = getSearchAllRestRequest();
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> restMLSearchAgentAction.handleRequest(request, channel, client));
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
        }).when(client).execute(eq(MLSearchAgentAction.INSTANCE), any(), any());

        RestRequest request = getSearchAllRestRequest();
        restMLSearchAgentAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchActionRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchActionRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(client, times(1)).execute(eq(MLSearchAgentAction.INSTANCE), argumentCaptor.capture(), any());
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        MLSearchActionRequest mlSearchActionRequest = argumentCaptor.getValue();
        String[] indices = mlSearchActionRequest.indices();
        assertArrayEquals(new String[] { ML_AGENT_INDEX }, indices);
        assertEquals(
            "{\"query\":{\"match_all\":{\"boost\":1.0}},\"version\":true,\"seq_no_primary_term\":true,\"_source\":{\"includes\":[],\"excludes\":[\"content\",\"model_content\",\"ui_metadata\"]}}",
            mlSearchActionRequest.source().toString()
        );
        RestResponse agentResponse = responseCaptor.getValue();
        assertEquals(RestStatus.REQUEST_TIMEOUT, agentResponse.status());
    }
}
