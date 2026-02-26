/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_HASH_VALUE;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_URL;
import static org.opensearch.ml.utils.TestHelper.makeRequest;

import java.io.IOException;
import java.util.List;
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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class RestMLInferenceIngestProcessorIT extends MLCommonsRestTestCase {
    private final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private String openAIChatModelId;
    private String bedrockEmbeddingModelId;

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

    @Before
    public void setup() throws IOException, InterruptedException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);
        String openAIChatModelName = "openAI-GPT-3.5 chat model " + randomAlphaOfLength(5);
        this.openAIChatModelId = registerRemoteModel(completionModelConnectorEntity, openAIChatModelName, true);
        String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
        this.bedrockEmbeddingModelId = registerRemoteModel(bedrockEmbeddingModelConnectorEntity, bedrockEmbeddingModelName, true);
    }

    public void testMLInferenceProcessorWithObjectFieldType() throws Exception {

        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"test ml model ingest processor\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"diary\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"diary_embedding\": \"data.*.embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"model_config\": {\"model\":\"text-embedding-ada-002\"}\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String createIndexRequestBody = "{\n"
            + "  \"settings\": {\n"
            + "    \"index\": {\n"
            + "      \"default_pipeline\": \"diary_embedding_pipeline\"\n"
            + "    }\n"
            + "  }\n"
            + " }";
        String uploadDocumentRequestBody = "{\n"
            + "  \"id\": 1,\n"
            + "  \"diary\": [\"happy\",\"first day at school\"],\n"
            + "  \"weather\": \"rainy\"\n"
            + "  }";
        String index_name = "daily_index";
        createPipelineProcessor(createPipelineRequestBody, "diary_embedding_pipeline");
        createIndex(index_name, createIndexRequestBody);
        // Skip test if key is null
        if (OPENAI_KEY == null || !isServiceReachable("api.openai.com")) {
            return;
        }
        uploadDocument(index_name, "1", uploadDocumentRequestBody);
        Map document = getDocument(index_name, "1");
        List embeddingList = JsonPath.parse(document).read("_source.diary_embedding");
        Assert.assertEquals(2, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.diary_embedding[0]");
        Assert.assertEquals(1536, embedding1.size());
        Assert.assertEquals(-0.0118564125, (Double) embedding1.get(0), 0.005);

        List embedding2 = JsonPath.parse(document).read("_source.diary_embedding[1]");
        Assert.assertEquals(1536, embedding2.size());
        Assert.assertEquals(-0.005518768, (Double) embedding2.get(0), 0.005);
    }

    public void testMLInferenceProcessorWithNestedFieldType() throws Exception {

        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"ingest reviews and generate embedding\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"model_id\": \""
            + this.openAIChatModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"book.*.chunk.text.*.context\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"book.*.chunk.text.*.context_embedding\": \"data.*.embedding\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"model_config\": {\"model\":\"text-embedding-ada-002\"}\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String createIndexRequestBody = "{\n"
            + "  \"settings\": {\n"
            + "    \"index\": {\n"
            + "      \"default_pipeline\": \"embedding_pipeline\"\n"
            + "    }\n"
            + "  }\n"
            + " }";
        String uploadDocumentRequestBody = "{\n"
            + "  \"book\": [\n"
            + "    {\n"
            + "      \"chunk\": {\n"
            + "        \"text\": [\n"
            + "          {\n"
            + "            \"chapter\": \"first chapter\",\n"
            + "            \"context\": \"this is the first part\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"chapter\": \"first chapter\",\n"
            + "            \"context\": \"this is the second part\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"chunk\": {\n"
            + "        \"text\": [\n"
            + "          {\n"
            + "            \"chapter\": \"second chapter\",\n"
            + "            \"context\": \"this is the third part\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"chapter\": \"second chapter\",\n"
            + "            \"context\": \"this is the fourth part\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String index_name = "book_index";
        createPipelineProcessor(createPipelineRequestBody, "embedding_pipeline");
        createIndex(index_name, createIndexRequestBody);
        if (OPENAI_KEY == null || !isServiceReachable("api.openai.com")) {
            return;
        }
        uploadDocument(index_name, "1", uploadDocumentRequestBody);

        Map document = getDocument(index_name, "1");

        List embeddingList = JsonPath.parse(document).read("_source.book[*].chunk.text[*].context_embedding");
        Assert.assertEquals(4, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.book[0].chunk.text[0].context_embedding");
        Assert.assertEquals(1536, embedding1.size());
        Assert.assertEquals(0.023224998, (Double) embedding1.get(0), 0.005);

        List embedding2 = JsonPath.parse(document).read("_source.book[0].chunk.text[1].context_embedding");
        Assert.assertEquals(1536, embedding2.size());
        Assert.assertEquals(0.016423305, (Double) embedding2.get(0), 0.005);

        List embedding3 = JsonPath.parse(document).read("_source.book[1].chunk.text[0].context_embedding");
        Assert.assertEquals(1536, embedding3.size());
        Assert.assertEquals(0.011252925, (Double) embedding3.get(0), 0.005);

        List embedding4 = JsonPath.parse(document).read("_source.book[1].chunk.text[1].context_embedding");
        Assert.assertEquals(1536, embedding4.size());
        Assert.assertEquals(0.014352738, (Double) embedding4.get(0), 0.005);
    }

    public void testMLInferenceProcessorWithForEachProcessor() throws Exception {
        String indexName = "my_books";
        String pipelineName = "my_books_bedrock_embedding_pipeline";
        String createIndexRequestBody = "{\n"
            + "  \"settings\": {\n"
            + "    \"index\": {\n"
            + "      \"default_pipeline\": \""
            + pipelineName
            + "\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"mappings\": {\n"
            + "    \"properties\": {\n"
            + "      \"books\": {\n"
            + "        \"type\": \"nested\",\n"
            + "        \"properties\": {\n"
            + "          \"title_embedding\": {\n"
            + "            \"type\": \"float\"\n"
            + "          },\n"
            + "          \"title\": {\n"
            + "            \"type\": \"text\"\n"
            + "          },\n"
            + "          \"description\": {\n"
            + "            \"type\": \"text\"\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
        createIndex(indexName, createIndexRequestBody);

        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"Test bedrock embeddings\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"foreach\": {\n"
            + "        \"field\": \"books\",\n"
            + "        \"processor\": {\n"
            + "          \"ml_inference\": {\n"
            + "            \"model_id\": \""
            + this.bedrockEmbeddingModelId
            + "\",\n"
            + "            \"input_map\": [\n"
            + "              {\n"
            + "                \"input\": \"_ingest._value.title\"\n"
            + "              }\n"
            + "            ],\n"
            + "            \"output_map\": [\n"
            + "              {\n"
            + "                \"_ingest._value.title_embedding\": \"$.embedding\"\n"
            + "              }\n"
            + "            ],\n"
            + "            \"ignore_missing\": false,\n"
            + "            \"ignore_failure\": false\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        createPipelineProcessor(createPipelineRequestBody, pipelineName);

        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String uploadDocumentRequestBody = "{\n"
            + "    \"books\": [{\n"
            + "            \"title\": \"first book\",\n"
            + "            \"description\": \"This is first book\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"title\": \"second book\",\n"
            + "            \"description\": \"This is second book\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
        uploadDocument(indexName, "1", uploadDocumentRequestBody);
        Map document = getDocument(indexName, "1");

        List embeddingList = JsonPath.parse(document).read("_source.books[*].title_embedding");
        Assert.assertEquals(2, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.books[0].title_embedding");
        Assert.assertEquals(1536, embedding1.size());
        List embedding2 = JsonPath.parse(document).read("_source.books[1].title_embedding");
        Assert.assertEquals(1536, embedding2.size());
    }

    public void testMLInferenceProcessorLocalModelObjectField() throws Exception {

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
            + "  \"description\": \"test ml model ingest processor\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"function_name\": \"text_embedding\",\n"
            + "        \"full_response_path\": true,\n"
            + "        \"model_id\": \""
            + this.localModelId
            + "\",\n"
            + "        \"model_input\": \"{ \\\"text_docs\\\": ${ml_inference.text_docs} }\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"text_docs\": \"diary\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"diary_embedding\": \"$.inference_results.*.output.*.data\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": false,\n"
            + "        \"ignore_failure\": false\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String createIndexRequestBody = "{\n"
            + "  \"settings\": {\n"
            + "    \"index\": {\n"
            + "      \"default_pipeline\": \"diary_embedding_pipeline\"\n"
            + "    }\n"
            + "  }\n"
            + " }";
        String uploadDocumentRequestBody = "{\n"
            + "  \"id\": 1,\n"
            + "  \"diary\": [\"happy\",\"first day at school\"],\n"
            + "  \"weather\": \"rainy\"\n"
            + "  }";
        String index_name = "daily_index";
        createPipelineProcessor(createPipelineRequestBody, "diary_embedding_pipeline");
        createIndex(index_name, createIndexRequestBody);

        uploadDocument(index_name, "1", uploadDocumentRequestBody);
        Map document = getDocument(index_name, "1");
        List embeddingList = JsonPath.parse(document).read("_source.diary_embedding");
        Assert.assertEquals(2, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.diary_embedding[0]");
        Assert.assertEquals(768, embedding1.size());
        Assert.assertEquals(0.42101282, (Double) embedding1.get(0), 0.005);

        List embedding2 = JsonPath.parse(document).read("_source.diary_embedding[1]");
        Assert.assertEquals(768, embedding2.size());
        Assert.assertEquals(0.49191704, (Double) embedding2.get(0), 0.005);
    }

    public void testMLInferenceIngestProcessor_simulatesIngestPipelineSuccessfully_withAsymmetricEmbeddingModelUsingPassageContentType()
        throws Exception {
        String taskId = registerModel(TestHelper.toJsonString(registerAsymmetricEmbeddingModelInput()));
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

        String asymmetricPipelineName = "asymmetric_embedding_pipeline";
        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"ingest PASSAGE text and generate a embedding using an asymmetric model\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "\n"
            + "        \"model_input\": \"{\\\"text_docs\\\":[\\\"${input_map.text_docs}\\\"],\\\"target_response\\\":[\\\"sentence_embedding\\\"],\\\"parameters\\\":{\\\"content_type\\\":\\\"passage\\\"}}\",\n"
            + "        \"function_name\": \"text_embedding\",\n"
            + "        \"model_id\": \""
            + this.localModelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"text_docs\": \"description\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "\n"
            + "            "
            + "            \"fact_embedding\": \"$.inference_results[0].output[0].data\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        createPipelineProcessor(createPipelineRequestBody, asymmetricPipelineName);
        String sampleDocuments = "{\n"
            + "  \"docs\": [\n"
            + "    {\n"
            + "      \"_index\": \"my-index\",\n"
            + "      \"_id\": \"1\",\n"
            + "      \"_source\": {\n"
            + "        \"title\": \"Central Park\",\n"
            + "        \"description\": \"A large public park in the heart of New York City, offering a wide range of recreational activities.\"\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"_index\": \"my-index\",\n"
            + "      \"_id\": \"2\",\n"
            + "      \"_source\": {\n"
            + "        \"title\": \"Empire State Building\",\n"
            + "        \"description\": \"An iconic skyscraper in New York City offering breathtaking views from its observation deck.\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";

        Map simulateResponseDocuments = simulateIngestPipeline(asymmetricPipelineName, sampleDocuments);

        DocumentContext documents = JsonPath.parse(simulateResponseDocuments);

        List centralParkFactEmbedding = documents.read("docs.[0].*._source.fact_embedding.*");
        assertEquals(768, centralParkFactEmbedding.size());
        Assert.assertEquals(0.5137818, (Double) centralParkFactEmbedding.get(0), 0.005);

        List empireStateBuildingFactEmbedding = documents.read("docs.[1].*._source.fact_embedding.*");
        assertEquals(768, empireStateBuildingFactEmbedding.size());
        Assert.assertEquals(0.4493039, (Double) empireStateBuildingFactEmbedding.get(0), 0.005);
    }

    private MLRegisterModelInput registerAsymmetricEmbeddingModelInput() {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(768)
            .queryPrefix("query >>")
            .passagePrefix("passage >> ")
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
            .hashValue(SENTENCE_TRANSFORMER_MODEL_HASH_VALUE)
            .build();
    }

    // TODO: add tests for other local model types such as sparse/cross encoders
    public void testMLInferenceProcessorLocalModelNestedField() throws Exception {

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
            + "  \"description\": \"ingest reviews and generate embedding\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"function_name\": \"text_embedding\",\n"
            + "        \"full_response_path\": true,\n"
            + "        \"model_id\": \""
            + this.localModelId
            + "\",\n"
            + "        \"model_input\": \"{ \\\"text_docs\\\": ${ml_inference.text_docs} }\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"text_docs\": \"book.*.chunk.text.*.context\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"book.*.chunk.text.*.context_embedding\": \"$.inference_results.*.output.*.data\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \"ignore_missing\": true,\n"
            + "        \"ignore_failure\": true\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        String createIndexRequestBody = "{\n"
            + "  \"settings\": {\n"
            + "    \"index\": {\n"
            + "      \"default_pipeline\": \"embedding_pipeline\"\n"
            + "    }\n"
            + "  }\n"
            + " }";
        String uploadDocumentRequestBody = "{\n"
            + "  \"book\": [\n"
            + "    {\n"
            + "      \"chunk\": {\n"
            + "        \"text\": [\n"
            + "          {\n"
            + "            \"chapter\": \"first chapter\",\n"
            + "            \"context\": \"this is the first part\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"chapter\": \"first chapter\",\n"
            + "            \"context\": \"this is the second part\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"chunk\": {\n"
            + "        \"text\": [\n"
            + "          {\n"
            + "            \"chapter\": \"second chapter\",\n"
            + "            \"context\": \"this is the third part\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"chapter\": \"second chapter\",\n"
            + "            \"context\": \"this is the fourth part\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String index_name = "book_index";
        createPipelineProcessor(createPipelineRequestBody, "embedding_pipeline");
        createIndex(index_name, createIndexRequestBody);

        uploadDocument(index_name, "1", uploadDocumentRequestBody);
        Map document = getDocument(index_name, "1");

        List embeddingList = JsonPath.parse(document).read("_source.book[*].chunk.text[*].context_embedding");
        Assert.assertEquals(4, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.book[0].chunk.text[0].context_embedding");
        Assert.assertEquals(768, embedding1.size());
        Assert.assertEquals(0.48988956, (Double) embedding1.get(0), 0.005);

        List embedding2 = JsonPath.parse(document).read("_source.book[0].chunk.text[1].context_embedding");
        Assert.assertEquals(768, embedding2.size());
        Assert.assertEquals(0.49552172, (Double) embedding2.get(0), 0.005);

        List embedding3 = JsonPath.parse(document).read("_source.book[1].chunk.text[0].context_embedding");
        Assert.assertEquals(768, embedding3.size());
        Assert.assertEquals(0.5004309, (Double) embedding3.get(0), 0.005);

        List embedding4 = JsonPath.parse(document).read("_source.book[1].chunk.text[1].context_embedding");
        Assert.assertEquals(768, embedding4.size());
        Assert.assertEquals(0.47907734, (Double) embedding4.get(0), 0.005);
    }

    protected void createPipelineProcessor(String requestBody, final String pipelineName) throws Exception {
        Response pipelineCreateResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_ingest/pipeline/" + pipelineName,
                null,
                requestBody,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, pipelineCreateResponse.getStatusLine().getStatusCode());

    }

    protected Map simulateIngestPipeline(String pipelineName, String sampleDocuments) throws IOException {
        Response ingestionResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_ingest/pipeline/" + pipelineName + "/_simulate",
                null,
                sampleDocuments,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, ingestionResponse.getStatusLine().getStatusCode());

        return parseResponseToMap(ingestionResponse);
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

    protected Map getDocument(final String index, final String docId) throws Exception {
        Response docResponse = TestHelper.makeRequest(client(), "GET", "/" + index + "/_doc/" + docId + "?refresh=true", null, "", null);
        assertEquals(200, docResponse.getStatusLine().getStatusCode());

        return parseResponseToMap(docResponse);
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
            .hashValue(SENTENCE_TRANSFORMER_MODEL_HASH_VALUE)
            .build();
    }

}
