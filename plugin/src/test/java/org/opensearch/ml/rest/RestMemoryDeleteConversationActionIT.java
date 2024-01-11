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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.settings.MLCommonsSettings;
import org.opensearch.ml.utils.TestHelper;

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
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
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
        assert (ccmap.containsKey("conversation_id"));
        String id = (String) ccmap.get("conversation_id");

        Response response = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{conversation_id}", id),
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

    public void testDeleteConversation_ThatDoesNotExist() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{conversation_id}", "happybirthday"),
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
        assert (ccmap.containsKey("conversation_id"));
        String cid = (String) ccmap.get("conversation_id");

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
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{conversation_id}", cid),
                null,
                gson.toJson(params),
                null
            );
        assert (ciresponse != null);
        assert (TestHelper.restStatus(ciresponse) == RestStatus.OK);
        HttpEntity cihttpEntity = ciresponse.getEntity();
        String cientityString = TestHelper.httpEntityToString(cihttpEntity);
        Map cimap = gson.fromJson(cientityString, Map.class);
        assert (cimap.containsKey("interaction_id"));
        String iid = (String) cimap.get("interaction_id");

        Response dcresponse = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                ActionConstants.DELETE_CONVERSATION_REST_PATH.replace("{conversation_id}", cid),
                null,
                "",
                null
            );
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
        assert (gcmap.containsKey("conversations"));
        assert (!gcmap.containsKey("next_token"));
        assert (((ArrayList) gcmap.get("conversations")).size() == 0);

        try {
            Response giresponse = TestHelper
                .makeRequest(client(), "GET", ActionConstants.GET_INTERACTIONS_REST_PATH.replace("{conversation_id}", cid), null, "", null);
            assert (giresponse != null);
            assert (TestHelper.restStatus(giresponse) == RestStatus.OK);
            HttpEntity gihttpEntity = giresponse.getEntity();
            String gientityString = TestHelper.httpEntityToString(gihttpEntity);
            Map gimap = gson.fromJson(gientityString, Map.class);
            assert (gimap.containsKey("interactions"));
            assert (!gimap.containsKey("next_token"));
            assert (((ArrayList) gimap.get("interactions")).size() == 0);
            assert (false);
        } catch (ResponseException e) {
            assert (TestHelper.restStatus(e.getResponse()) == RestStatus.NOT_FOUND);
        }
    }
}
