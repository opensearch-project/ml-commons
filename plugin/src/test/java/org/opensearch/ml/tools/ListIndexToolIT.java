/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHost;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.RestBaseAgentToolsIT;
import org.opensearch.ml.utils.TestHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ListIndexToolIT extends RestBaseAgentToolsIT {
    private String agentId;
    private final String question = "{\"parameters\":{\"question\":\"please help list all the index status in the current cluster?\"}}";

    @Before
    public void setUpCluster() throws Exception {
        registerListIndexFlowAgent();
    }

    public void testListIndexWithNoPermissions() throws Exception {
        if (!isHttps()) {
            log.info("Skipping permission test as security is not enabled");
            return;
        }

        String noPermissionUser = "no_permission_user";
        String password = "TestPassword123!";

        try {
            createUser(noPermissionUser, password, new ArrayList<>());

            final RestClient noPermissionClient = new SecureRestClientBuilder(
                getClusterHosts().toArray(new HttpHost[0]),
                isHttps(),
                noPermissionUser,
                password
            ).setSocketTimeout(60000).build();

            try {
                ResponseException exception = expectThrows(ResponseException.class, () -> {
                    TestHelper
                        .makeRequest(noPermissionClient, "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, question, null);
                });

                String errorMessage = exception.getMessage().toLowerCase();
                assertTrue(
                    "Expected permission error, got: " + errorMessage,
                    errorMessage.contains("no permissions") || errorMessage.contains("forbidden") || errorMessage.contains("unauthorized")
                );
            } finally {
                noPermissionClient.close();
            }
        } finally {
            deleteUser(noPermissionUser);
        }
    }

    private List<String> createIndices(int count) throws IOException {
        List<String> indices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String indexName = "test" + StringUtils.toRootLowerCase(randomAlphaOfLength(7));
            createIndex(indexName, Settings.EMPTY);
            indices.add(indexName);
        }
        return indices;
    }

    private void registerListIndexFlowAgent() throws Exception {
        String requestBody = Files
            .readString(
                Path.of(this.getClass().getClassLoader().getResource("org/opensearch/ml/tools/ListIndexAgentRegistration.json").toURI())
            );
        registerMLAgent(client(), requestBody, response -> agentId = (String) response.get("agent_id"));
    }

    public void testListIndexWithFewIndices() throws IOException {
        List<String> indices = createIndices(ListIndexTool.DEFAULT_PAGE_SIZE);
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, question, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractResult(responseStr);
        String[] actualLines = toolOutput.split("\\n");
        long testIndexCount = Arrays.stream(actualLines).filter(x -> x.contains("test")).count();
        assert testIndexCount == indices.size();
        for (String index : indices) {
            assert Objects.requireNonNull(toolOutput).contains(index);
        }
    }

    public void testListIndexWithMoreThan100Indices() throws IOException {
        List<String> indices = createIndices(ListIndexTool.DEFAULT_PAGE_SIZE + 1);
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, question, null);
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        String toolOutput = extractResult(responseStr);
        String[] actualLines = toolOutput.split("\\n");
        long testIndexCount = Arrays.stream(actualLines).filter(x -> x.contains("test")).count();
        assert testIndexCount == indices.size();
        for (String index : indices) {
            assert Objects.requireNonNull(toolOutput).contains(index);
        }
    }

    /**
     * An example of responseStr:
     * {
     *   "inference_results": [
     *     {
     *       "output": [
     *         {
     *           "name": "response",
     *           "result": "row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)\n1,yellow,open,test4,6ohWskucQ3u3xV9tMjXCkA,1,1,0,0,208b,208b\n2,yellow,open,test5,5AQLe-Z3QKyyLibbZ3Xcng,1,1,0,0,208b,208b\n3,yellow,open,test2,66Cj3zjlQ-G8I3vWeEONpQ,1,1,0,0,208b,208b\n4,yellow,open,test3,6A-aVxPiTj2U9GnupHQ3BA,1,1,0,0,208b,208b\n5,yellow,open,test8,-WKw-SCET3aTFuWCMMixrw,1,1,0,0,208b,208b"
     *         }
     *       ]
     *     }
     *   ]
     * }
     * @param responseStr
     * @return
     */
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
