/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestData;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RestMLGuardrailsIT extends MLCommonsRestTestCase {

    final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    final String COHERE_KEY = System.getenv("COHERE_KEY");

    final String completionModelConnectorEntity = "{\n"
        + "\"name\": \"OpenAI Connector\",\n"
        + "\"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
        + "\"version\": 1,\n"
        + "\"client_config\": {\n"
        + "    \"max_connection\": 20,\n"
        + "    \"connection_timeout\": 50000,\n"
        + "    \"read_timeout\": 50000\n"
        + "  },\n"
        + "\"protocol\": \"http\",\n"
        + "\"parameters\": {\n"
        + "    \"endpoint\": \"api.openai.com\",\n"
        + "    \"auth\": \"API_Key\",\n"
        + "    \"content_type\": \"application/json\",\n"
        + "    \"max_tokens\": 7,\n"
        + "    \"temperature\": 0,\n"
        + "    \"model\": \"gpt-3.5-turbo-instruct\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"openAI_key\": \""
        + OPENAI_KEY
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "      {"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"url\": \"https://${parameters.endpoint}/v1/completions\",\n"
        + "       \"headers\": {\n"
        + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "       },\n"
        + "       \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\",  \\\"max_tokens\\\": ${parameters.max_tokens},  \\\"temperature\\\": ${parameters.temperature} }\"\n"
        + "      }\n"
        + "  ]\n"
        + "}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() throws IOException, InterruptedException {
        disableClusterConnectorAccessControl();
        createStopWordsIndex();
        // TODO Do we really need to wait this long? This adds 20s to every test case run.
        // Can we instead check the cluster state and move on?
        Thread.sleep(20000);
    }

    public void testPredictRemoteModelSuccess() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelGuardrails("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a test\"\n" + "  }\n" + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        responseList = (List) responseMap.get("choices");
        if (responseList == null) {
            assertTrue(checkThrottlingOpenAI(responseMap));
            return;
        }
        responseMap = (Map) responseList.get(0);
        assertFalse(((String) responseMap.get("text")).isEmpty());
    }

    public void testPredictRemoteModelFailed() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("guardrails triggered for user input");
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelGuardrails("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a test of stop word.\"\n" + "  }\n" + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
    }

    protected void createStopWordsIndex() throws IOException {
        String indexName = "stop_words";
        String mappingContent = "\"properties\": {\n"
            + "      \"title\": {\n"
            + "        \"type\": \"text\"\n"
            + "      },\n"
            + "      \"query\": {\n"
            + "        \"type\": \"percolator\"\n"
            + "      }\n"
            + "    }";
        createIndex(indexName, restClientSettings(), mappingContent);

        Response bulkResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "_bulk?refresh=true",
                null,
                TestHelper.toHttpEntity(TestData.STOP_WORDS_DATA.replaceAll("stop_words", indexName)),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );

        Response statsResponse = TestHelper.makeRequest(client(), "GET", indexName, ImmutableMap.of(), "", null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
    }

    protected Response createConnector(String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, TestHelper.toHttpEntity(input), null);
    }

    protected Response registerRemoteModelGuardrails(String name, String connectorId) throws IOException {
        String registerModelGroupEntity = "{\n"
            + "  \"name\": \"remote_model_group\",\n"
            + "  \"description\": \"This is an example description\"\n"
            + "}";
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/model_groups/_register",
                null,
                TestHelper.toHttpEntity(registerModelGroupEntity),
                null
            );
        Map responseMap = parseResponseToMap(response);
        assertEquals((String) responseMap.get("status"), "CREATED");
        String modelGroupId = (String) responseMap.get("model_group_id");

        String registerModelEntity = "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "  \"version\": \"1.0.0\",\n"
            + "  \"description\": \"test model\",\n"
            + "  \"connector_id\": \""
            + connectorId
            + "\",\n"
            + "  \"guardrails\": {\n"
            + "    \"input_guardrail\": {\n"
            + "      \"stop_words\": [\n"
            + "        {"
            + "          \"index_name\": \"stop_words\",\n"
            + "          \"source_fields\": [\"title\"]\n"
            + "        }"
            + "      ],\n"
            + "      \"regex\": [\"regex1\", \"regex2\"]\n"
            + "    },\n"
            + "    \"output_guardrail\": {\n"
            + "      \"stop_words\": [\n"
            + "        {"
            + "          \"index_name\": \"stop_words\",\n"
            + "          \"source_fields\": [\"title\"]\n"
            + "        }"
            + "      ],\n"
            + "      \"regex\": [\"regex1\", \"regex2\"]\n"
            + "    }\n"
            + "  }\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    protected Response deployRemoteModel(String modelId) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, "", null);
    }

    protected Response predictRemoteModel(String modelId, String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_predict", null, input, null);
    }

    protected boolean checkThrottlingOpenAI(Map responseMap) {
        Map map = (Map) responseMap.get("error");
        String message = (String) map.get("message");
        return message.equals("You exceeded your current quota, please check your plan and billing details.");
    }

    protected void disableClusterConnectorAccessControl() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.connector_access_control_enabled\":false, \"plugins.ml_commons.sync_up_job_interval_in_seconds\":3}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    protected Response getTask(String taskId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
    }
}
