/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MLModelUtilTests {

    public void testConstructor() {
        String modelId = "my_model";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> inputField = new HashMap<>();
        inputField.put("field", "input_field");
        inputMap.add(inputField);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> outputField = new HashMap<>();
        outputField.put("field", "output_field");
        outputMap.add(outputField);

        Map<String, String> modelConfig = new HashMap<>();
        modelConfig.put("config_key", "config_value");

        MLModelUtil mlModelUtil = new MLModelUtil(modelId, inputMap, outputMap, modelConfig);

        assertEquals(modelId, mlModelUtil.getModel_id());
        assertEquals(inputMap, mlModelUtil.getInput_map());
        assertEquals(outputMap, mlModelUtil.getOutput_map());
        assertEquals(modelConfig, mlModelUtil.getModel_config());
    }

    @Test
    public void testStaticFields() {
        assertNotNull(MLModelUtil.MODEL_ID);
        assertNotNull(MLModelUtil.INPUT_MAP);
        assertNotNull(MLModelUtil.OUTPUT_MAP);
        assertNotNull(MLModelUtil.MODEL_CONFIG);
        assertNotNull(MLModelUtil.IGNORE_MISSING);
    }
}
