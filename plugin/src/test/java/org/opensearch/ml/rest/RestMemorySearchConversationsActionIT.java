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

public class RestMemorySearchConversationsActionIT extends MLCommonsRestTestCase {

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

    public void testSearchConversations_Successful() throws IOException {
        Response ccresponse = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (ccresponse != null);
        assert (TestHelper.restStatus(ccresponse) == RestStatus.OK);
        HttpEntity cchttpEntity = ccresponse.getEntity();
        String ccentityString = TestHelper.httpEntityToString(cchttpEntity);
        Map ccmap = gson.fromJson(ccentityString, Map.class);
        assert (ccmap.containsKey("conversation_id"));
        String id = (String) ccmap.get("conversation_id");

        Response scresponse = TestHelper
            .makeRequest(client(), "POST", ActionConstants.SEARCH_CONVERSATIONS_REST_PATH, null, matchAllSearchQuery(), null);
        assert (scresponse != null);
        assert (TestHelper.restStatus(scresponse) == RestStatus.OK);
        HttpEntity schttpEntity = scresponse.getEntity();
        String scentityString = TestHelper.httpEntityToString(schttpEntity);
        Map scmap = gson.fromJson(scentityString, Map.class);
        assert (scmap.containsKey("hits"));
        Map hitsmap = (Map) scmap.get("hits");
        assert (hitsmap.containsKey("hits"));
        ArrayList<Map> hitsarray = (ArrayList<Map>) hitsmap.get("hits");
        assert (hitsarray.size() == 1);
        for (Map hit : hitsarray) {
            assert (hit.containsKey("_id"));
            assert (hit.get("_id").equals(id));
        }
    }

}
