/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_URL;
import static org.opensearch.ml.utils.TestHelper.makeRequest;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
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

/**
 * test ml inference search request processor to rewrite query with inference results
 */
public class RestMLInferenceSearchRequestProcessorIT extends MLCommonsRestTestCase {
    private final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private String openAIChatModelId;
    private String bedrockEmbeddingModelId;
    private String bedrockMultiModalEmbeddingModelId;
    private String localModelId;
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
        + "\"client_config\": {\n"
        + "    \"read_timeout\": 60000\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "      {\n"
        + "      \"action_type\": \"predict\",\n"
        + "          \"method\": \"POST\",\n"
        + "          \"url\": \"https://api.openai.com/v1/embeddings\",\n"
        + "          \"headers\": { \n"
        + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "          },\n"
        + "          \"request_body\": \"{ \\\"input\\\": [\\\"${parameters.input}\\\"], \\\"model\\\": \\\"${parameters.model}\\\" }\"\n"
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
     * register two remote models and create an index and document before tests
     * @throws Exception
     */
    @Before
    public void setup() throws Exception {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);
        String openAIChatModelName = "openAI-GPT-3.5 chat model " + randomAlphaOfLength(5);
        this.openAIChatModelId = registerRemoteModel(completionModelConnectorEntity, openAIChatModelName, true);
        String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
        this.bedrockEmbeddingModelId = registerRemoteModel(bedrockEmbeddingModelConnectorEntity, bedrockEmbeddingModelName, true);
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
            + "      },\n"
            + "      \"diary_embedding_size_int\": {\n"
            + "        \"type\": \"integer\"\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String uploadDocumentRequestBodyDoc1 = "{\n"
            + "  \"id\": 1,\n"
            + "  \"diary\": [\"happy\",\"first day at school\"],\n"
            + "  \"diary_embedding_size\": \"1536\",\n" // embedding size for ada model
            + "  \"diary_embedding_size_int\": 1536,\n"
            + "  \"weather\": \"rainy\"\n"
            + "  }";

        String uploadDocumentRequestBodyDoc2 = "{\n"
            + "  \"id\": 2,\n"
            + "  \"diary\": [\"bored\",\"at home\"],\n"
            + "  \"diary_embedding_size\": \"768\",\n"  // embedding size for local text embedding model
            + "  \"diary_embedding_size_int\": 768,\n"
            + "  \"weather\": \"sunny\"\n"
            + "  }";

        createIndex(index_name, createIndexRequestBody);
        uploadDocument(index_name, "1", uploadDocumentRequestBodyDoc1);
        uploadDocument(index_name, "2", uploadDocumentRequestBodyDoc2);
    }

    /**
     * Tests the ML inference processor with a remote model to rewrite the query string.
     * It creates a search pipeline with the ML inference processor,
     * and then performs a search using the pipeline. The test verifies that the query string is rewritten
     * correctly based on the inference results from the remote model.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelRewriteQueryString() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"request_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"query.term.diary_embedding_size.value\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"query.term.diary_embedding_size.value\": \"data[0].embedding.length()\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\":false,\n"
            + "        \"ignore_failure\": false\n"
            + "        \n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\"query\":{\"term\":{\"diary_embedding_size\":{\"value\":\"happy\"}}}}";

        String index_name = "daily_index";
        String pipelineName = "diary_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);

        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "rainy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
    }

    /**
     * Tests the ML inference processor with a remote model to rewrite the query string.
     * It creates a search pipeline with the ML inference processor,
     * the ml inference processor takes model input from search extension
     * and then performs a search using the pipeline. The test verifies that the query string is rewritten
     * correctly based on the inference results from the remote model.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelRewriteQueryStringWithSearchExtension() throws Exception {
        // Skip test if key is null
        if (OPENAI_KEY == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"request_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"ext.ml_inference.query_text\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"query.term.diary_embedding_size.value\": \"data[0].embedding.length()\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\":false,\n"
            + "        \"ignore_failure\": false\n"
            + "        \n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String query = "{\n"
            + "  \"query\": {\n"
            + "    \"term\": {\n"
            + "      \"diary_embedding_size\": {\n"
            + "        \"value\": \"any\"\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"ext\": {\n"
            + "    \"ml_inference\": {\n"
            + "      \"query_text\": \"foo\"\n"
            + "    }\n"
            + "  }\n"
            + "}";
        String index_name = "daily_index";
        String pipelineName = "diary_embedding_pipeline";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);

        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "1536");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "rainy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "happy");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "first day at school");
    }

    /**
     * Tests the ML inference processor with a remote model to rewrite the query type.
     * It creates a search pipeline with the ML inference processor configured to rewrite
     * a term query to a range query based on the inference results from the remote model.
     * The test then performs a search using the pipeline and verifies that the query type
     * is rewritten correctly.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelRewriteQueryType() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"request_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + "            \"model_id\": \""
            + this.bedrockEmbeddingModelId
            + "\",\n"
            + "        \"query_template\": \"{\\\"query\\\":{\\\"range\\\":{\\\"diary_embedding_size\\\":{\\\"lte\\\":${modelPrediction}}}}}\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"query.term.diary_embedding_size.value\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"modelPrediction\": \"embedding.length()\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String index_name = "daily_index";

        String pipelineName = "diary_embedding_pipeline_range_query";
        String query = "{\"query\":{\"term\":{\"diary_embedding_size\":{\"value\":\"happy\"}}}}";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        Map response = searchWithPipeline(client(), index_name, pipelineName, query);

        assertEquals((int) JsonPath.parse(response).read("$.hits.hits.length()"), 2);
    }

    /**
     * Tests the ML inference processor with a remote model with optional model input to rewrite the query type.
     * It creates a search pipeline with the ML inference processor configured to rewrite
     * a term query to a range query based on the inference results from the remote model.
     * The test then performs a search using the pipeline and verifies this multi-modal inference produce vector in size
     * of 1024, and there are one hit that has embedding-size more than 1024.
     * is rewritten correctly.
     *
     * @throws Exception if any error occurs during the test
     */
    public void testMLInferenceProcessorRemoteModelOptionalInputs() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String createPipelineRequestBody = "{\n"
            + "  \"request_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is to run knn query when query on both text and image\",\n"
            + "        \"model_id\": \""
            + this.bedrockMultiModalEmbeddingModelId
            + "\",\n"
            + "        \"query_template\": \"{\\\"size\\\": 2,\\\"query\\\": {\\\"range\\\": {\\\"diary_embedding_size_int\\\": {\\\"gte\\\": ${modelPrediction}}}}}\",\n"
            + "        \"optional_input_map\": [\n"
            + "          {\n"
            + "            \"inputText\": \"query.term.diary.value\",\n"
            + "            \"inputImage\": \"query.term.diary_image.value\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"modelPrediction\": \"embedding.length()\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"model_config\": {},\n"
            + "        \"ignore_missing\": false,\n"
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
        assertEquals((double) JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size_int"), 1536.0, 0.0001);
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
            + "  \"request_processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"tag\": \"ml_inference\",\n"
            + "        \"description\": \"This processor is going to run ml inference during search request\",\n"
            + " \"model_id\": \""
            + this.localModelId
            + "\",\n"
            + "        \"model_input\": \"{ \\\"text_docs\\\": [\\\"${ml_inference.text_docs}\\\"] ,\\\"return_number\\\": true,\\\"target_response\\\": [\\\"sentence_embedding\\\"]}\",\n"
            + "        \"function_name\": \"text_embedding\",\n"
            + "        \"full_response_path\": true,\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"text_docs\": \"query.term.diary_embedding_size.value\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"query.term.diary_embedding_size.value\": \"$.inference_results[0].output[0].data.length()\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \n"
            + "        \"ignore_missing\":false,\n"
            + "        \"ignore_failure\": false\n"
            + "        \n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String index_name = "daily_index";
        String pipelineName = "diary_embedding_pipeline_local";
        createSearchPipelineProcessor(createPipelineRequestBody, pipelineName);

        String query = "{\"query\":{\"term\":{\"diary_embedding_size\":{\"value\":\"bored\"}}}}";
        Map response = searchWithPipeline(client(), index_name, pipelineName, query);

        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary_embedding_size"), "768");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.weather"), "sunny");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[0]"), "bored");
        Assert.assertEquals(JsonPath.parse(response).read("$.hits.hits[0]._source.diary[1]"), "at home");
    }

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

    protected void uploadDocument(final String index, final String docId, final String jsonBody) throws IOException {
        Request request = new Request("PUT", "/" + index + "/_doc/" + docId + "?refresh=true");
        request.setJsonEntity(jsonBody);
        client().performRequest(request);
    }

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
