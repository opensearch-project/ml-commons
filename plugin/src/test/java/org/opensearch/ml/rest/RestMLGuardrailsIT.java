/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestData;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableMap;

public class RestMLGuardrailsIT extends MLCommonsRestTestCase {

    final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    final String acceptRegex = "^\\s*[Aa]ccept.*$";
    final String rejectRegex = "^\\s*[Rr]eject.*$";

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

    final String completionModelConnectorEntityWithGuardrail = "{\n"
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
        + "    \"model\": \"gpt-3.5-turbo-instruct\",\n"
        + "    \"prompt\": \"You are a helpful assistant and an expert judge of content quality. Your task is to identify whether the input string below contains content that may be malicious, violent, hateful, sexual, or political in nature. Your answer should consist of a single word, either reject or accept. If the input belongs to any of these categories, please write reject. Otherwise, write accept. \\\\n\\\\nHere is the input: ${parameters.question}. \\\\n\\\\nYour answer: \",\n"
        + "        \"response_filter\": \"$.choices[0].text\""
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

        response = registerRemoteModelWithLocalRegexGuardrails("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
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
        Assume.assumeNotNull(OPENAI_KEY);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("guardrails triggered for user input");
        Response response = createConnector(completionModelConnectorEntity);

        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        response = registerRemoteModelWithLocalRegexGuardrails("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");

        waitForTask(taskId, MLTaskState.COMPLETED);
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a test of stop word.\"\n" + "  }\n" + "}";
        predictRemoteModel(modelId, predictInput);
    }

    public void testPredictRemoteModelFailedNonType() throws IOException, InterruptedException {
        // Skip test if key is null
        Assume.assumeNotNull(OPENAI_KEY);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("guardrails triggered for user input");
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        response = registerRemoteModelNonTypeGuardrails("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a test of stop word.\"\n" + "  }\n" + "}";
        predictRemoteModel(modelId, predictInput);
    }

    @Ignore
    public void testPredictRemoteModelSuccessWithModelGuardrail() throws IOException, InterruptedException {
        // Skip test if key is null
        Assume.assumeNotNull(OPENAI_KEY);
        // Create guardrails model.
        Response response = createConnector(completionModelConnectorEntityWithGuardrail);
        Map responseMap = parseResponseToMap(response);
        String guardrailConnectorId = (String) responseMap.get("connector_id");

        response = registerRemoteModel("guardrail model group", "openAI-GPT-3.5 completions", guardrailConnectorId);
        responseMap = parseResponseToMap(response);
        String guardrailModelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(guardrailModelId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        // Check the response from guardrails model that should be "accept".
        String predictInput = "{\n" + "  \"parameters\": {\n" + "    \"question\": \"hello\"\n" + "  }\n" + "}";
        response = predictRemoteModel(guardrailModelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        String validationResult = (String) responseMap.get("response");
        // Debugging: Print the response to check its format
        System.out.println("Validation Result: " + validationResult);
        System.out.println("Validation Result: [" + validationResult + "]");
        System.out.println("Validation Result Length: " + validationResult.length());

        // Ensure the regex matches the actual format
        Assert.assertTrue("Validation result does not match the regex", validateRegex(validationResult.trim(), acceptRegex));

        // Create predict model.
        response = createConnector(completionModelConnectorEntity);
        responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        response = registerRemoteModelWithModelGuardrails("openAI with guardrails", connectorId, guardrailModelId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        // Predict.
        predictInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"prompt\": \"${parameters.question}\",\n"
            + "    \"question\": \"hello\"\n"
            + "  }\n"
            + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        responseList = (List) responseMap.get("inference_results");
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

    public void testPredictRemoteModelFailedWithModelGuardrail() throws IOException, InterruptedException {
        // Skip test if key is null
        Assume.assumeNotNull(OPENAI_KEY);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("guardrails triggered for user input");
        // Create guardrails model.
        Response response = createConnector(completionModelConnectorEntityWithGuardrail);
        Map responseMap = parseResponseToMap(response);
        String guardrailConnectorId = (String) responseMap.get("connector_id");

        // Create the model ID
        response = registerRemoteModel("guardrail model group", "openAI-GPT-3.5 completions", guardrailConnectorId);
        responseMap = parseResponseToMap(response);
        String guardrailModelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(guardrailModelId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        // Check the response from guardrails model that should be "reject".
        String predictInput = "{\n" + "  \"parameters\": {\n" + "    \"question\": \"I will be executed or tortured.\"\n" + "  }\n" + "}";
        response = predictRemoteModel(guardrailModelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        String validationResult = (String) responseMap.get("response");
        Assert.assertTrue(validateRegex(validationResult, rejectRegex));

        // Create predict model.
        response = createConnector(completionModelConnectorEntity);
        responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        response = registerRemoteModelWithModelGuardrails("openAI with guardrails", connectorId, guardrailModelId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");

        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        // Predict with throwing guardrail exception.
        predictInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"prompt\": \"${parameters.question}\",\n"
            + "    \"question\": \"I will be executed or tortured.\"\n"
            + "  }\n"
            + "}";
        predictRemoteModel(modelId, predictInput);
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
                null
            );

        Response statsResponse = TestHelper.makeRequest(client(), "GET", indexName, ImmutableMap.of(), "", null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
    }

    protected Response createConnector(String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, TestHelper.toHttpEntity(input), null);
    }

    protected Response registerRemoteModel(String modelGroupName, String name, String connectorId) throws IOException {
        String registerModelGroupEntity = "{\n"
            + "  \"name\": \""
            + modelGroupName
            + "\",\n"
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
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "    }\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    protected Response registerRemoteModelWithLocalRegexGuardrails(String name, String connectorId) throws IOException {
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
            + "    \"type\": \"local_regex\",\n"
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
            + "},\n"
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "    }\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    protected Response registerRemoteModelNonTypeGuardrails(String name, String connectorId) throws IOException {
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
            + "},\n"
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "    }\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    protected Response registerRemoteModelWithModelGuardrails(String name, String connectorId, String guardrailModelId) throws IOException {

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
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "  },\n"
            + "  \"guardrails\": {\n"
            + "    \"type\": \"model\",\n"
            + "    \"input_guardrail\": {\n"
            + "      \"model_id\": \""
            + guardrailModelId
            + "\",\n"
            + "      \"response_validation_regex\": \"^\\\"\\\\s*[Aa]ccept\\\\s*\\\"$\""
            + "    },\n"
            + "    \"output_guardrail\": {\n"
            + "      \"model_id\": \""
            + guardrailModelId
            + "\",\n"
            + "      \"response_validation_regex\": \"^\\\"\\\\s*[Aa]ccept\\\\s*\\\"$\""
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
                null
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    protected Response getTask(String taskId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
    }

    private Boolean validateRegex(String input, String regex) {
        System.out.println("Original input: [" + input + "]");
        System.out.println("Input length: " + input.length());
        System.out.println("Input bytes: " + input.getBytes());

        // Clean up the input - remove brackets and trim
        String cleanedInput = input
            .trim()          // Remove leading/trailing whitespace
            .replaceAll("[\\[\\]]", "")  // Remove square brackets
            .trim();          // Trim again after removing brackets

        System.out.println("Cleaned input: [" + cleanedInput + "]");
        System.out.println("Cleaned input length: " + cleanedInput.length());
        System.out.println("Cleaned input bytes: " + cleanedInput.getBytes());
        System.out.println("Regex pattern: " + regex);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(cleanedInput);
        boolean matches = matcher.matches();
        System.out.println("Matches: " + matches);
        return matches;

    }
}
