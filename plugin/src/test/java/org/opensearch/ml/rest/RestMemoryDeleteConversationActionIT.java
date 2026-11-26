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

import static org.opensearch.ml.common.conversation.ActionConstants.CONVERSATION_ID_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_CONVERSATION_LIST_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_INTERACTION_ID_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_INTERACTION_LIST_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMemoryDeleteConversationActionIT extends MLCommonsRestTestCase {

    @Before
    public void setupFeatureSettings() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"" + MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey() + "\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    public void testDeleteConversation_ThatExists() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String id = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Response response = TestHelper
            .makeRequest(client(), "DELETE", ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{memory_id}", id), null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("success"));
        assert ((Boolean) map.get("success"));
    }

    public void testDeleteConversation_ThatDoesNotExist() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{memory_id}", "happybirthday"),
                null,
                "",
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("success"));
        assert ((Boolean) map.get("success"));
    }

    public void testDeleteConversation_WithInteractions() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Map<String, String> params = Map
            .of(
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.RESPONSE_ORIGIN_FIELD,
                "origin",
                ActionConstants.PROMPT_TEMPLATE_FIELD,
                "promtp template",
                ActionConstants.ADDITIONAL_INFO_FIELD,
                "some metadata"
            );
        Response ciresponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (ciresponse != null);
        assert (TestHelper.restStatus(ciresponse) == RestStatus.OK);
        HttpEntity cihttpEntity = ciresponse.getEntity();
        String cientityString = TestHelper.httpEntityToString(cihttpEntity);
        Map cimap = gson.fromJson(cientityString, Map.class);
        assert (cimap.containsKey(RESPONSE_INTERACTION_ID_FIELD));
        String iid = (String) cimap.get(RESPONSE_INTERACTION_ID_FIELD);

        Response dcresponse = TestHelper
            .makeRequest(client(), "DELETE", ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{memory_id}", cid), null, "", null);
        assert (dcresponse != null);
        assert (TestHelper.restStatus(dcresponse) == RestStatus.OK);
        HttpEntity dchttpEntity = dcresponse.getEntity();
        String dcentityString = TestHelper.httpEntityToString(dchttpEntity);
        Map dcmap = gson.fromJson(dcentityString, Map.class);
        assert (dcmap.containsKey("success"));
        assert ((Boolean) dcmap.get("success"));

        Response gcresponse = TestHelper.makeRequest(client(), "GET", ActionConstants.GET_CONVERSATIONS_REST_PATH, null, "", null);
        assert (gcresponse != null);
        assert (TestHelper.restStatus(gcresponse) == RestStatus.OK);
        HttpEntity gchttpEntity = gcresponse.getEntity();
        String gcentityString = TestHelper.httpEntityToString(gchttpEntity);
        Map gcmap = gson.fromJson(gcentityString, Map.class);
        assert (gcmap.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (!gcmap.containsKey("next_token"));
        assert (((ArrayList) gcmap.get(RESPONSE_CONVERSATION_LIST_FIELD)).size() == 0);

        try {
            Response giresponse = TestHelper
                .makeRequest(client(), "GET", ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid), null, "", null);
            assert (giresponse != null);
            assert (TestHelper.restStatus(giresponse) == RestStatus.OK);
            HttpEntity gihttpEntity = giresponse.getEntity();
            String gientityString = TestHelper.httpEntityToString(gihttpEntity);
            Map gimap = gson.fromJson(gientityString, Map.class);
            assert (gimap.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
            assert (!gimap.containsKey("next_token"));
            assert (((ArrayList) gimap.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 0);
            assert (false);
        } catch (ResponseException e) {
            assert (TestHelper.restStatus(e.getResponse()) == RestStatus.NOT_FOUND);
        }
    }

    public void testDeleteConversation_WithTraces_DeletesTraces() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        String cid = (String) gson.fromJson(TestHelper.httpEntityToString(ccresponse.getEntity()), Map.class).get(CONVERSATION_ID_FIELD);

        String createMessagePath = ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid);
        Response ciresponse = TestHelper
            .makeRequest(client(), "POST", createMessagePath, null, gson.toJson(Map.of(ActionConstants.INPUT_FIELD, "input")), null);
        assert (TestHelper.restStatus(ciresponse) == RestStatus.OK);
        String iid = (String) gson
            .fromJson(TestHelper.httpEntityToString(ciresponse.getEntity()), Map.class)
            .get(RESPONSE_INTERACTION_ID_FIELD);
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> trace = Map
                .of(
                    ActionConstants.INPUT_FIELD,
                    "trace input " + i,
                    ActionConstants.PARENT_INTERACTION_ID_FIELD,
                    iid,
                    ActionConstants.TRACE_NUMBER_FIELD,
                    i
                );
            Response traceResponse = TestHelper.makeRequest(client(), "POST", createMessagePath, null, gson.toJson(trace), null);
            assert (TestHelper.restStatus(traceResponse) == RestStatus.OK);
        }
        assertEquals(3, countMessagesOfMemory(cid));

        Response dcresponse = TestHelper
            .makeRequest(client(), "DELETE", ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{memory_id}", cid), null, "", null);
        assert (TestHelper.restStatus(dcresponse) == RestStatus.OK);
        assert ((Boolean) gson.fromJson(TestHelper.httpEntityToString(dcresponse.getEntity()), Map.class).get("success"));

        // The message and both of its traces are gone
        assertEquals(0, countMessagesOfMemory(cid));
    }

    private int countMessagesOfMemory(String memoryId) throws IOException {
        String index = ConversationalIndexConstants.INTERACTIONS_INDEX_NAME;
        // The messages index is a system index, so read it with the admin client (super-admin when security is enabled)
        TestHelper.makeRequest(adminClient(), "POST", index + "/_refresh", null, "", null);
        Response response = TestHelper
            .makeRequest(
                adminClient(),
                "POST",
                index + "/_count",
                null,
                gson
                    .toJson(
                        Map.of("query", Map.of("term", Map.of(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, memoryId)))
                    ),
                null
            );
        return ((Number) gson.fromJson(TestHelper.httpEntityToString(response.getEntity()), Map.class).get("count")).intValue();
    }
}
