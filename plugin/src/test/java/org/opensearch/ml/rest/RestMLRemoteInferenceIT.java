/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

public class RestMLRemoteInferenceIT extends MLCommonsRestTestCase {

    private final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private final String COHERE_KEY = System.getenv("COHERE_KEY");

    private final String completionModelConnectorEntity = "{\n"
        + "\"name\": \"OpenAI Connector\",\n"
        + "\"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
        + "\"version\": 1,\n"
        + "\"protocol\": \"http\",\n"
        + "\"parameters\": {\n"
        + "    \"endpoint\": \"api.openai.com\",\n"
        + "    \"auth\": \"API_Key\",\n"
        + "    \"content_type\": \"application/json\",\n"
        + "    \"max_tokens\": 7,\n"
        + "    \"temperature\": 0,\n"
        + "    \"model\": \"text-davinci-003\"\n"
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
        Thread.sleep(20000);
    }

    public void testCreateConnector() throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        assertNotNull((String) responseMap.get("connector_id"));
    }

    public void testGetConnector() throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/connectors/" + connectorId, null, "", null);
        responseMap = parseResponseToMap(response);
        assertEquals("OpenAI Connector", (String) responseMap.get("name"));
        assertEquals("1", (String) responseMap.get("version"));
        assertEquals("The connector to public OpenAI model service for GPT 3.5", (String) responseMap.get("description"));
        assertEquals("http", (String) responseMap.get("protocol"));
    }

    public void testDeleteConnector() throws IOException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/connectors/" + connectorId, null, "", null);
        responseMap = parseResponseToMap(response);
        assertEquals("deleted", (String) responseMap.get("result"));
    }

    public void testSearchConnectors() throws IOException {
        createConnector(completionModelConnectorEntity);
        String searchEntity = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  },\n" + "  \"size\": 1000\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/connectors/_search", null, TestHelper.toHttpEntity(searchEntity), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals((Double) 1.0, (Double) ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
    }

    public void testRegisterRemoteModel() throws IOException, InterruptedException {
        Response response = createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 completions", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = getTask(taskId);
        responseMap = parseResponseToMap(response);
        assertNotNull(responseMap.get("model_id"));
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
        assertEquals("COMPLETED", (String) responseMap.get("status"));
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
    }

    public void testPredictRemoteModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
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

    public void testUndeployRemoteModel() throws IOException, InterruptedException {
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
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = undeployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        assertTrue(responseMap.toString().contains("undeployed"));
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
    }

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
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.openai.com\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"model\": \"text-davinci-edit-001\"\n"
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
            + "          \"url\": \"https://api.openai.com/v1/edits\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"input\\\": \\\"${parameters.input}\\\",  \\\"instruction\\\": \\\"${parameters.instruction}\\\"  }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("openAI-GPT-3.5 edit model", connectorId);
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
            + "      \"input\": \"What day of the wek is it?\",\n"
            + "      \"instruction\": \"Fix the spelling mistakes\"\n"
            + "  }\n"
            + "}";
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

    public void testOpenAIModerationsModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"OpenAI moderations model Connector\",\n"
            + "  \"description\": \"The connector to public OpenAI moderations model service\",\n"
            + "  \"version\": 1,\n"
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

    public void testOpenAITextEmbeddingModel_ISO8859_1() throws IOException, InterruptedException {
        testOpenAITextEmbeddingModel("ISO-8859-1", null, (exception) -> {
            assertTrue(exception instanceof org.opensearch.client.ResponseException);
            String stackTrace = ExceptionUtils.getStackTrace(exception);
            assertTrue(stackTrace.contains("'utf-8' codec can't decode byte 0xeb"));
        });
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

    public void testCohereClassifyModel() throws IOException, InterruptedException {
        // Skip test if key is null
        if (COHERE_KEY == null) {
            return;
        }
        String entity = "{\n"
            + "  \"name\": \"Cohere classify model Connector\",\n"
            + "  \"description\": \"The connector to public Cohere classify model service\",\n"
            + "  \"version\": 1,\n"
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
            + "          \"url\": \"https://${parameters.endpoint}/v1/classify\",\n"
            + "          \"headers\": { \n"
            + "          \"Authorization\": \"Bearer ${credential.cohere_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"inputs\\\": ${parameters.inputs}, \\\"examples\\\": ${parameters.examples}, \\\"truncate\\\": \\\"END\\\" }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";
        Response response = createConnector(entity);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("cohere classify model", connectorId);
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
            + "      \"inputs\": [\n"
            + "            \"Confirm your email address\",\n"
            + "            \"hey i need u to send some $\"\n"
            + "        ],\n"
            + "        \"examples\": [\n"
            + "            {\n"
            + "                \"text\": \"Dermatologists don't like her!\",\n"
            + "                \"label\": \"Spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Hello, open to this?\",\n"
            + "                \"label\": \"Spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"I need help please wire me $1000 right now\",\n"
            + "                \"label\": \"Spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Nice to know you ;)\",\n"
            + "                \"label\": \"Spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Please help me?\",\n"
            + "                \"label\": \"Spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Your parcel will be delivered today\",\n"
            + "                \"label\": \"Not spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Review changes to our Terms and Conditions\",\n"
            + "                \"label\": \"Not spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Weekly sync notes\",\n"
            + "                \"label\": \"Not spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Re: Follow up from todays meeting\",\n"
            + "                \"label\": \"Not spam\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"text\": \"Pre-read for tomorrow\",\n"
            + "                \"label\": \"Not spam\"\n"
            + "            }\n"
            + "        ]\n"
            + "  }\n"
            + "}";

        response = predictRemoteModel(modelId, predictInput);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        responseMap = (Map) responseMap.get("dataAsMap");
        responseList = (List) responseMap.get("classifications");
        assertFalse(responseList.isEmpty());
    }

    private Response createConnector(String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, TestHelper.toHttpEntity(input), null);
    }

    private Response registerRemoteModel(String name, String connectorId) throws IOException {
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
            + "\"\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(registerModelEntity), null);
    }

    private Response deployRemoteModel(String modelId) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, "", null);
    }

    private Response predictRemoteModel(String modelId, String input) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_predict", null, input, null);
    }

    private Response undeployRemoteModel(String modelId) throws IOException {
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

    private boolean checkThrottlingOpenAI(Map responseMap) {
        Map map = (Map) responseMap.get("error");
        String message = (String) map.get("message");
        return message.equals("You exceeded your current quota, please check your plan and billing details.");
    }

    private Map parseResponseToMap(Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        return gson.fromJson(entityString, Map.class);
    }

    private void disableClusterConnectorAccessControl() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.connector_access_control_enabled\":false, \"plugins.ml_commons.sync_up_job_interval_in_seconds\":3}}",
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    private Response getTask(String taskId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
    }

}
