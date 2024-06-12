/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.SneakyThrows;

public class RestBedRockInferenceIT extends MLCommonsRestTestCase {
    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String GITHUB_CI_AWS_REGION = "us-west-2";

    @SneakyThrows
    @Before
    public void setup() throws IOException, InterruptedException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        Thread.sleep(20000);
    }

    public void test_bedrock_embedding_model() throws Exception {
        // Skip test if key is null
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String templates = Files
            .readString(
                Path
                    .of(
                        RestMLPredictionAction.class
                            .getClassLoader()
                            .getResource("org/opensearch/ml/rest/templates/BedRockConnectorBodies.json")
                            .toURI()
                    )
            );
        Map<String, Object> templateMap = StringUtils.gson.fromJson(templates, Map.class);
        for (Map.Entry<String, Object> templateEntry : templateMap.entrySet()) {
            String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
            String testCaseName = templateEntry.getKey();
            String errorMsg = String.format(Locale.ROOT, "Failing test case name: %s", testCaseName);
            String modelId = registerRemoteModel(
                String
                    .format(
                        StringUtils.gson.toJson(templateEntry.getValue()),
                        GITHUB_CI_AWS_REGION,
                        AWS_ACCESS_KEY_ID,
                        AWS_SECRET_ACCESS_KEY,
                        AWS_SESSION_TOKEN
                    ),
                bedrockEmbeddingModelName,
                true
            );

            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", "world")).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 2, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            assertTrue(errorMsg, output.get(1) instanceof Map);
            validateOutput(errorMsg, (Map) output.get(0));
            validateOutput(errorMsg, (Map) output.get(1));
        }
    }

    private void validateOutput(String errorMsg, Map<String, Object> output) {
        assertTrue(errorMsg, output.containsKey("output"));
        assertTrue(errorMsg, output.get("output") instanceof List);
        List outputList = (List) output.get("output");
        assertEquals(errorMsg, 1, outputList.size());
        assertTrue(errorMsg, outputList.get(0) instanceof Map);
        assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
        assertEquals(errorMsg, 1536, ((List) ((Map<?, ?>) outputList.get(0)).get("data")).size());
    }
}
