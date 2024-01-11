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

public class RestMemoryGetConversationActionIT extends MLCommonsRestTestCase {
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

    public void testGetConversation() throws IOException {
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
        String id = (String) ccmap.get(ActionConstants.CONVERSATION_ID_FIELD);

        Response gcresponse = TestHelper
            .makeRequest(client(), "GET", ActionConstants.GET_CONVERSATION_REST_PATH.replace("{conversation_id}", id), null, "", null);
        assert (gcresponse != null);
        assert (TestHelper.restStatus(gcresponse) == RestStatus.OK);
        HttpEntity gchttpEntity = gcresponse.getEntity();
        String gcentitiyString = TestHelper.httpEntityToString(gchttpEntity);
        @SuppressWarnings("unchecked")
        Map<String, String> gcmap = gson.fromJson(gcentitiyString, Map.class);
        assert (gcmap.containsKey(ActionConstants.CONVERSATION_ID_FIELD) && gcmap.get(ActionConstants.CONVERSATION_ID_FIELD).equals(id));
        assert (gcmap.containsKey(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD)
            && gcmap.get(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD).equals("name"));
    }
}
