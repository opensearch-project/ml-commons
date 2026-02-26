/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_URL;
import static org.opensearch.ml.utils.TestHelper.makeRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.JsonPath;

public class RestMLInferenceSearchResponseProcessorIT extends MLCommonsRestTestCase {

    private final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private String openAIChatModelId;
    private String bedrockEmbeddingModelId;
    private String localModelId;
    private String bedrockClaudeModelId;
    private String bedrockMultiModalEmbeddingModelId;
    private final String completionModelConnectorEntity = "{\n"
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
        + "          \"request_body\": \"{ \\\"input\\\": ${parameters.input}, \\\"model\\\": \\\"${parameters.model}\\\" }\"\n"
        + "      }\n"
        + "  ]\n"
        + "}";

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");

    private static final String GITHUB_CI_AWS_REGION = "us-west-2";

    private final String bedrockEmbeddingModelConnectorEntity = "{\n"
        + "  \"name\": \"Amazon Bedrock Connector: embedding\",\n"
        + "  \"description\": \"The connector to bedrock Titan embedding model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"client_config\": {\n"
        + "    \"max_connection\": 200\n"
        + "  },\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model_name\": \"amazon.titan-embed-text-v1\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "    {\n"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model_name}/invoke\",\n"
        + "      \"headers\": {\n"
        + "        \"content-type\": \"application/json\",\n"
        + "        \"x-amz-content-sha256\": \"required\"\n"
        + "      },\n"
        + "      \"request_body\": \"{ \\\"inputText\\\": \\\"${parameters.input}\\\" }\",\n"
        + "      \"pre_process_function\": \"connector.pre_process.bedrock.embedding\",\n"
        + "      \"post_process_function\": \"connector.post_process.bedrock.embedding\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    private final String bedrockClaudeModelConnectorEntity = "{\n"
        + "  \"name\": \"Bedrock Connector: claude 3.5\",\n"
        + "  \"description\": \"The connector to bedrock claude 3.5 model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"client_config\": {\n"
        + "    \"max_connection\": 200\n"
        + "  },\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model\": \""
        + "anthropic.claude-3-5-sonnet-20241022-v2:0"
        + "\",\n"
        + "    \"system_prompt\": \"You are a helpful assistant.\",\n"
        + "\"response_filter\": \"$.output.message.content[0].text\""
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "        {\n"
        + "            \"action_type\": \""
        + "predict"
        + "\",\n"
        + "            \"method\": \"POST\",\n"
        + "            \"headers\": {\n"
        + "                \"content-type\": \"application/json\"\n"
        + "            },\n"
        + "            \"url\": \"https://bedrock-runtime."
        + GITHUB_CI_AWS_REGION
        + ".amazonaws.com/model/"
        + "anthropic.claude-3-5-sonnet-20241022-v2:0"
        + "/converse\",\n"
        + "            \"request_body\": \"{ \\\"system\\\": [{\\\"text\\\": \\\"you are a helpful assistant.\\\"}], \\\"messages\\\":[{\\\"role\\\": \\\"user\\\", \\\"content\\\":[ {\\\"type\\\": \\\"text\\\", \\\"text\\\":\\\"${parameters.prompt}\\\"}]}] , \\\"inferenceConfig\\\": {\\\"temperature\\\": 0.0, \\\"topP\\\": 0.9, \\\"maxTokens\\\": 1000} }\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    private final String bedrockMultiModalEmbeddingModelConnectorEntity = "{\n"
        + "  \"name\": \"Amazon Bedrock Connector: bedrock Titan multi-modal embedding model\",\n"
        + "  \"description\": \"Test connector for Amazon Bedrock Titan multi-modal embedding model\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"aws_sigv4\",\n"
        + "  \"parameters\": {\n"
        + "    \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "    \"service_name\": \"bedrock\",\n"
        + "    \"model\": \"amazon.titan-embed-image-v1\",\n"
        + "    \"input_docs_processed_step_size\": 2\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"access_key\": \""
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "    \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "    \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "    {\n"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke\",\n"
        + "      \"headers\": {\n"
        + "        \"content-type\": \"application/json\"\n"
        + "      },\n"
        + "      \"request_body\": \"{\\\"inputText\\\": \\\"${parameters.inputText:-null}\\\", \\\"inputImage\\\": \\\"${parameters.inputImage:-null}\\\"}\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    /**
     * Registers two remote models and creates an index and documents before running the tests.
     *
     * @throws Exception if any error occurs during the setup
     */
    @Before
    public void setup() throws Exception {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);
        String openAIChatModelName = "openAI-GPT-3.5 chat model " + randomAlphaOfLength(5);
        this.openAIChatModelId = registerRemoteModel(completionModelConnectorEntity, openAIChatModelName, true);
        String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
        this.bedrockEmbeddingModelId = registerRemoteModel(bedrockEmbeddingModelConnectorEntity, bedrockEmbeddingModelName, true);
        String bedrockClaudeModelName = "bedrock claude model " + randomAlphaOfLength(5);
        this.bedrockClaudeModelId = registerRemoteModel(bedrockClaudeModelConnectorEntity, bedrockClaudeModelName, true);
        String bedrockMultiModalEmbeddingModelName = "bedrock multi modal embedding model " + randomAlphaOfLength(5);
        this.bedrockMultiModalEmbeddingModelId = registerRemoteModel(
            bedrockMultiModalEmbeddingModelConnectorEntity,
            bedrockMultiModalEmbeddingModelName,
            true
        );

        String index_name = "daily_index";
        String createIndexRequestBody = "{\n"
            + "  \"mappings\": {\n"
            + "    \"properties\": {\n"
            + "      \"diary_embedding_size\": {\n"
            + "        \"type\": \"keyword\"\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
        String uploadDocumentRequestBodyDoc1 = "{\n"
            + "  \"id\": 1,\n"
            + "  \"diary\": [\"happy\",\"first day at school\"],\n"
            + "  \"diary_embedding_size\": \"1536\",\n" // embedding size for ada model
            + "  \"weather\": \"sunny\"\n"
            + "  }";

        String uploadDocumentRequestBodyDoc2 = "{\n"
            + "  \"id\": 2,\n"
            + "  \"diary\": [\"bored\",\"at home\"],\n"
            + "  \"diary_embedding_size\": \"768\",\n"  // embedding size for local text embedding model
            + "  \"weather\": \"sunny\"\n"
            + "  }";

        createIndex(index_name, createIndexRequestBody);
        uploadDocument(index_name, "1", uploadDocumentRequestBodyDoc1);
        uploadDocument(index_name, "2", uploadDocumentRequestBodyDoc2);
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and an object field as input.
     * It creates a search pipeline with the processor configured to use the remote model,
     * performs a search using the pipeline, gathering search documents into context and added in a custom prompt
     * Using a toString() in placeholder to specify the context needs to cast as string
     * and verifies the inference results.
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelCustomPrompt() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.bedrockClaudeModelId
            + "\",\n"
            + "        \"function_name\": \"REMOTE\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"context\": \"weather\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"llm_response\":\"$.response\"\n"
            + "            \n"
            + "          }\n"
            + "        ],\n"
            + "        \"model_config\": {\n"
            + "          \"prompt\":\"\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context.toString()}. \\n\\n Human: please summarize the documents \\n\\n Assistant:\"\n"
            + "        },\n"
            + "        \"ignore_missing\":false,\n"
            + "        \"ignore_failure\": false\n"
            + "        \n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\"query\":{\"term\":{\"weather\":{\"value\":\"sunny\"}}}}";

        String index_name = "daily_index";
        String pipelineName = "qa_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertNotNull(JsonPath.parse(response).read("$.hits.hits[0]._source.llm_response"));
        Assert.assertNotNull(JsonPath.parse(response).read("$.hits.hits[1]._source.llm_response"));
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and a string field as input.
     * It creates a search pipeline with the processor configured to use the remote model,
     * runs one to one prediction by sending one document to one prediction
     * performs a search using the pipeline, and verifies the inference results.
     *
     * @throws Exception if any error occurs during the test
     */
    @Test
    public void testMLInferenceProcessorRemoteModelStringField() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.bedrockEmbeddingModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"weather\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"weather_embedding\": \"$.embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false,\n"
            + "        \"one_to_one\": true\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\"query\":{\"term\":{\"diary\":{\"value\":\"happy\"}}}}";

        String index_name = "daily_index";
        String pipelineName = "weather_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
        List embeddingList = (List) JsonPath.parse(response).read("$.hits.hits[0]._source.weather_embedding");
        Assert.assertEquals(embeddingList.size(), 1536);
        Assert.assertEquals((Double) embeddingList.get(0), 0.734375, 0.005);
        Assert.assertEquals((Double) embeddingList.get(1), 0.87109375, 0.005);
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and
     * read the model input from a string field in ml inference search extension
     * It creates a search pipeline with the processor configured to use the remote model,
     * runs one to one prediction by sending one document to one prediction
     * performs a search using the pipeline, and verifies the inference results.
     *
     * @throws Exception if any error occurs during the test
     */
    @Test
    public void testMLInferenceProcessorRemoteModelStringFieldWithSearchExtension() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search response\",\n"
            + "        \"model_id\": \""
            + this.bedrockEmbeddingModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"$._request.ext.ml_inference.query_text\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"weather_embedding\": \"$.embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false,\n"
            + "        \"one_to_one\": true\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\n"
            + "  \"query\": {\n"
            + "    \"term\": {\n"
            + "      \"diary\": {\n"
            + "        \"value\": \"happy\"\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"ext\": {\n"
            + "    \"ml_inference\": {\n"
            + "      \"query_text\": \"sunny\"\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String index_name = "daily_index";
        String pipelineName = "weather_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
        List embeddingList = (List) JsonPath.parse(response).read("$.hits.hits[0]._source.weather_embedding");
        Assert.assertEquals(embeddingList.size(), 1536);
        Assert.assertEquals((Double) embeddingList.get(0), 0.734375, 0.005);
        Assert.assertEquals((Double) embeddingList.get(1), 0.87109375, 0.005);
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and
     * read the optional model input from string field from search hits
     * It creates a search pipeline with the processor configured to use the remote model,
     * runs one to one prediction by sending one document to one prediction
     * performs a search using the pipeline, and verifies the inference results.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelOptionalInputs() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is to run knn query when query on both text and image\",\n"
            + "        \"model_id\": \""
            + this.bedrockMultiModalEmbeddingModelId
            + "\",\n"
            + "        \"optional_input_map\": [\n"
            + "          {\n"
            + "            \"inputText\": \"diary[0]\",\n"
            + "            \"inputImage\": \"diary_image\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"multi_modal_embedding\": \"embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"model_config\": {},\n"
            + "        \"one_to_one\": true,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String index_name = "daily_index";

        String pipelineName = "diary_multimodal_embedding_pipeline";
        String query = "{\"query\":{\"term\":{\"diary\":{\"value\":\"happy\"}}}}";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);

        assertEquals((int) JsonPath.parse(response).read("$.hits.hits.length()"), 1);
        assertEquals((int) JsonPath.parse(response).read("$.hits.hits[0]._source.multi_modal_embedding.length()"), 1024);
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and a nested list field as input.
     * It creates a search pipeline with the processor configured to use the remote model,
     * performs a search using the pipeline, and verifies the inference results.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelNestedListField() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"diary[0]\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"dairy_embedding\": \"data[*].embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\"query\":{\"term\":{\"weather\":{\"value\":\"sunny\"}}}}";

        String index_name = "daily_index";
        String pipelineName = "diary_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
        List embeddingList = (List) JsonPath.parse(response).read("$.hits.hits[0]._source.dairy_embedding");
        Assert.assertEquals(embeddingList.size(), 1536);
        Assert.assertEquals((Double) embeddingList.get(0), -0.011842756, 0.005);
        Assert.assertEquals((Double) embeddingList.get(1), -0.012508746, 0.005);
    }

    /**
     * Tests the MLInferenceSearchResponseProcessor with a remote model and an object field as input.
     * It creates a search pipeline with the processor configured to use the remote model,
     * performs a search using the pipeline, and verifies the inference results.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelObjectField() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"weather\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"weather_embedding\": \"data[*].embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\"query\":{\"term\":{\"weather\":{\"value\":\"sunny\"}}}}";

        String index_name = "daily_index";
        String pipelineName = "weather_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
        List embeddingList = (List) JsonPath.parse(response).read("$.hits.hits[0]._source.weather_embedding");
        Assert.assertEquals(embeddingList.size(), 1536);
        Assert.assertEquals((Double) embeddingList.get(0), 0.00020525085, 0.005);
        Assert.assertEquals((Double) embeddingList.get(1), -0.0071890163, 0.005);
    }

    /**
     * Tests the ML inference processor with a local model.
     * It registers, deploys, and gets a local model, creates a search pipeline with the ML inference processor
     * configured to use the local model, and then performs a search using the pipeline.
     * The test verifies that the query string is rewritten correctly based on the inference results
     * from the local model.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorLocalModel() throws Exception {

        String taskId = registerModel(TestHelper.toJsonString(registerModelInput()));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            assertNotNull(response.get(MODEL_ID_FIELD));
            this.localModelId = (String) response.get(MODEL_ID_FIELD);
            try {
                String deployTaskID = deployModel(this.localModelId);
                waitForTask(deployTaskID, MLTaskState.COMPLETED);

                getModel(client(), this.localModelId, model -> { assertEquals("DEPLOYED", model.get("model_state")); });
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        String createPipelineRequestBody = "{\n"
            + "  \"response_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.localModelId
            + "\",\n"
            + "        \"model_input\": \"{ \\\"text_docs\\\": [\\\"${ml_inference.text_docs}\\\"] ,\\\"return_number\\\": true,\\\"target_response\\\": [\\\"sentence_embedding\\\"]}\",\n"
            + "        \"function_name\": \"text_embedding\",\n"
            + "        \"full_response_path\": true,\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"weather\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"weather_embedding\": \"$.inference_results[0].output[0].data\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String index_name = "daily_index";
        String pipelineName = "weather_embedding_pipeline_local";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        String query = "{\"query\":{\"term\":{\"diary_embedding_size\":{\"value\":\"768\"}}}}";
        Map response = searchWithPipeline(client(), index_name, pipelineName, query);
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "768");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "bored");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "at home");

        List embeddingList = (List) JsonPath.parse(response).read("$.hits.hits[0]._source.weather_embedding");
        Assert.assertEquals(embeddingList.size(), 768);
        Assert.assertEquals((Double) embeddingList.get(0), 0.54809606, 0.005);
        Assert.assertEquals((Double) embeddingList.get(1), 0.46797526, 0.005);

    }

    /**
     * Creates a search pipeline processor with the given request body and pipeline name.
     *
     * @param requestBody the request body for creating the search pipeline processor
     * @param pipelineName the name of the search pipeline
     * @throws Exception if any error occurs during the creation of the search pipeline processor
     */
    protected void createSearchPipelineProcessor(String requestBody, final String pipelineName) throws Exception {
        Response pipelineCreateResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_search/pipeline/" + pipelineName,
                null,
                requestBody,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, pipelineCreateResponse.getStatusLine().getStatusCode());

    }

    /**
     * Creates an index with the given name and request body.
     *
     * @param indexName the name of the index
     * @param requestBody the request body for creating the index
     * @throws Exception if any error occurs during the creation of the index
     */
    protected void createIndex(String indexName, String requestBody) throws Exception {
        Response response = makeRequest(
            client(),
            "PUT",
            indexName,
            null,
            requestBody,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
        );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    /**
     * Uploads a document to the specified index with the given document ID and JSON body.
     *
     * @param index the name of the index
     * @param docId the document ID
     * @param jsonBody the JSON body of the document
     * @throws IOException if an I/O error occurs during the upload
     */
    protected void uploadDocument(final String index, final String docId, final String jsonBody) throws IOException {
        Request request = new Request("PUT", "/" + index + "/_doc/" + docId + "?refresh=true");
        request.setJsonEntity(jsonBody);
        client().performRequest(request);
    }

    /**
     * Creates a MLRegisterModelInput instance with the specified configuration.
     *
     * @return the MLRegisterModelInput instance
     * @throws IOException if an I/O error occurs during the creation of the input
     * @throws InterruptedException if the thread is interrupted during the creation of the input
     */
    protected MLRegisterModelInput registerModelInput() throws IOException, InterruptedException {

        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(768)
            .build();
        return MLRegisterModelInput
            .builder()
            .modelName("test_model_name")
            .version("1.0.0")
            .functionName(FunctionName.TEXT_EMBEDDING)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .url(SENTENCE_TRANSFORMER_MODEL_URL)
            .deployModel(false)
            .hashValue("e13b74006290a9d0f58c1376f9629d4ebc05a0f9385f40db837452b167ae9021")
            .build();
    }

}
