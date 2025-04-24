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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.memory.action.conversation.SearchConversationsAction;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.test.OpenSearchTestCase;

import com.google.gson.Gson;

public class RestMemorySearchConversationsActionTests extends OpenSearchTestCase {

    Gson gson;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        gson = new Gson();
    }

    public void testBasics() {
        RestMemorySearchConversationsAction action = new RestMemorySearchConversationsAction(mlFeatureEnabledSetting);
        assert (action.getName().equals("conversation_memory_search_conversations"));
        List<Route> routes = action.routes();
        assert (routes.size() == 2);
        assert (routes.get(0).equals(new Route(RestRequest.Method.POST, ActionConstants.SEARCH_CONVERSATIONS_REST_PATH)));
        assert (routes.get(1).equals(new Route(RestRequest.Method.GET, ActionConstants.SEARCH_CONVERSATIONS_REST_PATH)));
    }

    public void testPreprareRequest() throws Exception {
        RestMemorySearchConversationsAction action = new RestMemorySearchConversationsAction(mlFeatureEnabledSetting);
        RestRequest request = TestHelper.getSearchAllRestRequest();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchActionRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchActionRequest.class);
        ArgumentCaptor<RestResponse> responseCaptor = ArgumentCaptor.forClass(RestResponse.class);

        verify(client, times(1)).execute(eq(SearchConversationsAction.INSTANCE), argumentCaptor.capture(), any());
        assert (argumentCaptor.getValue().source().query() instanceof MatchAllQueryBuilder);
    }
}
