/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.JsonPath;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.utils.TestHelper;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.utils.TestHelper.makeRequest;

public class RestBedRockInferenceIT extends MLCommonsRestTestCase {
    private String bedrockEmbeddingModelId;
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
        String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
        this.bedrockEmbeddingModelId = registerRemoteModel(bedrockEmbeddingModelConnectorEntity, bedrockEmbeddingModelName, true);
    }


    public void test_bedrock_embedding_model() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", "world")).build();
        MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
        ModelTensorOutput output = predictRemoteModel(bedrockEmbeddingModelId, mlInput);
        assertEquals(2, output.getMlModelOutputs().size());
        assertEquals(1536, output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getData().length);
    }
}
