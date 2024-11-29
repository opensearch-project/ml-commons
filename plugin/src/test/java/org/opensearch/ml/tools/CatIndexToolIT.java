/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.rest.RestBaseAgentToolsIT;
import org.opensearch.ml.utils.TestHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CatIndexToolIT extends RestBaseAgentToolsIT {
    private String agentId;
    private final String question = "{\"parameters\":{\"question\":\"please help list all the index status in the current cluster?\"}}";

    @Before
    public void setUpCluster() throws Exception {
        registerCatIndexFlowAgent();
    }

    private List<String> createIndices(int count) throws IOException {
        List<String> indices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String indexName = "test" + i;
            createIndex(indexName, Settings.EMPTY);
            indices.add(indexName);
        }
        return indices;
    }

    private void registerCatIndexFlowAgent() throws Exception {
        String requestBody = Files
            .readString(
                Path.of(this.getClass().getClassLoader().getResource("org/opensearch/ml/tools/CatIndexAgentRegistration.json").toURI())
            );
        registerMLAgent(client(), requestBody, response -> agentId = (String) response.get("agent_id"));
    }

    public void testCatIndexWithFewIndices() throws IOException {
        List<String> indices = createIndices(10);
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, question, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractResult(responseStr);
        String[] actualLines = toolOutput.split("\\n");
        // plus 2 as there are one line of header and one line of system agent index, but sometimes the ml-config index will be created
        // then there will be one more line.
        assert actualLines.length == indices.size() + 2 || actualLines.length == indices.size() + 3;
        for (String index : indices) {
            assert Objects.requireNonNull(toolOutput).contains(index);
        }
    }

    public void testCatIndexWithMoreThan100Indices() throws IOException {
        List<String> indices = createIndices(101);
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, question, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractResult(responseStr);
        String[] actualLines = toolOutput.split("\\n");
        assert actualLines.length == indices.size() + 2 || actualLines.length == indices.size() + 3;
        for (String index : indices) {
            assert Objects.requireNonNull(toolOutput).contains(index);
        }
    }

    private String extractResult(String responseStr) {
        JsonArray output = JsonParser
            .parseString(responseStr)
            .getAsJsonObject()
            .get("inference_results")
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get("output")
            .getAsJsonArray();
        for (JsonElement element : output) {
            if ("response".equals(element.getAsJsonObject().get("name").getAsString())) {
                return element.getAsJsonObject().get("result").getAsString();
            }
        }
        return null;
    }
}
