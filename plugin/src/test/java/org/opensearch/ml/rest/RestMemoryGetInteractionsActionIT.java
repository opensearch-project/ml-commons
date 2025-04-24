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
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_INTERACTION_ID_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_INTERACTION_LIST_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMemoryGetInteractionsActionIT extends MLCommonsRestTestCase {

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

    public void testGetInteractions_NoConversation() throws IOException {
        Response response = TestHelper
            .makeRequest(client(), "GET", ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", "coffee"), null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (!map.containsKey("next_token"));
        assert (((ArrayList) map.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 0);
    }

    public void testGetInteractions_NoInteractions() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Response response = TestHelper
            .makeRequest(client(), "GET", ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid), null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (!map.containsKey("next_token"));
        assert (((ArrayList) map.get(RESPONSE_INTERACTION_LIST_FIELD)).isEmpty());
    }

    public void testGetInteractions_LastPage() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Map<String, Object> params = Map
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
                Collections.singletonMap("meta data", "some meta")
            );
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_INTERACTION_ID_FIELD));
        String iid = (String) map.get(RESPONSE_INTERACTION_ID_FIELD);

        Response response1 = TestHelper
            .makeRequest(client(), "GET", ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid), null, "", null);
        assert (response1 != null);
        assert (TestHelper.restStatus(response1) == RestStatus.OK);
        HttpEntity httpEntity1 = response1.getEntity();
        String entityString1 = TestHelper.httpEntityToString(httpEntity1);
        Map map1 = gson.fromJson(entityString1, Map.class);
        assert (map1.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (!map1.containsKey("next_token"));
        assert (((ArrayList) map1.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 1);
        @SuppressWarnings("unchecked")
        ArrayList<Map> interactions = (ArrayList<Map>) map1.get(RESPONSE_INTERACTION_LIST_FIELD);
        assert (((String) interactions.get(0).get(RESPONSE_INTERACTION_ID_FIELD)).equals(iid));
    }

    public void testGetInteractions_MorePages() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Map<String, Object> params = Map
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
                Collections.singletonMap("meta data", "some meta")
            );
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_INTERACTION_ID_FIELD));
        String iid = (String) map.get(RESPONSE_INTERACTION_ID_FIELD);

        Response response1 = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid),
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1"),
                "",
                null
            );
        assert (response1 != null);
        assert (TestHelper.restStatus(response1) == RestStatus.OK);
        HttpEntity httpEntity1 = response1.getEntity();
        String entityString1 = TestHelper.httpEntityToString(httpEntity1);
        Map map1 = gson.fromJson(entityString1, Map.class);
        assert (map1.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (map1.containsKey("next_token"));
        assert (((ArrayList) map1.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 1);
        @SuppressWarnings("unchecked")
        ArrayList<Map> interactions = (ArrayList<Map>) map1.get(RESPONSE_INTERACTION_LIST_FIELD);
        assert (((String) interactions.get(0).get(RESPONSE_INTERACTION_ID_FIELD)).equals(iid));
        assert (((Double) map1.get("next_token")).intValue() == 1);
    }

    public void testGetInteractions_NextPage() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Map<String, Object> params = Map
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
                Collections.singletonMap("meta data", "some meta")
            );
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_INTERACTION_ID_FIELD));
        String iid = (String) map.get(RESPONSE_INTERACTION_ID_FIELD);

        Response response2 = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{memory_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (response2 != null);
        assert (TestHelper.restStatus(response2) == RestStatus.OK);
        HttpEntity httpEntity2 = response2.getEntity();
        String entityString2 = TestHelper.httpEntityToString(httpEntity2);
        Map map2 = gson.fromJson(entityString2, Map.class);
        assert (map2.containsKey(RESPONSE_INTERACTION_ID_FIELD));
        String iid2 = (String) map2.get(RESPONSE_INTERACTION_ID_FIELD);

        Response response1 = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid),
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1"),
                "",
                null
            );
        assert (response1 != null);
        assert (TestHelper.restStatus(response1) == RestStatus.OK);
        HttpEntity httpEntity1 = response1.getEntity();
        String entityString1 = TestHelper.httpEntityToString(httpEntity1);
        Map map1 = gson.fromJson(entityString1, Map.class);
        assert (map1.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (map1.containsKey("next_token"));
        assert (((ArrayList) map1.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 1);
        @SuppressWarnings("unchecked")
        ArrayList<Map> interactions = (ArrayList<Map>) map1.get(RESPONSE_INTERACTION_LIST_FIELD);
        assert (((String) interactions.get(0).get(RESPONSE_INTERACTION_ID_FIELD)).equals(iid));
        assert (((Double) map1.get("next_token")).intValue() == 1);

        Response response3 = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{memory_id}", cid),
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1", ActionConstants.NEXT_TOKEN_FIELD, "1"),
                "",
                null
            );
        assert (response3 != null);
        assert (TestHelper.restStatus(response3) == RestStatus.OK);
        HttpEntity httpEntity3 = response3.getEntity();
        String entityString3 = TestHelper.httpEntityToString(httpEntity3);
        Map map3 = gson.fromJson(entityString3, Map.class);
        assert (map3.containsKey(RESPONSE_INTERACTION_LIST_FIELD));
        assert (map3.containsKey("next_token"));
        assert (((ArrayList) map3.get(RESPONSE_INTERACTION_LIST_FIELD)).size() == 1);
        @SuppressWarnings("unchecked")
        ArrayList<Map> interactions3 = (ArrayList<Map>) map3.get(RESPONSE_INTERACTION_LIST_FIELD);
        assert (((String) interactions3.get(0).get(RESPONSE_INTERACTION_ID_FIELD)).equals(iid2));
        assert (((Double) map3.get("next_token")).intValue() == 2);
    }
}
