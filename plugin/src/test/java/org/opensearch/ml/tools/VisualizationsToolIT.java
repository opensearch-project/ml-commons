/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import static org.opensearch.ml.utils.TestHelper.makeRequest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.hc.core5.http.ParseException;
import org.junit.Assert;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.engine.tools.VisualizationsTool;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.transport.client.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class VisualizationsToolIT extends ToolIntegrationWithLLMTest {
    @Override
    List<PromptHandler> promptHandlers() {
        return List.of(new PromptHandler() {
            @Override
            LLMThought llmThought() {
                return LLMThought
                    .builder()
                    .action(VisualizationsTool.TYPE)
                    .actionInput("RAM")
                    .question("can you show me RAM info with visualization?")
                    .build();
            }
        }, new PromptHandler() {
            @Override
            LLMThought llmThought() {
                return LLMThought
                    .builder()
                    .action(VisualizationsTool.TYPE)
                    .actionInput("sales")
                    .question("how about the sales about this month?")
                    .build();
            }
        });
    }

    String toolType() {
        return VisualizationsTool.TYPE;
    }

    public void testVisualizationNotFound() throws IOException, ParseException {
        String requestBody = "{\"parameters\":{\"question\":\"can you show me RAM info with visualization?\"}}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, requestBody, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractAdditionalInfo(responseStr);
        Assert.assertEquals("No Visualization found", toolOutput);
    }

    public void testVisualizationFound() throws IOException, ParseException {
        String title = "[eCommerce] Sales by Category";
        String id = UUID.randomUUID().toString();
        prepareVisualization(title, id);
        String requestBody = "{\"parameters\":{\"question\":\"how about the sales about this month?\"}}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, requestBody, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractAdditionalInfo(responseStr);
        Assert.assertEquals("Title,Id\n" + String.format(Locale.ROOT, "%s,%s\n", title, id), toolOutput);
    }

    private void prepareVisualization(String title, String id) throws IOException {
        String body = "{\n"
            + "    \"visualization\": {\n"
            + "        \"title\": \""
            + title
            + "\"\n"
            + "    },\n"
            + "    \"type\": \"visualization\"\n"
            + "}";
        Response response = makeRequest(client(), "POST", String.format(Locale.ROOT, ".kibana/_doc/%s?refresh=true", id), null, body, null);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), RestStatus.CREATED.getStatus());
    }

    private String extractAdditionalInfo(String responseStr) {
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
                return element
                    .getAsJsonObject()
                    .get("dataAsMap")
                    .getAsJsonObject()
                    .get("additional_info")
                    .getAsJsonObject()
                    .get(String.format(Locale.ROOT, "%s.output", toolType()))
                    .getAsString();
            }
        }
        return null;
    }
}
