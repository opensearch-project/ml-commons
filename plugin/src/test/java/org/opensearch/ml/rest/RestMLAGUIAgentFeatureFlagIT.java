/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AG_UI_DISABLED_MESSAGE;

import java.io.IOException;
import java.util.Map;

import org.junit.After;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

public class RestMLAGUIAgentFeatureFlagIT extends MLCommonsRestTestCase {

    private static final String AG_UI_ENABLED = "plugins.ml_commons.ag_ui_enabled";

    private static final String AG_UI_AGENT = "{\"name\":\"agui\",\"type\":\"AG_UI\","
        + "\"llm\":{\"model_id\":\"dummy-model\",\"parameters\":{\"system_prompt\":\"sys\"}},"
        + "\"parameters\":{\"_llm_interface\":\"openai/v1/chat/completions\"},"
        + "\"tools\":[{\"type\":\"ListIndexTool\",\"name\":\"ListIndexTool\"}]}";

    @After
    public void resetFlag() throws IOException {
        updateClusterSettings(AG_UI_ENABLED, null);
    }

    public void testRegisterAndExecuteAGUIAgent_rejectedWhenFeatureDisabled() throws IOException {
        // Register is rejected while the flag is at its default (false)
        ResponseException registerError = expectThrows(ResponseException.class, this::registerAGUIAgent);
        assertEquals(403, registerError.getResponse().getStatusLine().getStatusCode());
        assertTrue(TestHelper.httpEntityToString(registerError.getResponse().getEntity()).contains(ML_COMMONS_AG_UI_DISABLED_MESSAGE));

        // An agent registered while the flag was on can't be executed once it is turned off
        updateClusterSettings(AG_UI_ENABLED, true);
        Map registered = parseResponseToMap(registerAGUIAgent());
        String agentId = (String) registered.get("agent_id");
        assertNotNull(agentId);

        updateClusterSettings(AG_UI_ENABLED, false);
        ResponseException executeError = expectThrows(
            ResponseException.class,
            () -> TestHelper
                .makeRequest(
                    client(),
                    "POST",
                    "/_plugins/_ml/agents/" + agentId + "/_execute",
                    null,
                    TestHelper.toHttpEntity("{\"parameters\":{\"question\":\"hello\"}}"),
                    null
                )
        );
        assertEquals(403, executeError.getResponse().getStatusLine().getStatusCode());
        assertTrue(TestHelper.httpEntityToString(executeError.getResponse().getEntity()).contains(ML_COMMONS_AG_UI_DISABLED_MESSAGE));
    }

    private Response registerAGUIAgent() throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(AG_UI_AGENT), null);
    }
}
