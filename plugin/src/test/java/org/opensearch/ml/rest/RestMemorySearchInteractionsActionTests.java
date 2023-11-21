/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.SearchInteractionsAction;
import org.opensearch.ml.memory.action.conversation.SearchInteractionsRequest;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.test.OpenSearchTestCase;

import com.google.gson.Gson;

public class RestMemorySearchInteractionsActionTests extends OpenSearchTestCase {

    Gson gson;

    @Before
    public void setup() {
        gson = new Gson();
    }

    public void testBasics() {
        RestMemorySearchInteractionsAction action = new RestMemorySearchInteractionsAction();
        assert (action.getName().equals("conversation_memory_search_interactions"));
        List<Route> routes = action.routes();
        assert (routes.size() == 2);
        assert (routes.get(0).equals(new Route(RestRequest.Method.POST, ActionConstants.SEARCH_INTERACTIONS_REST_PATH)));
        assert (routes.get(1).equals(new Route(RestRequest.Method.GET, ActionConstants.SEARCH_INTERACTIONS_REST_PATH)));
    }

    public void testPreprareRequest() throws Exception {
        RestMemorySearchInteractionsAction action = new RestMemorySearchInteractionsAction();
        RestRequest request = TestHelper.getSearchAllRestRequest();
        request.params().put(ActionConstants.CONVERSATION_ID_FIELD, "test_cid");

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<SearchInteractionsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchInteractionsRequest.class);
        verify(client, times(1)).execute(eq(SearchInteractionsAction.INSTANCE), argumentCaptor.capture(), any());
        assert (argumentCaptor.getValue().source().query() instanceof MatchAllQueryBuilder);
        assert (argumentCaptor.getValue().getConversationId().equals("test_cid"));
    }

    public void testSearchListener_TimeOut() throws Exception {
        RestMemorySearchInteractionsAction action = new RestMemorySearchInteractionsAction();
        RestChannel channel = mock(RestChannel.class);
        SearchResponse response = mock(SearchResponse.class);
        doReturn(true).when(response).isTimedOut();
        doReturn("timed out").when(response).toString();
        RestResponse brr = action.search(channel).buildResponse(response);
        assert (brr.status() == RestStatus.REQUEST_TIMEOUT);
    }

    public void testSearchListener_Success() throws Exception {
        RestMemorySearchInteractionsAction action = new RestMemorySearchInteractionsAction();
        RestChannel channel = mock(RestChannel.class);
        SearchResponse response = mock(SearchResponse.class);
        doReturn(false).when(response).isTimedOut();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        doReturn(builder).when(channel).newBuilder();
        doReturn(builder).when(response).toXContent(any(), any());
        RestResponse brr = action.search(channel).buildResponse(response);
        assert (brr.status() == RestStatus.OK);
    }
}
