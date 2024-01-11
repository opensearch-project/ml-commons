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

import static org.opensearch.ml.utils.TestData.matchAllSearchQuery;

import java.io.IOException;
import java.util.ArrayList;
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

public class RestMemorySearchInteractionsActionIT extends MLCommonsRestTestCase {

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

    public void testSearchInteractions_Successfull() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey("conversation_id"));
        String cid = (String) ccmap.get("conversation_id");

        Map<String, String> params1 = Map
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
                "fish metadata"
            );
        Response ciresponse1 = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{conversation_id}", cid),
                null,
                gson.toJson(params1),
                null
            );
        assert (ciresponse1 != null);
        assert (TestHelper.restStatus(ciresponse1) == RestStatus.OK);
        HttpEntity cihttpEntity1 = ciresponse1.getEntity();
        String cientityString1 = TestHelper.httpEntityToString(cihttpEntity1);
        Map cimap1 = gson.fromJson(cientityString1, Map.class);
        assert (cimap1.containsKey("interaction_id"));
        String iid1 = (String) cimap1.get("interaction_id");

        Map<String, String> params2 = Map
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
                "france metadata"
            );
        Response ciresponse2 = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_INTERACTION_REST_PATH.replace("{conversation_id}", cid),
                null,
                gson.toJson(params2),
                null
            );
        assert (ciresponse2 != null);
        assert (TestHelper.restStatus(ciresponse2) == RestStatus.OK);
        HttpEntity cihttpEntity2 = ciresponse2.getEntity();
        String cientityString2 = TestHelper.httpEntityToString(cihttpEntity2);
        Map cimap2 = gson.fromJson(cientityString2, Map.class);
        assert (cimap2.containsKey("interaction_id"));
        String iid2 = (String) cimap2.get("interaction_id");

        Response siresponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.SEARCH_INTERACTIONS_REST_PATH.replace("{conversation_id}", cid),
                null,
                matchAllSearchQuery(),
                null
            );
        assert (siresponse != null);
        assert (TestHelper.restStatus(siresponse) == RestStatus.OK);
        HttpEntity sihttpEntity = siresponse.getEntity();
        String sientityString = TestHelper.httpEntityToString(sihttpEntity);
        Map simap = gson.fromJson(sientityString, Map.class);
        assert (simap.containsKey("hits"));
        Map hitsmap = (Map) simap.get("hits");
        assert (hitsmap.containsKey("hits"));
        ArrayList<Map> hitsarray = (ArrayList<Map>) hitsmap.get("hits");
        assert (hitsarray.size() == 2);
        for (Map hit : hitsarray) {
            assert (hit.containsKey("_id"));
            assert (hit.get("_id").equals(iid1) || hit.get("_id").equals(iid2));
        }
    }

}
