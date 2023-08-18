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
import java.util.Map;

import org.apache.http.HttpEntity;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.ml.utils.TestHelper;

public class RestConversationalDeleteConversationActionIT extends MLCommonsRestTestCase {

    public void testDeleteConversation_ThatExists() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey("conversation_id"));
        String id = (String) ccmap.get("conversation_id");

        Response response = TestHelper.makeRequest(client(), "DELETE", "_plugins/_ml/conversational/memory/" + id, null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("success"));
        assert ((Boolean) map.get("success"));
    }

    public void testDeleteConversation_ThatDoesNotExist() throws IOException {
        Response response = TestHelper.makeRequest(client(), "DELETE", "_plugins/_ml/conversational/memory/happybirthday", null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("success"));
        assert ((Boolean) map.get("success"));
    }

    public void testDeleteConversation_WithInteractions() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_PATH, null, "", null);
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
                ActionConstants.PROMPT_FIELD,
                "prompt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.AI_AGENT_FIELD,
                "agent",
                ActionConstants.INTER_ATTRIBUTES_FIELD,
                "attributes"
            );
        Response ciresponse = TestHelper.makeRequest(client(), "POST", "_plugins/_ml/conversational/memory/" + cid, params, "", null);
        assert (ciresponse != null);
        assert (TestHelper.restStatus(ciresponse) == RestStatus.OK);
        HttpEntity cihttpEntity = ciresponse.getEntity();
        String cientityString = TestHelper.httpEntityToString(cihttpEntity);
        Map cimap = gson.fromJson(cientityString, Map.class);
        assert (cimap.containsKey("interaction_id"));
        String iid = (String) cimap.get("interaction_id");

        Response dcresponse = TestHelper.makeRequest(client(), "DELETE", "_plugins/_ml/conversational/memory/" + cid, null, "", null);
        assert (dcresponse != null);
        assert (TestHelper.restStatus(dcresponse) == RestStatus.OK);
        HttpEntity dchttpEntity = dcresponse.getEntity();
        String dcentityString = TestHelper.httpEntityToString(dchttpEntity);
        Map dcmap = gson.fromJson(dcentityString, Map.class);
        assert (dcmap.containsKey("success"));
        assert ((Boolean) dcmap.get("success"));

        Response gcresponse = TestHelper.makeRequest(client(), "GET", "_plugins/_ml/conversational/memory/", null, "", null);
        assert (gcresponse != null);
        assert (TestHelper.restStatus(gcresponse) == RestStatus.OK);
        HttpEntity gchttpEntity = gcresponse.getEntity();
        String gcentityString = TestHelper.httpEntityToString(gchttpEntity);
        Map gcmap = gson.fromJson(gcentityString, Map.class);
        assert (gcmap.containsKey("conversations"));
        assert (!gcmap.containsKey("next_token"));
        assert (((ArrayList) gcmap.get("conversations")).size() == 0);

        Response giresponse = TestHelper.makeRequest(client(), "GET", "_plugins/_ml/conversational/memory/" + cid, null, "", null);
        assert (giresponse != null);
        assert (TestHelper.restStatus(giresponse) == RestStatus.OK);
        HttpEntity gihttpEntity = giresponse.getEntity();
        String gientityString = TestHelper.httpEntityToString(gihttpEntity);
        Map gimap = gson.fromJson(gientityString, Map.class);
        assert (gimap.containsKey("interactions"));
        assert (!gimap.containsKey("next_token"));
        assert (((ArrayList) gimap.get("interactions")).size() == 0);
    }
}
