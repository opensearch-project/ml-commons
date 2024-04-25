/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opensearch.ml.processor.MLInferenceIngestProcessor.DEFAULT_MAX_PREDICTION_TASKS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class InferenceProcessorAttributesTests {

    public void testConstructor() {
        String modelId = "my_model";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> inputField = new HashMap<>();
        inputField.put("model_input", "document_field");
        inputMap.add(inputField);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> outputField = new HashMap<>();
        outputField.put("model_output", "new_document_field");
        outputMap.add(outputField);

        Map<String, String> modelConfig = new HashMap<>();
        modelConfig.put("config_key", "config_value");

        InferenceProcessorAttributes mlModelUtil = new InferenceProcessorAttributes(
            modelId,
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS
        );

        assertEquals(modelId, mlModelUtil.getModelId());
        assertEquals(inputMap, mlModelUtil.getOutputMaps());
        assertEquals(outputMap, mlModelUtil.getOutputMaps());
        assertEquals(modelConfig, mlModelUtil.getModelConfigMaps());
        assertEquals(DEFAULT_MAX_PREDICTION_TASKS, mlModelUtil.getMaxPredictionTask());
    }

    @Test
    public void testStaticFields() {
        assertNotNull(InferenceProcessorAttributes.MODEL_ID);
        assertNotNull(InferenceProcessorAttributes.INPUT_MAP);
        assertNotNull(InferenceProcessorAttributes.OUTPUT_MAP);
        assertNotNull(InferenceProcessorAttributes.MODEL_CONFIG);
        assertNotNull(InferenceProcessorAttributes.MAX_PREDICTION_TASKS);
    }
}
