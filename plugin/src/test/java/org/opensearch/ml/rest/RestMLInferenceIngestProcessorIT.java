/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

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
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.JsonPath;

public class RestMLInferenceIngestProcessorIT extends MLCommonsRestTestCase {
    private final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private String modelId;
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

    @Before
    public void setup() throws IOException, InterruptedException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);

        // create connectors for OPEN AI and register model
        Response response = RestMLRemoteInferenceIT.createConnector(completionModelConnectorEntity);
        Map responseMap = parseResponseToMap(response);
        String openAIConnectorId = (String) responseMap.get("connector_id");
        response = RestMLRemoteInferenceIT.registerRemoteModel("openAI-GPT-3.5 chat model", openAIConnectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        responseMap = parseResponseToMap(response);
        this.modelId = (String) responseMap.get("model_id");
        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
    }

    public void testMLInferenceProcessorWithObjectFieldType() throws Exception {

        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"test ml model ingest processor\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"model_id\": \""
            + this.modelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"diary\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"data.embedding\": \"diary_embedding\"\n"
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
        uploadDocument(index_name, "1", uploadDocumentRequestBody);
        Map document = getDocument(index_name, "1");
        List embeddingList = JsonPath.parse(document).read("_source.diary_embedding");
        Assert.assertEquals(2, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.diary_embedding[0]");
        Assert.assertEquals(1536, embedding1.size());
        Assert.assertEquals(-0.0118564125, (Double) embedding1.get(0), 0.00005);

        List embedding2 = JsonPath.parse(document).read("_source.diary_embedding[1]");
        Assert.assertEquals(1536, embedding2.size());
        Assert.assertEquals(-0.005518768, (Double) embedding2.get(0), 0.00005);
    }

    public void testMLInferenceProcessorWithNestedFieldType() throws Exception {

        String createPipelineRequestBody = "{\n"
            + "  \"description\": \"ingest reviews and generate embedding\",\n"
            + "  \"processors\": [\n"
            + "    {\n"
            + "      \"ml_inference\": {\n"
            + "        \"model_id\": \""
            + this.modelId
            + "\",\n"
            + "        \"input_map\": [\n"
            + "          {\n"
            + "            \"input\": \"book.chunk.text.context\"\n"
            + "          }\n"
            + "        ],\n"
            + "        \n"
            + "        \"output_map\": [\n"
            + "          {\n"
            + "            \"data.embedding\": \"book.chunk.text.context_embedding\"\n"
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
        uploadDocument(index_name, "1", uploadDocumentRequestBody);
        Map document = getDocument(index_name, "1");

        List embeddingList = JsonPath.parse(document).read("_source.book[*].chunk.text[*].context_embedding");
        Assert.assertEquals(4, embeddingList.size());

        List embedding1 = JsonPath.parse(document).read("_source.book[0].chunk.text[0].context_embedding");
        Assert.assertEquals(1536, embedding1.size());
        Assert.assertEquals(0.023224998, (Double) embedding1.get(0), 0.00005);

        List embedding2 = JsonPath.parse(document).read("_source.book[0].chunk.text[1].context_embedding");
        Assert.assertEquals(1536, embedding2.size());
        Assert.assertEquals(0.016423305, (Double) embedding2.get(0), 0.00005);

        List embedding3 = JsonPath.parse(document).read("_source.book[1].chunk.text[0].context_embedding");
        Assert.assertEquals(1536, embedding3.size());
        Assert.assertEquals(0.011252925, (Double) embedding3.get(0), 0.00005);

        List embedding4 = JsonPath.parse(document).read("_source.book[1].chunk.text[1].context_embedding");
        Assert.assertEquals(1536, embedding4.size());
        Assert.assertEquals(0.014352738, (Double) embedding4.get(0), 0.00005);
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

}
