/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InferenceProcessorAttributes {

    protected List<Map<String, String>> inputMaps;

    protected List<Map<String, String>> outputMaps;

    protected String modelId;
    protected int maxPredictionTask;

    protected Map<String, String> modelConfigMaps;
    public static final String MODEL_ID = "model_id";
    /**
     * The list of maps will support for one or more prediction tasks mapping
     * model fields on the left and document fields on the right side
     * example input_maps
     *"input_map": [
     *           {
     *             "input": "book.title"
     *           },
     *           {
     *             "input": "book.text"
     *           },
     *         ]
     */
    public static final String INPUT_MAP = "input_map";
    /**
     * example output_maps
     *"output_map": [
     *           {
     *             "response.language": "book.title_language"
     *           },
     *           {
     *             "response.language": "book.text_language"
     *           },
     *         ]
     */
    public static final String OUTPUT_MAP = "output_map";
    public static final String MODEL_CONFIG = "model_config";
    public static final String MAX_PREDICTION_TASKS = "max_prediction_tasks";

    /**
     *  Utility class containing shared parameters for MLModelIngest/SearchProcessor
     *  */

    public InferenceProcessorAttributes(
        String modelId,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        Map<String, String> modelConfigMaps,
        int maxPredictionTask
    ) {
        this.modelId = modelId;
        this.modelConfigMaps = modelConfigMaps;
        this.inputMaps = inputMaps;
        this.outputMaps = outputMaps;
        this.maxPredictionTask = maxPredictionTask;
    }

}
