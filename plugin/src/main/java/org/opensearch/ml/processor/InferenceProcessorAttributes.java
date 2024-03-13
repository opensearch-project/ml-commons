/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.util.List;
import java.util.Map;

import lombok.Getter;

class InferenceProcessorAttributes {
    @Getter
    private final List<Map<String, String>> inputMaps;
    @Getter
    private final List<Map<String, String>> outputMaps;
    @Getter
    protected String modelId;
    @Getter
    protected Map<String, String> modelConfig;
    public static final String MODEL_ID = "model_id";
    public static final String INPUT_MAP = "input_map";
    public static final String OUTPUT_MAP = "output_map";
    public static final String MODEL_CONFIG = "model_config";
    public static final String IGNORE_MISSING = "ignore_missing";

    /**
     *  Utility class containing shared parameters and methods for MLModelIngest/SearchProcessors
     *  */

    public InferenceProcessorAttributes(
        String modelId,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        Map<String, String> modelConfig
    ) {
        this.modelId = modelId;
        this.modelConfig = modelConfig;
        this.inputMaps = inputMaps;
        this.outputMaps = outputMaps;
    }

}
