/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

public class RestMLRegisterAgentModelSpecIT extends MLCommonsRestTestCase {

    private static final String UNIFIED_AGENT_API_ENABLED = "plugins.ml_commons.unified_agent_api_enabled";

    @Before
    public void enableUnifiedAgentApi() throws IOException {
        updateClusterSettings(UNIFIED_AGENT_API_ENABLED, true);
    }

    @After
    public void resetUnifiedAgentApi() throws IOException {
        updateClusterSettings(UNIFIED_AGENT_API_ENABLED, null);
    }

    public void testRegisterAgent_unknownFieldInModel_returnsBadRequest() {
        String body = "{\"name\":\"unified\",\"type\":\"conversational\","
            + "\"model\":{\"model_id\":\"gpt-mock\",\"model_provider\":\"openai/v1/chat/completions\","
            + "\"credential\":{\"openai_api_key\":\"dummy\"},\"parameters\":{\"temperature\":\"0.1\"}},"
            + "\"tools\":[{\"type\":\"ListIndexTool\"}]}";

        ResponseException e = expectThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(body), null)
        );
        assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        assertTrue(e.getMessage().contains("Unknown field [parameters] in model. Did you mean [model_parameters]?"));
    }
}
