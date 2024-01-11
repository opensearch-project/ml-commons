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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.settings.MLCommonsSettings;
import org.opensearch.ml.utils.TestHelper;

public class RestMemoryGetInteractionActionIT extends MLCommonsRestTestCase {
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

    public void testGetInteraction() throws IOException {
        Response ccresponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_CONVERSATION_REST_PATH,
                null,
                gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, "name")),
                null
            );
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        @SuppressWarnings("unchecked")
        Map<String, String> ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey(ActionConstants.CONVERSATION_ID_FIELD));
        String cid = (String) ccmap.get(ActionConstants.CONVERSATION_ID_FIELD);

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
                Collections.singletonMap("metadata", "some metadata")
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
        @SuppressWarnings("unchecked")
        Map<String, String> cimap = gson.fromJson(cientityString, Map.class);
        assert (cimap.containsKey(ActionConstants.RESPONSE_INTERACTION_ID_FIELD));
        String iid = cimap.get(ActionConstants.RESPONSE_INTERACTION_ID_FIELD);

        Response giresponse = TestHelper
            .makeRequest(
                client(),
                "GET",
                ActionConstants.GET_INTERACTION_REST_PATH.replace("{conversation_id}", cid).replace("{interaction_id}", iid),
                null,
                "",
                null
            );
        assert (giresponse != null);
        assert (TestHelper.restStatus(giresponse) == RestStatus.OK);
        HttpEntity gihttpEntity = giresponse.getEntity();
        String gientityString = TestHelper.httpEntityToString(gihttpEntity);
        @SuppressWarnings("unchecked")
        Map<String, Object> gimap = gson.fromJson(gientityString, Map.class);
        assert (gimap.containsKey(ActionConstants.RESPONSE_INTERACTION_ID_FIELD)
            && gimap.get(ActionConstants.RESPONSE_INTERACTION_ID_FIELD).equals(iid));
        assert (gimap.containsKey(ActionConstants.CONVERSATION_ID_FIELD) && gimap.get(ActionConstants.CONVERSATION_ID_FIELD).equals(cid));
        assert (gimap.containsKey(ActionConstants.INPUT_FIELD) && gimap.get(ActionConstants.INPUT_FIELD).equals("input"));
        assert (gimap.containsKey(ActionConstants.PROMPT_TEMPLATE_FIELD)
            && gimap.get(ActionConstants.PROMPT_TEMPLATE_FIELD).equals("promtp template"));
        assert (gimap.containsKey(ActionConstants.AI_RESPONSE_FIELD) && gimap.get(ActionConstants.AI_RESPONSE_FIELD).equals("response"));
        assert (gimap.containsKey(ActionConstants.RESPONSE_ORIGIN_FIELD)
            && gimap.get(ActionConstants.RESPONSE_ORIGIN_FIELD).equals("origin"));
        assert (gimap.containsKey(ActionConstants.ADDITIONAL_INFO_FIELD)
            && gimap.get(ActionConstants.ADDITIONAL_INFO_FIELD).equals(Collections.singletonMap("metadata", "some metadata")));
    }
}
