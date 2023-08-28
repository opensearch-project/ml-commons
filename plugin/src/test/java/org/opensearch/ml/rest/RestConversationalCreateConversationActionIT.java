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
import java.util.Map;

import org.apache.http.HttpEntity;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.ml.utils.TestHelper;

public class RestConversationalCreateConversationActionIT extends MLCommonsRestTestCase {

    public void testCreateConversation() throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", ActionConstants.CREATE_CONVERSATION_REST_PATH, null, "", null);
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("conversation_id"));
    }

    public void testCreateConversationNamed() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                ActionConstants.CREATE_CONVERSATION_REST_PATH,
                Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, "name"),
                "",
                null
            );
        assert (response != null);
        assert (TestHelper.restStatus(response) == RestStatus.OK);
        HttpEntity httpEntity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(httpEntity);
        Map map = gson.fromJson(entityString, Map.class);
        assert (map.containsKey("conversation_id"));
    }
}
