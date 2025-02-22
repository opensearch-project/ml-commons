/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestBedRockV2PostProcessFunctionInferenceIT extends MLCommonsRestTestCase {
    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String GITHUB_CI_AWS_REGION = "us-west-2";
    private static final List<String> POST_PROCESS_FUNCTIONS = List
        .of("connector.post_process.bedrock_v2.embedding.float", "connector.post_process.bedrock_v2.embedding.binary");
    private static final Map<String, String> DATA_TYPE = Map
        .of("connector.post_process.bedrock_v2.embedding.float", "FLOAT32", "connector.post_process.bedrock_v2.embedding.binary", "BINARY");

    @SneakyThrows
    @Before
    public void setup() throws IOException, InterruptedException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);
    }

    public void test_bedrock_embedding_model() throws Exception {
        // Skip test if key is null
        if (tokenNotSet()) {
            return;
        }
        String templates = Files
            .readString(
                Path
                    .of(
                        RestMLPredictionAction.class
                            .getClassLoader()
                            .getResource("org/opensearch/ml/rest/templates/BedRockV2ConnectorBodies.json")
                            .toURI()
                    )
            );
        for (String postProcessFunction : POST_PROCESS_FUNCTIONS) {
            String bedrockEmbeddingModelName = "bedrock embedding model: " + postProcessFunction;
            String modelId = registerRemoteModel(
                String
                    .format(
                        templates,
                        GITHUB_CI_AWS_REGION,
                        AWS_ACCESS_KEY_ID,
                        AWS_SECRET_ACCESS_KEY,
                        AWS_SESSION_TOKEN,
                        StringUtils.substringAfterLast(postProcessFunction, "."),
                        postProcessFunction
                    ),
                bedrockEmbeddingModelName,
                true
            );
            String errorMsg = String.format("failed to test: %s", postProcessFunction);
            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", "world")).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 2, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            assertTrue(errorMsg, output.get(1) instanceof Map);
            validateOutput(errorMsg, (Map) output.get(0), DATA_TYPE.get(postProcessFunction));
            validateOutput(errorMsg, (Map) output.get(1), DATA_TYPE.get(postProcessFunction));
        }
    }

    private void validateOutput(String errorMsg, Map<String, Object> output, String dataType) {
        assertTrue(errorMsg, output.containsKey("output"));
        assertTrue(errorMsg, output.get("output") instanceof List);
        List outputList = (List) output.get("output");
        assertEquals(errorMsg, 1, outputList.size());
        assertTrue(errorMsg, outputList.get(0) instanceof Map);
        assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
        assertEquals(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data_type"), dataType);
    }

    private boolean tokenNotSet() {
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            log.info("#### The AWS credentials are not set. Skipping test. ####");
            return true;
        }
        return false;
    }
}
