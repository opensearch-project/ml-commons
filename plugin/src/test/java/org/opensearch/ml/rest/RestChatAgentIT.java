/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for a conversational (chat) agent that delegates to a flow agent via AgentTool.
 *
 * <p>Setup:
 * <ol>
 *   <li>Inner agent – a flow agent with ListIndexTool that queries iris_data.</li>
 *   <li>Outer agent – a flow agent with a single AgentTool whose {@code agent_id} points to the
 *       inner agent.</li>
 * </ol>
 *
 * <p>The test exercises the full AgentTool execution path and verifies that the outer agent
 * completes successfully and surfaces the inner agent's results.
 */
public class RestChatAgentIT extends MLCommonsRestTestCase {

    private String irisIndex = "iris_data";

    @Before
    public void setup() throws IOException, ParseException {
        ingestIrisData(irisIndex);
    }

    @After
    public void deleteIndices() throws IOException {
        deleteIndexWithAdminClient(irisIndex);
    }

    public void testChatAgentWithAgentTool() throws IOException {
        // Step 1 – register the inner flow agent backed by ListIndexTool.
        String innerAgentBody = "{\n"
            + "  \"name\": \"Test_Inner_Flow_Agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"Inner flow agent that lists indices\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"ListIndexTool\",\n"
            + "      \"name\": \"ListIndexTool\",\n"
            + "      \"parameters\": {\n"
            + "        \"input\": \"${parameters.question}\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(innerAgentBody), null);
        Map responseMap = parseResponseToMap(response);
        String innerAgentId = (String) responseMap.get("agent_id");
        assertNotNull(innerAgentId);

        // Step 2 – register the outer chat agent with AgentTool pointing at the inner agent.
        String outerAgentBody = "{\n"
            + "  \"name\": \"Test_Chat_Agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"Chat agent that delegates to the inner flow agent via AgentTool\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"AgentTool\",\n"
            + "      \"name\": \"AgentTool\",\n"
            + "      \"parameters\": {\n"
            + "        \"agent_id\": \""
            + innerAgentId
            + "\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(outerAgentBody), null);
        responseMap = parseResponseToMap(response);
        String outerAgentId = (String) responseMap.get("agent_id");
        assertNotNull(outerAgentId);

        // Step 3 – execute the outer agent. The question propagates through AgentTool to the inner agent.
        response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/" + outerAgentId + "/_execute",
                null,
                TestHelper.toHttpEntity("{\"parameters\":{\"question\":\"How many indices do I have?\"}}"),
                null
            );
        responseMap = parseResponseToMap(response);

        List inferenceResults = (List) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertFalse(inferenceResults.isEmpty());

        Map firstResult = (Map) inferenceResults.get(0);
        List output = (List) firstResult.get("output");
        assertNotNull(output);
        assertFalse(output.isEmpty());
    }
}
