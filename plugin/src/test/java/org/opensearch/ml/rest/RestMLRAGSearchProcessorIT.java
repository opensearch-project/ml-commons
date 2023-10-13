/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.rest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.opensearch.ml.utils.TestHelper.makeRequest;
import static org.opensearch.ml.utils.TestHelper.toHttpEntity;

public class RestMLRAGSearchProcessorIT extends RestMLRemoteInferenceIT {

    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private static final String OPENAI_CONNECTOR_BLUEPRINT =
        "{\n"
            + "    \"name\": \"OpenAI Chat Connector\",\n"
            + "    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
            + "    \"version\": 2,\n"
            + "    \"protocol\": \"http\",\n"
            + "    \"parameters\": {\n"
            + "        \"endpoint\": \"api.openai.com\",\n"
            + "        \"model\": \"gpt-3.5-turbo\",\n"
            + "        \"temperature\": 0\n"
            + "    },\n"
            + "    \"credential\": {\n"
            + "        \"openAI_key\": \"" + OPENAI_KEY + "\"\n"
            + "    },\n"
            + "    \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \"predict\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"url\": \"https://${parameters.endpoint}/v1/chat/completions\",\n"
            + "            \"headers\": {\n"
            + "                \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "            },\n"
            + "            \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages}, \\\"temperature\\\": ${parameters.temperature} }\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");

    private static final String BEDROCK_CONNECTOR_BLUEPRINT1 =
              "{\n"
            + "  \"name\": \"Bedrock Connector: claude2\",\n"
            + "  \"description\": \"The connector to bedrock claude2 model\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"parameters\": {\n"
            + "    \"region\": \"us-east-1\",\n"
            + "    \"service_name\": \"bedrock\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"access_key\": \"" + AWS_ACCESS_KEY_ID + "\",\n"
            + "    \"secret_key\": \"" + AWS_SECRET_ACCESS_KEY + "\",\n"
            + "    \"session_token\": \"" + AWS_SESSION_TOKEN + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \"predict\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"headers\": {\n"
            + "                \"content-type\": \"application/json\"\n"
            + "            },\n"
            + "            \"url\": \"https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke\",\n"
            + "            \"request_body\": \"{\\\"prompt\\\":\\\"\\\\n\\\\nHuman: ${parameters.inputs}\\\\n\\\\nAssistant:\\\",\\\"max_tokens_to_sample\\\":300,\\\"temperature\\\":0.5,\\\"top_k\\\":250,\\\"top_p\\\":1,\\\"stop_sequences\\\":[\\\"\\\\\\\\n\\\\\\\\nHuman:\\\"]}\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    private static final String BEDROCK_CONNECTOR_BLUEPRINT2 =
              "{\n"
            + "  \"name\": \"Bedrock Connector: claude2\",\n"
            + "  \"description\": \"The connector to bedrock claude2 model\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"parameters\": {\n"
            + "    \"region\": \"us-east-1\",\n"
            + "    \"service_name\": \"bedrock\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"access_key\": \"" + AWS_ACCESS_KEY_ID + "\",\n"
            + "    \"secret_key\": \"" + AWS_SECRET_ACCESS_KEY + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \"predict\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"headers\": {\n"
            + "                \"content-type\": \"application/json\"\n"
            + "            },\n"
            + "            \"url\": \"https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke\",\n"
            + "            \"request_body\": \"{\\\"prompt\\\":\\\"\\\\n\\\\nHuman: ${parameters.inputs}\\\\n\\\\nAssistant:\\\",\\\"max_tokens_to_sample\\\":300,\\\"temperature\\\":0.5,\\\"top_k\\\":250,\\\"top_p\\\":1,\\\"stop_sequences\\\":[\\\"\\\\\\\\n\\\\\\\\nHuman:\\\"]}\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    private static final String BEDROCK_CONNECTOR_BLUEPRINT = AWS_SESSION_TOKEN == null ? BEDROCK_CONNECTOR_BLUEPRINT2 : BEDROCK_CONNECTOR_BLUEPRINT1;
    private static final String PIPELINE_TEMPLATE =
              "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"retrieval_augmented_generation\": {\n"
            + "        \"tag\": \"%s\",\n"
            + "        \"description\": \"%s\",\n"
            + "        \"model_id\": \"%s\",\n"
            + "        \"system_prompt\": \"%s\",\n"
            + "        \"user_instructions\": \"%s\",\n"
            + "        \"context_field_list\": [\"%s\"]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    private static final String BM25_SEARCH_REQUEST_TEMPLATE =
              "{\n"
            + "  \"_source\": [\"%s\"],\n"
            + "  \"query\" : {\n"
            + "    \"match\": {\"%s\": \"%s\"}\n"
            + "  },\n"
            + "   \"ext\": {\n"
            + "      \"generative_qa_parameters\": {\n"
            + "        \"llm_model\": \"%s\",\n"
            + "        \"llm_question\": \"%s\",\n"
            + "        \"context_size\": %d,\n"
            + "        \"interaction_size\": %d,\n"
            + "        \"timeout\": %d\n"
            + "      }\n"
            + "  }\n"
            + "}";

    private static final String BM25_SEARCH_REQUEST_WITH_CONVO_TEMPLATE =
        "{\n"
            + "  \"_source\": [\"%s\"],\n"
            + "  \"query\" : {\n"
            + "    \"match\": {\"%s\": \"%s\"}\n"
            + "  },\n"
            + "   \"ext\": {\n"
            + "      \"generative_qa_parameters\": {\n"
            + "        \"llm_model\": \"%s\",\n"
            + "        \"llm_question\": \"%s\",\n"
            + "        \"conversation_id\": \"%s\",\n"
            + "        \"context_size\": %d,\n"
            + "        \"interaction_size\": %d,\n"
            + "        \"timeout\": %d\n"
            + "      }\n"
            + "  }\n"
            + "}";

    private static final String OPENAI_MODEL = "gpt-3.5-turbo";
    private static final String BEDROCK_ANTHROPIC_CLAUDE = "bedrock/anthropic-claude";
    private static final String TEST_DOC_PATH = "org/opensearch/ml/rest/test_data/";
    private static Set<String> testDocs = Set.of("qa_doc1.json", "qa_doc2.json", "qa_doc3.json");
    private static final String DEFAULT_USER_AGENT = "Kibana";
    protected ClassLoader classLoader = RestMLRAGSearchProcessorIT.class.getClassLoader();
    private static final String INDEX_NAME = "test";

    // "client" gets initialized by the test framework at the instance level
    // so we perform this per test case, not via @BeforeClass.
    @Before
    public void init() throws Exception {

        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.rag_pipeline_feature_enabled\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.memory_feature_enabled\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\n"
                    + "        \"plugins.ml_commons.trusted_connector_endpoints_regex\": [\n"
                    + "            \"^https://runtime\\\\.sagemaker\\\\..*\\\\.amazonaws\\\\.com/.*$\",\n"
                    + "            \"^https://api\\\\.openai\\\\.com/.*$\",\n"
                    + "            \"^https://api\\\\.cohere\\\\.ai/.*$\",\n"
                    + "            \"^http://127.0.0.1:1323/.*$\",\n"
                    + "            \"^https://bedrock.*\\\\.amazonaws.com/.*$\"\n"
                    + "        ]\n"
                    + "    }}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        for (String doc : testDocs) {
            String requestBody = Files.readString(Path.of(classLoader.getResource(TEST_DOC_PATH + doc).toURI()));
            index(INDEX_NAME, requestBody);
        }
    }

    void index(String indexName, String requestBody) throws Exception {
        makeRequest(
            client(),
            "POST",
            indexName + "/_doc",
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Response statsResponse = makeRequest(client(), "GET", indexName, ImmutableMap.of(), "", null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
        String result = EntityUtils.toString(statsResponse.getEntity());
        assertTrue(result.contains(indexName));
    }

    public void testBM25WithOpenAI() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(OPENAI_CONNECTOR_BLUEPRINT);
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

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAI";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_MODEL;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithBedrock() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        Response response = createConnector(BEDROCK_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("Bedrock Anthropic Claude", connectorId);
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

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAI";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);
    }

    public void testBM25WithOpenAIWithConversation() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        Response response = createConnector(OPENAI_CONNECTOR_BLUEPRINT);
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

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithOpenAI";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        String conversationId = createConversation("test_convo_1");
        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = OPENAI_MODEL;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.conversationId = conversationId;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        String interactionId = (String) rag.get("interaction_id");
        assertNotNull(interactionId);
    }

    public void testBM25WithBedrockWithConversation() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        Response response = createConnector(BEDROCK_CONNECTOR_BLUEPRINT);
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = registerRemoteModel("Bedrock", connectorId);
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

        PipelineParameters pipelineParameters = new PipelineParameters();
        pipelineParameters.tag = "testBM25WithBedrock";
        pipelineParameters.description = "desc";
        pipelineParameters.modelId = modelId;
        pipelineParameters.systemPrompt = "You are a helpful assistant";
        pipelineParameters.userInstructions = "none";
        pipelineParameters.context_field = "text";
        Response response1 = createSearchPipeline("pipeline_test", pipelineParameters);
        assertEquals(200, response1.getStatusLine().getStatusCode());

        String conversationId = createConversation("test_convo_1");
        SearchRequestParameters requestParameters = new SearchRequestParameters();
        requestParameters.source = "text";
        requestParameters.match = "president";
        requestParameters.llmModel = BEDROCK_ANTHROPIC_CLAUDE;
        requestParameters.llmQuestion = "who is lincoln";
        requestParameters.contextSize = 5;
        requestParameters.interactionSize = 5;
        requestParameters.timeout = 60;
        requestParameters.conversationId = conversationId;
        Response response2 = performSearch(INDEX_NAME, "pipeline_test", 5, requestParameters);
        assertEquals(200, response2.getStatusLine().getStatusCode());

        Map responseMap2 = parseResponseToMap(response2);
        Map ext = (Map) responseMap2.get("ext");
        assertNotNull(ext);
        Map rag = (Map) ext.get("retrieval_augmented_generation");
        assertNotNull(rag);

        // TODO handle errors such as throttling
        String answer = (String) rag.get("answer");
        assertNotNull(answer);

        String interactionId = (String) rag.get("interaction_id");
        assertNotNull(interactionId);
    }

    private Response createSearchPipeline(String pipeline, PipelineParameters parameters) throws Exception {
        return makeRequest(
            client(),
            "PUT",
            String.format(Locale.ROOT, "/_search/pipeline/%s", pipeline),
            null,
            toHttpEntity(
                String.format(
                    Locale.ROOT,
                    PIPELINE_TEMPLATE,
                    parameters.tag, parameters.description,
                    parameters.modelId, parameters.systemPrompt, parameters.userInstructions, parameters.context_field
                )
            ),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }

    private Response performSearch(String indexName, String pipeline, int size, SearchRequestParameters requestParameters) throws Exception {

        String httpEntity = (requestParameters.conversationId == null) ?
                            String.format(
                                Locale.ROOT,
                                BM25_SEARCH_REQUEST_TEMPLATE,
                                requestParameters.source, requestParameters.source, requestParameters.match,
                                requestParameters.llmModel, requestParameters.llmQuestion, requestParameters.contextSize,
                                requestParameters.interactionSize, requestParameters.timeout
                            )
                            :
                            String.format(
                                Locale.ROOT,
                                BM25_SEARCH_REQUEST_WITH_CONVO_TEMPLATE,
                                requestParameters.source, requestParameters.source, requestParameters.match,
                                requestParameters.llmModel, requestParameters.llmQuestion, requestParameters.conversationId, requestParameters.contextSize,
                                requestParameters.interactionSize, requestParameters.timeout
                            );
        return makeRequest(
            client(),
            "POST",
            String.format(Locale.ROOT, "/%s/_search", indexName),
            Map.of("search_pipeline", pipeline, "size", String.valueOf(size)),
            toHttpEntity(httpEntity),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }

    private String createConversation(String name) throws Exception {
        Response response = makeRequest(
            client(),
            "POST",
            "/_plugins/_ml/memory/conversation",
            null,
            toHttpEntity(
                String.format(
                    Locale.ROOT,
                    "{\"name\": \"%s\"}",
                    name
                )
            ),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        return (String) ((Map) parseResponseToMap(response)).get("conversation_id");
    }

    static class PipelineParameters {
        String tag;
        String description;
        String modelId;
        String systemPrompt;
        String userInstructions;
        String context_field;
    }

    static class SearchRequestParameters {
        String source;
        String match;
        String llmModel;
        String llmQuestion;
        int contextSize;
        int interactionSize;
        int timeout;
        String conversationId;
    }
}
