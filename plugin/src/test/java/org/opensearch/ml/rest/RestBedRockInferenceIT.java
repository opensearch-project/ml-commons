/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import lombok.SneakyThrows;
import org.junit.Before;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        String templates = Files.readString(Path.of(RestMLPredictionAction.class.getClassLoader().getResource("org/opensearch/ml/rest/templates/BedRockConnectorBodies.json").toURI()));
        Map<String, Object> templateMap = StringUtils.gson.fromJson(templates, Map.class);
        for (Map.Entry<String, Object> templateEntry : templateMap.entrySet()) {
            String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
            String testCaseName = templateEntry.getKey();
            String errorMsg = String.format(Locale.ROOT, "Failing test case name: %s", testCaseName);
            String modelId = registerRemoteModel(String.format(StringUtils.gson.toJson(templateEntry.getValue()), GITHUB_CI_AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN), bedrockEmbeddingModelName, true);

            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", "world")).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictRemoteModel(modelId, mlInput);
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 2, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) output.get(0)).get("output") instanceof List);
            List outputList = (List) ((Map<?, ?>) output.get(0)).get("output");
            assertEquals(errorMsg, 1, outputList.size());
            assertTrue(errorMsg, outputList.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
            assertEquals(errorMsg, 1536, ((List) ((Map<?, ?>) outputList.get(0)).get("data")).size());
        }

    }
}
