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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.settings.MLCommonsSettings;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMemoryGetConversationsActionIT extends MLCommonsRestTestCase {

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

    public void testNoConversations_EmptyList() throws IOException {
        Response response = TestHelper.makeRequest(client(), "GET", ActionConstants.GET_CONVERSATIONS_REST_PATH, null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (!map.containsKey("next_token"));
        assert (((ArrayList) map.get(RESPONSE_CONVERSATION_LIST_FIELD)).size() == 0);
    }

    public void testGetConversations_LastPage() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String id = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Response response = TestHelper.makeRequest(client(), "GET", ActionConstants.GET_CONVERSATIONS_REST_PATH, null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (!map.containsKey("next_token"));
        @SuppressWarnings("unchecked")
        ArrayList<Map> conversations = (ArrayList<Map>) map.get(RESPONSE_CONVERSATION_LIST_FIELD);
        assert (conversations.size() == 1);
        assert (conversations.get(0).containsKey(CONVERSATION_ID_FIELD));
        assert (((String) conversations.get(0).get(CONVERSATION_ID_FIELD)).equals(id));
    }

    public void testConversations_MorePages() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(CONVERSATION_ID_FIELD));
        String id = (String) ccmap.get(CONVERSATION_ID_FIELD);

        Response response = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_CONVERSATIONS_REST_PATH,
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1"),
                "",
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (map.containsKey("next_token"));
        @SuppressWarnings("unchecked")
        ArrayList<Map> conversations = (ArrayList<Map>) map.get(RESPONSE_CONVERSATION_LIST_FIELD);
        assert (conversations.size() == 1);
        assert (conversations.get(0).containsKey(CONVERSATION_ID_FIELD));
        assert (((String) conversations.get(0).get(CONVERSATION_ID_FIELD)).equals(id));
        assert (((Double) map.get("next_token")).intValue() == 1);
    }

    public void testGetConversations_nextPage() throws IOException, InterruptedException {
        Response ccresponse1 = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse1 != null);
        assert (TestHelper.restStatus(ccresponse1) == RestStatus.OK);
        HttpEntity cchttpEntity1 = ccresponse1.getEntity();
        String ccentityString1 = TestHelper.httpEntityToString(cchttpEntity1);
        Map ccmap1 = gson.fromJson(ccentityString1, Map.class);
        assert (ccmap1.containsKey(CONVERSATION_ID_FIELD));
        logger.info("ccentityString1={}", ccentityString1);
        String id1 = (String) ccmap1.get(CONVERSATION_ID_FIELD);

        // wait for 0.1s to make sure update time is different between conversation 1 and 2
        TimeUnit.MICROSECONDS.sleep(100);

        Response ccresponse2 = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse2 != null);
        assert (TestHelper.restStatus(ccresponse2) == RestStatus.OK);
        HttpEntity cchttpEntity2 = ccresponse2.getEntity();
        String ccentityString2 = TestHelper.httpEntityToString(cchttpEntity2);
        Map ccmap2 = gson.fromJson(ccentityString2, Map.class);
        assert (ccmap2.containsKey(CONVERSATION_ID_FIELD));
        String id2 = (String) ccmap2.get(CONVERSATION_ID_FIELD);

        Response response1 = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_CONVERSATIONS_REST_PATH,
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1"),
                "",
                null
            );
        assert (response1 != null);
        assert (TestHelper.restStatus(response1) == RestStatus.OK);
        HttpEntity httpEntity1 = response1.getEntity();
        String entityString1 = TestHelper.httpEntityToString(httpEntity1);
        Map map1 = gson.fromJson(entityString1, Map.class);
        assert (map1.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (map1.containsKey("next_token"));
        @SuppressWarnings("unchecked")
        ArrayList<Map> conversations1 = (ArrayList<Map>) map1.get(RESPONSE_CONVERSATION_LIST_FIELD);
        assert (conversations1.size() == 1);
        assert (conversations1.get(0).containsKey(CONVERSATION_ID_FIELD));
        Assert.assertEquals(conversations1.get(0).get(CONVERSATION_ID_FIELD), id2);
        assert (((Double) map1.get("next_token")).intValue() == 1);

        Response response = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_CONVERSATIONS_REST_PATH,
                Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "1", ActionConstants.NEXT_TOKEN_FIELD, "1"),
                "",
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey(RESPONSE_CONVERSATION_LIST_FIELD));
        assert (map.containsKey("next_token"));
        @SuppressWarnings("unchecked")
        ArrayList<Map> conversations = (ArrayList<Map>) map.get(RESPONSE_CONVERSATION_LIST_FIELD);
        assert (conversations.size() == 1);
        assert (conversations.get(0).containsKey(CONVERSATION_ID_FIELD));
        assert (((String) conversations.get(0).get(CONVERSATION_ID_FIELD)).equals(id1));
        assert (((Double) map.get("next_token")).intValue() == 2);
    }
}
