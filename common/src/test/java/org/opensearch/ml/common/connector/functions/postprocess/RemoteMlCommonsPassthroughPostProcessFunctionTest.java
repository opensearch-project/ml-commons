/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.ml.common.output.model.ModelTensors.OUTPUT_FIELD;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

public class RemoteMlCommonsPassthroughPostProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RemoteMlCommonsPassthroughPostProcessFunction function;

    @Before
    public void setUp() {
        function = new RemoteMlCommonsPassthroughPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotMapOrList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input must be a Map or List");
        function.apply("abc", null);
    }

    /**
     * Tests processing of ML-Commons response containing sparse vector data with rank features.
     * Validates that sparse vectors with dataAsMap containing token-score pairs are correctly parsed.
     */
    @Test
    public void process_MLCommonsResponse_RankFeatures() {
        Map<String, Object> rankFeatures = Map
            .of("increasingly", 0.028670792, "achievements", 0.4906937, "nation", 0.15371077, "hello", 0.35982144, "today", 3.0966291);
        Map<String, Object> innerDataAsMap = Map.of("response", Arrays.asList(rankFeatures));
        Map<String, Object> output = Map.of("name", "output", "dataAsMap", innerDataAsMap);
        Map<String, Object> inferenceResult = Map.of("output", Arrays.asList(output));
        Map<String, Object> input = Map.of("inference_results", Arrays.asList(inferenceResult));

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(1, result.size());
        ModelTensor tensor = result.get(0);
        assertEquals("output", tensor.getName());
        assertEquals(innerDataAsMap, tensor.getDataAsMap());

        // Verify the nested sparse data structure
        Map<String, Object> dataAsMap = (Map<String, Object>) tensor.getDataAsMap();
        List<Map<String, Object>> response = (List<Map<String, Object>>) dataAsMap.get("response");
        assertEquals(1, response.size());
        assertEquals(0.35982144, (Double) response.get(0).get("hello"), 0.0001);
        assertEquals(3.0966291, (Double) response.get(0).get("today"), 0.0001);
    }

    /**
     * Tests processing of ML-Commons response containing dense vector data with numerical arrays.
     * Validates that dense vectors with data_type, shape, and data fields are correctly parsed.
     */
    @Test
    public void process_MLCommonsResponse_DenseVector() {
        Map<String, Object> output = Map
            .of(
                "name",
                "sentence_embedding",
                "data_type",
                "FLOAT32",
                "shape",
                Arrays.asList(3L),
                "data",
                Arrays.asList(0.5400895, -0.19082281, 0.4996347)
            );
        Map<String, Object> inferenceResult = Map.of("output", Arrays.asList(output));
        Map<String, Object> input = Map.of("inference_results", Arrays.asList(inferenceResult));

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(1, result.size());
        ModelTensor tensor = result.get(0);
        assertEquals("sentence_embedding", tensor.getName());
        assertEquals(MLResultDataType.FLOAT32, tensor.getDataType());
        assertEquals(1, tensor.getShape().length);
        assertEquals(3L, tensor.getShape()[0]);
        assertEquals(3, tensor.getData().length);
        assertEquals(0.5400895, tensor.getData()[0].doubleValue(), 0.0001);
    }

    /**
     * Tests processing of ML-Commons response with multiple output tensors in a single inference result.
     * Ensures all outputs are processed and returned as separate ModelTensor objects.
     */
    @Test
    public void process_MLCommonsResponse_MultipleOutputs() {
        Map<String, Object> output1 = Map.of("name", "output1", "result", "result1");
        Map<String, Object> output2 = Map.of("name", "output2", "result", "result2");
        Map<String, Object> inferenceResult = Map.of("output", Arrays.asList(output1, output2));
        Map<String, Object> input = Map.of("inference_results", Arrays.asList(inferenceResult));

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(2, result.size());
        assertEquals("output1", result.get(0).getName());
        assertEquals("result1", result.get(0).getResult());
        assertEquals("output2", result.get(1).getName());
        assertEquals("result2", result.get(1).getResult());
    }

    /**
     * Tests edge case where ML-Commons response has empty inference_results array.
     * Should return empty list without errors.
     */
    @Test
    public void process_MLCommonsResponse_EmptyInferenceResults() {
        Map<String, Object> input = Map.of("inference_results", Arrays.asList());

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(0, result.size());
    }

    /**
     * Tests edge cases where inference result lacks the expected format.
     * Should skip processing and return empty list.
     */
    @Test
    public void process_MLCommonsResponse_InvalidOutputs() {
        Map<String, Object> inferenceResult = Map.of("other_field", "value");
        Map<String, Object> input = Map.of("inference_results", Arrays.asList(inferenceResult));

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(0, result.size());

        // correct format, but with empty output
        inferenceResult = Map.of("output", List.of(Map.of()));
        input = Map.of("inference_results", List.of(inferenceResult));

        result = function.apply(input, null);

        assertEquals(0, result.size());

        // Fallback for non-ml-commons responses
        input = Map.of("invalid_format", "invalid value");
        result = function.apply(input, null);

        assertEquals(1, result.size());
        assertEquals(input, result.getFirst().getDataAsMap());
        assertEquals("response", result.getFirst().getName());
    }

    /**
     * Tests processing of ML-Commons response containing dense vector data with numerical arrays.
     * Validates that when the types are incorrect, values are parsed as nulls.
     */
    @Test
    public void process_MLCommonsResponse_InvalidDenseVectorFormat() {
        Map<String, Object> output = Map
            .of(
                "name",
                List.of("Not a string"),
                "data_type",
                "NON-EXISTENT TYPE",
                "shape",
                "not a list of long",
                "data",
                "not a list of numbers"
            );
        Map<String, Object> inferenceResult = Map.of("output", Arrays.asList(output));
        Map<String, Object> input = Map.of("inference_results", Arrays.asList(inferenceResult));

        List<ModelTensor> result = function.apply(input, null);

        assertEquals(1, result.size());
        ModelTensor tensor = result.getFirst();
        assertEquals(OUTPUT_FIELD, tensor.getName());
        assertNull(tensor.getShape());
        assertNull(tensor.getData());
        assertNull(tensor.getDataType());
    }
}
