/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
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
        if (tokenNotSet()) {
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

    public void testChatCompletionBedrockErrorResponseFormats() throws Exception {
        // Simulate Bedrock inference endpoint behavior
        // You can mock or create sample response maps for two formats

        Map<String, Object> errorFormat1 = Map.of("error", Map.of("message", "Unsupported Claude response format"));

        Map<String, Object> errorFormat2 = Map.of("error", "InvalidRequest");

        // Use the same validation style but inverted for errors
        validateErrorOutput("Should detect error format 1 correctly", errorFormat1, "Unsupported Claude response format");
        validateErrorOutput("Should detect error format 2 correctly", errorFormat2, "InvalidRequest");
    }

    private void validateErrorOutput(String msg, Map<String, Object> output, String expectedError) {
        assertTrue(msg, output.containsKey("error"));
        Object error = output.get("error");

        if (error instanceof Map) {
            assertEquals(msg, expectedError, ((Map<?, ?>) error).get("message"));
        } else if (error instanceof String) {
            assertEquals(msg, expectedError, error);
        } else {
            fail("Unexpected error format: " + error.getClass());
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

    public void test_bedrock_multimodal_model() throws Exception {
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
                            .getResource("org/opensearch/ml/rest/templates/BedRockMultiModalConnectorBodies.json")
                            .toURI()
                    )
            );
        Map<String, Object> templateMap = StringUtils.gson.fromJson(templates, Map.class);
        for (Map.Entry<String, Object> templateEntry : templateMap.entrySet()) {
            String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
            String testCaseName = templateEntry.getKey();
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
            String imageBase64 =
                "iVBORw0KGgoAAAANSUhEUgAAAEkAAAAaCAYAAAD7aXGFAAABXmlDQ1BJQ0MgUHJvZmlsZQAAKJFtkD9LA0EQxd+ZaEADRpRUFulUiBIvAbGMUVRIcUTFP5WXvTOJ5OJydyJ24mcQO1sRrCWFFn6EgKBoIYoI9uI1mpyzOfUSdYdlfjxmZmcf0BFWOS8HARgV28zNTsVWVtdioRd0UfRSTKjM4mlFyVIJvnP7ca4hiXw1KmZFjftG4PTtttS/3njar8l/69tOt6ZbjPIHXZlx0wakBLGyY3PBe8QDJi1FfCC44PGJ4LzHF82axVyGuEYcYUVVI34gjudb9EILG+Vt9rWD2D6sV5YWKEfpDmIaM8hSxKBARgrjmMQcefR/T6rZk8EWOHZhooQCirCpO00KRxk68TwqYBhDnFhGQswVXv/20Ne0ZyBp0FPDvrYZAc4doO/M14Ye6TtHwKXCVVP9cVZygtZG0vNf6qkCnYeu+7oMhEaA+o3rvlddt34MBO6o1/kEFollXGoMcoEAAABWZVhJZk1NACoAAAAIAAGHaQAEAAAAAQAAABoAAAAAAAOShgAHAAAAEgAAAESgAgAEAAAAAQAAAEmgAwAEAAAAAQAAABoAAAAAQVNDSUkAAABTY3JlZW5zaG90dJ8lxQAAAdRpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDYuMC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8ZXhpZjpQaXhlbFlEaW1lbnNpb24+MjY8L2V4aWY6UGl4ZWxZRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+NzM8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpVc2VyQ29tbWVudD5TY3JlZW5zaG90PC9leGlmOlVzZXJDb21tZW50PgogICAgICA8L3JkZjpEZXNjcmlwdGlvbj4KICAgPC9yZGY6UkRGPgo8L3g6eG1wbWV0YT4KaUYItQAABhNJREFUWAntWAtMFGcQHu4OH4g2UhW1PBtRW18V0aCpAQEVvYqpD0xMaqI2ISKlpqRGq0YxatMGa60FxUBsQmIaSWqkoYhK1UqgYK1UrCCoRahW8VUFNYaH3W/O//f22N3b1vpKmOT2n/fuPzv/zOx5+Pr6PqROMIyAzVD6EggtFiv5+Pjwk16/fu1fPbGPz6tksVjo3r17yu+uru1LH6SFC9+n+Pj5vMGpUyN0N6olyMnJJZvNSseO/UQbNqzRUmGeRVfykgiQCU8bnv4dnvYOnoH/ziCZCLJukJDGNptNKWxWTTeQ4acF7mwhHz06jAYNGqxlruK53qdv3340aVIMDRz4mkrPiPD29qawsHEUHj6BevTwNlLVlGnvUlFds2Y9TZgwkVpb28huj1IZDx36Jm3dup15+/Z9RxkZW1XynTu/IX//QLpz5zbNnRsnZXFx79Ls2fOoXz9f7ioQwH9Dw0XKzEynkyd/kbpAVq9eTxMnRlBLSyulpq5S6FTq1q0b65SWFtO6datU+q5Ely5dKC3tKxoy5A2VqL6+jtau/UTFMyJ0M6mkpJjtUP1DQtRvfPr0GdLnuHHhEhfIgAF+jJ4+fUqwKDbWTkuXLqP+/QfIAEEI/8HBr9PGjZ93yCzIAFarRdnURhkg8Fpb27EYwpYt6R0CBIOAgCBKT89SnsPD0F4IdYN05EgRPXzomDMjI2OEPq9hYWMl7dj04yOJgIrNHTiwn/VwtJYt+5hxZMWOHV8rGWWnBQvmUW7ut8y3Wq2ETfXu3Vv6FgiOp6enjVv1ihUpNGfODNq8eZMQa67JySky6Ddv3qRNm1Jp2rQogv3587Xk5eWlelmaTh4xdYPU0tJC1641stqYMY+D0rVrV2V468P8trY28vDwoIiIyEfuiKKiJjPe3t5OZWUljM+f/x7rgcCx2bs3l5qbm+nq1SuUlbWd9uzZzXo4Hnb7TMZdLzjWmGVwJJua7tD9+/ddVVR0TMxUprGPxMRFdPToj9Te3sb2SUkJhMCZBd0gwcHx42Xsx88vQPqLjp7CG25qaqKqqt+ZHx3teCAQyBrApUt/Kg/lOBJBQcHMa2ioV3z+zLjzJTs7U6k7LcwaOfItZ5HEc3J2Sdwd4uXVg/AyAYWF+XTr1i2VCYK1bdsXKp4RYRikgoLv2RapjnMMiIyM5rWysoIOHz7E+LBhw3nFRQQUhRWAo9KzZy/G6+r+4FXrcuPGdWYHBgZ1EOOIInvMQmjoGKl66tRvEndGKip+dSYNccMg1dbWyDccEzOFHYlOUVCQTwcP7ue6hTfXp09fQsYgoID8/Dxe0apxJAHoYnogjra3tyOgenpm+M6jRW3tWU0TfKuhXJgBwyDBwblzNewHdcnfP4A7DJyXl5fSgwcPSHxU2u1xhKMIwAfjlSt/MX758iXZADAW6AHGAkBzs/mM0fMlnhnykJAhmmp4sWgWZsBtkFDwAIGBwbKo1tc/zoiyslKWY6YKDXXUo+rqM8zDBXXp9u2/mUar1wN8kQMuXqzj9UkuJ04cl+YjRoySuDPifCSd+Vq42yCJNu7p6alkiqNzFRcflb7y8/cxjixDIAFFRQd4FRexcT8/fxo//m3BlmtCwlLlmHoyXVmpXUOksgkEnQ9ZDoiNfUfpxo4XIEzxFZGU9JEg3a5ug3T3brNsl716vcIORWBAXLhwnh8IqYuNYrYS2Sfujs4kZi5M0fhrA75QrxITP6RZs+JZFRvLy9srzJ5oLSz8ge1RIzMysrkU4BNn7Nhwhc7SnMf0bug2SDCsqDgh7dFlXFuq8/FqbLwqi70wQnakpX3KJAbNxYsTlCEyj3bt2k0zZ85iPupccnKCPJrC9r+u6elf0tmzVWyOAXX58lVKMylSZq3PeMLHCPO/FW7cRbwV4FqtE11OQHl5xzkIskOHCpXvvTTV/AQ+2jsK7cqVKeQ6Ipj59DDaaErKB3TmzGncRgXI/iVLFsk5TiXUIDye13/cw4eP4k7mGhiNZ3xiVvfu3Wnw4KHsp6am2u207nrD5xYk1wd5kWlTNelF3sCzeLbOIJmIcmeQOoNkIgImVP4BXZkNVryYcSoAAAAASUVORK5CYII=";
            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", imageBase64)).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            String errorMsg = String
                .format(Locale.ROOT, "Failing test case name: %s, inference result: %s", testCaseName, gson.toJson(inferenceResult));
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 1, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) output.get(0)).get("output") instanceof List);
            List outputList = (List) ((Map<?, ?>) output.get(0)).get("output");
            assertEquals(errorMsg, 1, outputList.size());
            assertTrue(errorMsg, outputList.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
            assertEquals(errorMsg, 1024, ((List) ((Map<?, ?>) outputList.get(0)).get("data")).size());
        }

    }

    public void test_bedrock_multimodal_model_empty_imageInput() throws Exception {
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
                            .getResource("org/opensearch/ml/rest/templates/BedRockMultiModalConnectorBodies.json")
                            .toURI()
                    )
            );
        Map<String, Object> templateMap = StringUtils.gson.fromJson(templates, Map.class);
        for (Map.Entry<String, Object> templateEntry : templateMap.entrySet()) {
            String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
            String testCaseName = templateEntry.getKey();
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

            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello")).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            String errorMsg = String
                .format(Locale.ROOT, "Failing test case name: %s, inference result: %s", testCaseName, gson.toJson(inferenceResult));
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 1, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) output.get(0)).get("output") instanceof List);
            List outputList = (List) ((Map<?, ?>) output.get(0)).get("output");
            assertEquals(errorMsg, 1, outputList.size());
            assertTrue(errorMsg, outputList.get(0) instanceof Map);
            assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
            assertEquals(errorMsg, 1024, ((List) ((Map<?, ?>) outputList.get(0)).get("data")).size());
        }
    }

    public void test_bedrock_multimodal_model_empty_imageInput_null_textInput() throws Exception {
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
                            .getResource("org/opensearch/ml/rest/templates/BedRockMultiModalConnectorBodies.json")
                            .toURI()
                    )
            );
        Map<String, Object> templateMap = StringUtils.gson.fromJson(templates, Map.class);
        for (Map.Entry<String, Object> templateEntry : templateMap.entrySet()) {
            String bedrockEmbeddingModelName = "bedrock embedding model " + randomAlphaOfLength(5);
            String testCaseName = templateEntry.getKey();
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

            List<String> input = new ArrayList<>();
            input.add(null);
            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(input).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            String errorMsg = String
                .format(Locale.ROOT, "Failing test case name: %s, inference result: %s", testCaseName, gson.toJson(inferenceResult));
            assertTrue(errorMsg, inferenceResult.containsKey("status"));
            assertTrue(errorMsg, String.valueOf(inferenceResult.get("status")).contains("400"));
            assertTrue(errorMsg, inferenceResult.containsKey("error"));
            assertTrue(errorMsg, inferenceResult.get("error") instanceof Map);
            assertEquals(errorMsg, "illegal_argument_exception", ((Map<?, ?>) inferenceResult.get("error")).get("type"));
            assertEquals(errorMsg, "No input text or image provided", ((Map<?, ?>) inferenceResult.get("error")).get("reason"));
        }
    }

    public void test_bedrock_embedding_v2_model_with_postProcessFunction() throws Exception {
        final List<String> postProcessFunctions = List
            .of("connector.post_process.bedrock_v2.embedding.float", "connector.post_process.bedrock_v2.embedding.binary");
        final Map<String, String> dataType = Map
            .of(
                "connector.post_process.bedrock_v2.embedding.float",
                "FLOAT32",
                "connector.post_process.bedrock_v2.embedding.binary",
                "BINARY"
            );
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
        for (String postProcessFunction : postProcessFunctions) {
            String bedrockEmbeddingModelName = "bedrock embedding model: " + postProcessFunction;
            String modelId = registerRemoteModel(
                String
                    .format(
                        templates,
                        GITHUB_CI_AWS_REGION,
                        AWS_ACCESS_KEY_ID,
                        AWS_SECRET_ACCESS_KEY,
                        AWS_SESSION_TOKEN,
                        org.apache.commons.lang3.StringUtils.substringAfterLast(postProcessFunction, "."),
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
            validateOutput(errorMsg, (Map) output.get(0), dataType.get(postProcessFunction));
            validateOutput(errorMsg, (Map) output.get(1), dataType.get(postProcessFunction));
        }
    }

    private void validateOutput(String errorMsg, Map<String, Object> output, String dataType) {
        assertTrue(errorMsg, output.containsKey("output"));
        assertTrue(errorMsg, output.get("output") instanceof List);
        List outputList = (List) output.get("output");
        assertEquals(errorMsg, 1, outputList.size());
        assertTrue(errorMsg, outputList.get(0) instanceof Map);
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
