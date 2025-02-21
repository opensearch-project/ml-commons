package org.opensearch.ml.rest;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RestCohereInferenceIT extends MLCommonsRestTestCase {
    private final String COHERE_KEY = Optional.ofNullable(System.getenv("COHERE_KEY")).orElse("UzRF34a6gj0OKkvHOO6FZxLItv8CNpK5dFdCaUDW");
    private final Map<String, String> DATA_TYPE = Map.of(
        "connector.post_process.cohere_v2.embedding.float", "FLOAT32",
        "connector.post_process.cohere_v2.embedding.int8", "INT8",
        "connector.post_process.cohere_v2.embedding.uint8", "UINT8",
        "connector.post_process.cohere_v2.embedding.binary", "BINARY",
        "connector.post_process.cohere_v2.embedding.ubinary", "UBINARY"
        );
    private final List<String> POST_PROCESS_FUNCTIONS = List.of(
        "connector.post_process.cohere_v2.embedding.float",
        "connector.post_process.cohere_v2.embedding.int8",
        "connector.post_process.cohere_v2.embedding.uint8",
        "connector.post_process.cohere_v2.embedding.binary",
        "connector.post_process.cohere_v2.embedding.ubinary");

    @Before
    public void setup() throws IOException {
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
    }


    public void test_cohereInference_withDifferent_postProcessFunction() throws URISyntaxException, IOException, InterruptedException {
        String templates = Files
            .readString(
                Path
                    .of(
                        RestMLPredictionAction.class
                            .getClassLoader()
                            .getResource("org/opensearch/ml/rest/templates/CohereConnectorBodies.json")
                            .toURI()
                    )
            );
        for (String postProcessFunction : POST_PROCESS_FUNCTIONS) {
            String connectorRequestBody = String.format(templates, COHERE_KEY, StringUtils.substringAfterLast(postProcessFunction, "."), postProcessFunction);
            String testCaseName = postProcessFunction + "_test";
            String modelId = registerRemoteModel(connectorRequestBody, testCaseName, true);
            String errorMsg = String.format("failed to run test with test name: %s", testCaseName);
            TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(List.of("hello", "world")).build();
            MLInput mlInput = MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
            Map inferenceResult = predictTextEmbeddingModel(modelId, mlInput);
            assertTrue(errorMsg, inferenceResult.containsKey("inference_results"));
            List output = (List) inferenceResult.get("inference_results");
            assertEquals(errorMsg, 1, output.size());
            assertTrue(errorMsg, output.get(0) instanceof Map);
            validateOutput(errorMsg, (Map) output.get(0), DATA_TYPE.get(postProcessFunction));
        }
    }

    private void validateOutput(String errorMsg, Map<String, Object> output, String dataType) {
        assertTrue(errorMsg, output.containsKey("output"));
        assertTrue(errorMsg, output.get("output") instanceof List);
        List outputList = (List) output.get("output");
        assertEquals(errorMsg, 2, outputList.size());
        assertTrue(errorMsg, outputList.get(0) instanceof Map);
        assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data") instanceof List);
        assertTrue(errorMsg, ((Map<?, ?>) outputList.get(0)).get("data_type").equals(dataType));
    }

}
