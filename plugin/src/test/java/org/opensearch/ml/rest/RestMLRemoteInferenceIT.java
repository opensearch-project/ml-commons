/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMLRemoteInferenceIT extends MLCommonsRestTestCase {

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
        // TODO Do we really need to wait this long? This adds 20s to every test case run.
        // Can we instead check the cluster state and move on?
        Thread.sleep(20000);
    }

    public void testCreate_Get_DeleteConnector() throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        assertNotNull(connectorId);    // Testing create connector

        // Testing Get connector
        response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/connectors/" + connectorId, null, "", null);
        responseMap = parseResponseToMap(response);
        assertEquals("OpenAI Connector", responseMap.get("name"));
        assertEquals("1", responseMap.get("version"));
        assertEquals("The connector to public OpenAI model service for GPT 3.5", responseMap.get("description"));
        assertEquals("http", responseMap.get("protocol"));

        // Testing delete connector
        response = TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/connectors/" + connectorId, null, "", null);
        responseMap = parseResponseToMap(response);
        assertEquals("deleted", responseMap.get("result"));

    }

    private static String maskSensitiveInfo(String input) {
        // Regex to remove the whole credential object and replace it with "***"
        String regex = "\"credential\":\\{.*?}";
        return input.replaceAll(regex, "\"credential\": \"***\"");
    }

    @Test
    public void testMaskSensitiveInfo_withCredential() {
        String input = "{\"credential\":{\"username\":\"admin\",\"password\":\"secret\"}}";
        String expectedOutput = "{\"credential\": \"***\"}";
        String actualOutput = maskSensitiveInfo(input);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testMaskSensitiveInfo_noCredential() {
        String input = "{\"otherInfo\":\"someValue\"}";
        String expectedOutput = "{\"otherInfo\":\"someValue\"}";
        String actualOutput = maskSensitiveInfo(input);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testMaskSensitiveInfo_emptyInput() {
        String input = "";
        String expectedOutput = "";
        String actualOutput = maskSensitiveInfo(input);
        assertEquals(expectedOutput, actualOutput);
    }

    public void testSearchConnectors_beforeCreation() throws IOException {
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/connectors/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(0.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchConnectors_afterCreation() throws IOException {
        createConnector(completionModelConnectorEntity);
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/connectors/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals((Double) 1.0, (Double) ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchRemoteModels_beforeCreation() throws IOException {
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(0.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchRemoteModels_afterCreation() throws IOException {
        registerRemoteModel();
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(1.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchModelGroups_beforeCreation() throws IOException {
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/model_groups/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(0.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchModelGroups_afterCreation() throws IOException {
        registerRemoteModel();
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/model_groups/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(1.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchMLTasks_beforeCreation() throws IOException {
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/tasks/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(0.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testSearchMLTasks_afterCreation() throws IOException {
        registerRemoteModel();

        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/tasks/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals(1.0, ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testDeployRemoteModel() throws IOException, InterruptedException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = getTask(taskId);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        response = deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        assertEquals("COMPLETED", responseMap.get("status"));
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
    }

    public void testPredictWithAutoDeployAndTTL_RemoteModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            System.out.println("OPENAI_KEY is null");
            return;
        }
        Response updateCBSettingResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.jvm_heap_memory_threshold\":100}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, updateCBSettingResponse.getStatusLine().getStatusCode());

        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelWithTTLAndSkipHeapMemCheck("openAI-GPT-3.5 completions", connectorId, 1);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
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

        getModelProfile(modelId, verifyRemoteModelDeployed());
        TimeUnit.SECONDS.sleep(71);
        assertTrue(getModelProfile(modelId, verifyRemoteModelDeployed()).isEmpty());
    }

    public void testPredictRemoteModelWithInterface(String testCase, Consumer<Map> verifyResponse, Consumer<Exception> verifyException)
        throws IOException,
        InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelWithInterface("openAI-GPT-3.5 completions", connectorId, testCase);
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
        try {
            response = predictRemoteModel(modelId, predictInput);
            responseMap = parseResponseToMap(response);
            verifyResponse.accept(responseMap);
        } catch (Exception e) {
            verifyException.accept(e);
        }
    }

    public void testPredictRemoteModelWithCorrectInterface() throws IOException, InterruptedException {
        testPredictRemoteModelWithInterface("correctInterface", (responseMap) -> {
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
        }, null);
    }

    public void testPredictRemoteModelWithWrongInputInterface() throws IOException, InterruptedException {
        testPredictRemoteModelWithInterface("wrongInputInterface", null, (exception) -> {
            assertTrue(exception instanceof org.opensearch.client.ResponseException);
            String stackTrace = ExceptionUtils.getStackTrace(exception);
            assertTrue(stackTrace.contains("Error validating input schema"));
        });
    }

    public void testPredictRemoteModelWithWrongOutputInterface() throws IOException, InterruptedException {
        testPredictRemoteModelWithInterface("wrongOutputInterface", null, (exception) -> {
            assertTrue(exception instanceof org.opensearch.client.ResponseException);
            String stackTrace = ExceptionUtils.getStackTrace(exception);
            assertTrue(stackTrace.contains("Error validating output schema"));
        });
    }

    public void testPredictRemoteModelWithSkipValidatingMissingParameter(
        String testCase,
        Consumer<Map> verifyResponse,
        Consumer<Exception> verifyException
    ) throws IOException,
        InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(this.getConnectorBodyBySkipValidatingMissingParameter(testCase));
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelWithInterface("openAI-GPT-3.5 completions", connectorId, "correctInterface");
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
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a ${parameters.test}\"\n" + "  }\n" + "}";
        try {
            response = predictRemoteModel(modelId, predictInput);
            responseMap = parseResponseToMap(response);
            verifyResponse.accept(responseMap);
        } catch (Exception e) {
            verifyException.accept(e);
        }
    }

    public void testPredictRemoteModelWithSkipValidatingMissingParameterMissing() throws IOException, InterruptedException {
        testPredictRemoteModelWithSkipValidatingMissingParameter("missing", null, (exception) -> {
            assertTrue(exception.getMessage().contains("Some parameter placeholder not filled in payload: test"));
        });
    }

    public void testPredictRemoteModelWithSkipValidatingMissingParameterEnabled() throws IOException, InterruptedException {
        testPredictRemoteModelWithSkipValidatingMissingParameter("enabled", (responseMap) -> {
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
        }, null);
    }

    public void testPredictRemoteModelWithSkipValidatingMissingParameterDisabled() throws IOException, InterruptedException {
        testPredictRemoteModelWithSkipValidatingMissingParameter("disabled", null, (exception) -> {
            assertTrue(exception.getMessage().contains("Some parameter placeholder not filled in payload: test"));
        });
    }

    public void testOpenAIChatCompletionModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"OpenAI chat model Connector\",\n"
            + "  \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
            + "  \"version\": 1,\n"
            + "\"client_config\": {\n"
            + "    \"max_connection\": 20,\n"
            + "    \"connection_timeout\": 50000,\n"
            + "    \"read_timeout\": 50000\n"
            + "  },\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.openai.com\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"max_tokens\": 7,\n"
            + "      \"temperature\": 0,\n"
            + "      \"model\": \"gpt-3.5-turbo\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"openAI_key\": \""
            + OPENAI_KEY
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "      \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
            + "          \"headers\": { \n"
            + "            \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages} }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 chat model", connectorId);
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
        String predictInput = "{\n"
            + "  \"parameters\": {\n"
            + "      \"messages\": [{\"role\": \"user\", \"content\": \"Hello!\"}]\n"
            + "  }\n"
            + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        // TODO handle throttling error
        assertNotNull(responseMap);

        response = undeployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        assertTrue(responseMap.toString().contains("undeployed"));
    }

    @Ignore
    public void testOpenAIEditsModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"OpenAI Edit model Connector\",\n"
            + "  \"description\": \"The connector to public OpenAI edit model service\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"http\",\n"
            + "\"client_config\": {\n"
            + "    \"max_connection\": 20,\n"
            + "    \"connection_timeout\": 50000,\n"
            + "    \"read_timeout\": 50000\n"
            + "  },\n"
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.openai.com\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"model\": \"gpt-4\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"openAI_key\": \""
            + OPENAI_KEY
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "      \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "      \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": [{\\\"role\\\": \\\"user\\\", \\\"content\\\": \\\"${parameters.input}\\\"}]}\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-4 edit model", connectorId);
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
        String predictInput = "{\"parameters\":{\"input\":\"What day of the wek is it?\"}}";
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
        responseMap = (Map) responseMap.get("message");

        assertFalse(((String) responseMap.get("content")).isEmpty());
    }

    @Ignore
    public void testOpenAIModerationsModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"OpenAI moderations model Connector\",\n"
            + "  \"description\": \"The connector to public OpenAI moderations model service\",\n"
            + "  \"version\": 1,\n"
            + "\"client_config\": {\n"
            + "    \"max_connection\": 20,\n"
            + "    \"connection_timeout\": 50000,\n"
            + "    \"read_timeout\": 50000\n"
            + "  },\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.openai.com\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"model\": \"moderations\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"openAI_key\": \""
            + OPENAI_KEY
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "      \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://api.openai.com/v1/moderations\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"input\\\": \\\"${parameters.input}\\\" }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 moderations model", connectorId);
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
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"input\": \"I want to kill them.\"\n" + "  }\n" + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        responseList = (List) responseMap.get("results");
        if (responseList == null) {
            assertTrue(checkThrottlingOpenAI(responseMap));
            return;
        }
        responseMap = (Map) responseList.get(0);
        assertTrue((Boolean) responseMap.get("flagged"));
        responseMap = (Map) responseMap.get("categories");
        assertTrue((Boolean) responseMap.get("violence"));
    }

    public void testOpenAITextEmbeddingModel_UTF8() throws IOException, InterruptedException {
        testOpenAITextEmbeddingModel("UTF-8", (responseMap) -> {
            List responseList = (List) responseMap.get("inference_results");
            responseMap = (Map) responseList.get(0);
            responseList = (List) responseMap.get("output");
            responseMap = (Map) responseList.get(0);
            responseList = (List) responseMap.get("data");
            assertFalse(responseList.isEmpty());
        }, null);
    }

    private void testOpenAITextEmbeddingModel(String charset, Consumer<Map> verifyResponse, Consumer<Exception> verifyException)
        throws IOException,
        InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"OpenAI text embedding model Connector\",\n"
            + "  \"description\": \"The connector to public OpenAI text embedding model service\",\n"
            + "  \"version\": 1,\n"
            + "\"client_config\": {\n"
            + "    \"max_connection\": 20,\n"
            + "    \"connection_timeout\": 50000,\n"
            + "    \"read_timeout\": 50000\n"
            + "  },\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "      \"model\": \"text-embedding-ada-002\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"openAI_key\": \""
            + OPENAI_KEY
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "      \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://api.openai.com/v1/embeddings\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"input\\\": ${parameters.input}, \\\"model\\\": \\\"${parameters.model}\\\" }\",\n"
            + "          \"pre_process_function\": \"connector.pre_process.openai.embedding\",\n"
            + "          \"post_process_function\": \"connector.post_process.openai.embedding\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI text embedding model", connectorId);
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
        String predictInput = "{\n"
            + "  \"parameters\": {\n"
            + "      \"input\": [\"This is a string containing Moët Hennessy\"],\n"
            + "      \"charset\": \""
            + charset
            + "\"\n"
            + "  }\n"
            + "}";
        try {
            response = predictRemoteModel(modelId, predictInput);
            responseMap = parseResponseToMap(response);
            verifyResponse.accept(responseMap);
        } catch (Exception e) {
            verifyException.accept(e);
        }
    }

    public void testCohereGenerateTextModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (COHERE_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"Cohere generate text model Connector\",\n"
            + "  \"description\": \"The connector to public Cohere generate text model service\",\n"
            + "  \"version\": 1,\n"
            + "\"client_config\": {\n"
            + "    \"max_connection\": 20,\n"
            + "    \"connection_timeout\": 50000,\n"
            + "    \"read_timeout\": 50000\n"
            + "  },\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.cohere.ai\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"max_tokens\": \"20\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"cohere_key\": \""
            + COHERE_KEY
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "      \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://${parameters.endpoint}/v1/generate\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.cohere_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"max_tokens\\\": ${parameters.max_tokens}, \\\"return_likelihoods\\\": \\\"NONE\\\", \\\"truncate\\\": \\\"END\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\" }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("cohere generate text model", connectorId);
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
        String predictInput = "{\n"
            + "  \"parameters\": {\n"
            + "      \"prompt\": \"Once upon a time in a magical land called\",\n"
            + "      \"max_tokens\": 40\n"
            + "  }\n"
            + "}";
        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        responseList = (List) responseMap.get("generations");
        responseMap = (Map) responseList.get(0);
        assertFalse(((String) responseMap.get("text")).isEmpty());
    }

    public static Response createConnector(String input) throws IOException {
        try {
            return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, TestHelper.toHttpEntity(input), null);
        } catch (ResponseException e) {
            String sanitizedMessage = maskSensitiveInfo(e.getMessage());// Log sanitized message
            throw new RuntimeException("Request failed: " + sanitizedMessage);  // Re-throw sanitized exception
        }
    }

    public static Response registerRemoteModel(String name, String connectorId) throws IOException {
        return registerRemoteModel("remote_model_group", name, connectorId);
    }

    public static Response registerRemoteModel(String modelGroupName, String name, String connectorId) throws IOException {
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

    public static Response registerRemoteModelWithTTLAndSkipHeapMemCheck(String name, String connectorId, int ttl) throws IOException {
        String registerModelGroupEntity = "{\n"
            + "  \"name\": \"remote_model_group\",\n"
            + "  \"description\": \"This is an example description\"\n"
            + "}";
        String updateJVMHeapThreshold = "{\"persistent\":{\"plugins.ml_commons.jvm_heap_memory_threshold\":0}}";
        TestHelper.makeRequest(client(), "PUT", "/_cluster/settings", null, TestHelper.toHttpEntity(updateJVMHeapThreshold), null);
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
            + "  \"deploy_setting\": "
            + " { \"model_ttl_minutes\": "
            + ttl
            + "},\n"
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "    }\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    private String getConnectorBodyBySkipValidatingMissingParameter(String testCase) {
        switch (testCase) {
            case "missing":
                return completionModelConnectorEntity;
            case "enabled":
                return "{\n"
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
                    + "    \"skip_validating_missing_parameters\": \"true\"\n"
                    + "  },\n"
                    + "  \"credential\": {\n"
                    + "    \"openAI_key\": \""
                    + this.OPENAI_KEY
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
            case "disabled":
                return "{\n"
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
                    + "    \"skip_validating_missing_parameters\": \"false\"\n"
                    + "  },\n"
                    + "  \"credential\": {\n"
                    + "    \"openAI_key\": \""
                    + this.OPENAI_KEY
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
            default:
                throw new IllegalArgumentException("Invalid test case");
        }
    }

    public static Response registerRemoteModelWithInterface(String name, String connectorId, String testCase) throws IOException {
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

        final String openaiConnectorEntityWithCorrectInterface = "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"connector_id\": \""
            + connectorId
            + "\",\n"
            + "  \"interface\": {\n"
            + "    \"input\": {\n"
            + "      \"properties\": {\n"
            + "        \"parameters\": {\n"
            + "          \"properties\": {\n"
            + "            \"prompt\": {\n"
            + "              \"type\": \"string\",\n"
            + "              \"description\": \"This is a test description field\"\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    },\n"
            + "    \"output\": {\n"
            + "      \"properties\": {\n"
            + "        \"inference_results\": {\n"
            + "          \"type\": \"array\",\n"
            + "          \"items\": {\n"
            + "            \"type\": \"object\",\n"
            + "            \"properties\": {\n"
            + "              \"output\": {\n"
            + "                \"type\": \"array\",\n"
            + "                \"items\": {\n"
            + "                  \"properties\": {\n"
            + "                    \"name\": {\n"
            + "                      \"type\": \"string\",\n"
            + "                      \"description\": \"This is a test description field\"\n"
            + "                    },\n"
            + "                    \"dataAsMap\": {\n"
            + "                      \"type\": \"object\",\n"
            + "                      \"description\": \"This is a test description field\"\n"
            + "                    }\n"
            + "                  }\n"
            + "                },\n"
            + "                \"description\": \"This is a test description field\"\n"
            + "              },\n"
            + "              \"status_code\": {\n"
            + "                \"type\": \"integer\",\n"
            + "                \"description\": \"This is a test description field\"\n"
            + "              }\n"
            + "            }\n"
            + "          },\n"
            + "          \"description\": \"This is a test description field\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        final String openaiConnectorEntityWithWrongInputInterface = "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"connector_id\": \""
            + connectorId
            + "\",\n"
            + "  \"interface\": {\n"
            + "    \"input\": {\n"
            + "      \"properties\": {\n"
            + "        \"parameters\": {\n"
            + "          \"properties\": {\n"
            + "            \"prompt\": {\n"
            + "              \"type\": \"integer\",\n"
            + "              \"description\": \"This is a test description field\"\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        final String openaiConnectorEntityWithWrongOutputInterface = "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"connector_id\": \""
            + connectorId
            + "\",\n"
            + "  \"interface\": {\n"
            + "    \"output\": {\n"
            + "      \"properties\": {\n"
            + "        \"inference_results\": {\n"
            + "          \"type\": \"array\",\n"
            + "          \"items\": {\n"
            + "            \"type\": \"object\",\n"
            + "            \"properties\": {\n"
            + "              \"output\": {\n"
            + "                \"type\": \"integer\",\n"
            + "                \"description\": \"This is a test description field\"\n"
            + "              },\n"
            + "              \"status_code\": {\n"
            + "                \"type\": \"integer\",\n"
            + "                \"description\": \"This is a test description field\"\n"
            + "              }\n"
            + "            }\n"
            + "          },\n"
            + "          \"description\": \"This is a test description field\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        switch (testCase) {
            case "correctInterface":
                return TestHelper
                    .makeRequest(
                        client(),
                        "POST",
                        "/_plugins/_ml/models/_register",
                        null,
                        TestHelper.toHttpEntity(openaiConnectorEntityWithCorrectInterface),
                        null
                    );
            case "wrongInputInterface":
                return TestHelper
                    .makeRequest(
                        client(),
                        "POST",
                        "/_plugins/_ml/models/_register",
                        null,
                        TestHelper.toHttpEntity(openaiConnectorEntityWithWrongInputInterface),
                        null
                    );
            case "wrongOutputInterface":
                return TestHelper
                    .makeRequest(
                        client(),
                        "POST",
                        "/_plugins/_ml/models/_register",
                        null,
                        TestHelper.toHttpEntity(openaiConnectorEntityWithWrongOutputInterface),
                        null
                    );
            default:
                throw new IllegalArgumentException("Invalid test case");
        }
    }

    public static Response deployRemoteModel(String modelId) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, "", null);
    }

    public Response predictRemoteModel(String modelId, String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_predict", null, input, null);
    }

    public Response undeployRemoteModel(String modelId) throws IOException {
        String undeployEntity = "{\n"
            + "  \"SYqCMdsFTumUwoHZcsgiUg\": {\n"
            + "    \"stats\": {\n"
            + "      \""
            + modelId
            + "\": \"undeployed\"\n"
            + "    }\n"
            + "  }\n"
            + "}";
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_undeploy", null, undeployEntity, null);
    }

    protected boolean checkThrottlingOpenAI(Map responseMap) {
        Map map = (Map) responseMap.get("error");
        String message = (String) map.get("message");
        return message.equals("You exceeded your current quota, please check your plan and billing details.");
    }

    public static void disableClusterConnectorAccessControl() throws IOException {
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

    public static Response getTask(String taskId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
    }

    public String registerRemoteModel() throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        logger.info("task ID created: {}", taskId);
        return taskId;
    }

    public String registerRemoteModelWithInterface(String testCase) throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelWithInterface("openAI-GPT-3.5 completions", connectorId, testCase);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        logger.info("task ID created: {}", taskId);
        return taskId;
    }

    public void testPredictRemoteModelFeatureDisabled() throws IOException, InterruptedException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModelWithInterface("openAI-GPT-3.5 completions", connectorId, "correctInterface");
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
        response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.remote_inference.enabled\":false}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
        String predictInput = "{\n" + "  \"parameters\": {\n" + "      \"prompt\": \"Say this is a test\"\n" + "  }\n" + "}";
        try {
            predictRemoteModel(modelId, predictInput);
        } catch (Exception e) {
            assertTrue(e instanceof org.opensearch.client.ResponseException);
            String stackTrace = ExceptionUtils.getStackTrace(e);
            assertTrue(stackTrace.contains("Remote Inference is currently disabled."));
        }
    }
}
