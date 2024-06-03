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
    protected String functionName;
    protected int maxPredictionTask;

    protected Map<String, String> modelConfigMaps;
    public static final String MODEL_ID = "model_id";
    /**
     * The list of maps that support one or more prediction tasks mapping.
     * The mappings also support JSON path for nested objects.
     *
     * input_map is used to construct model inputs, where the keys represent the model input fields,
     * and the values represent the corresponding document fields.
     *
     *  Example input_map:
     *
     * "input_map": [
     *   {
     *     "input": "book.title"
     *   },
     *   {
     *     "input": "book.text"
     *   }
     * ]
     */
    public static final String INPUT_MAP = "input_map";
    /**
     * output_map is used to construct document fields, where the keys represent the document fields,
     * and the values represent the corresponding model output fields.
     *
     * Example output_map:
     *
     * "output_map": [
     *   {
     *     "book.title_language": "response.language"
     *   },
     *   {
     *     "book.text_language": "response.language"
     *   }
     * ]
     *
     */
    public static final String OUTPUT_MAP = "output_map";
    public static final String FUNCTION_NAME = "function_name";
    public static final String MODEL_CONFIG = "model_config";
    public static final String MAX_PREDICTION_TASKS = "max_prediction_tasks";

    /**
     *  Utility class containing shared parameters for MLModelIngest/SearchProcessor
     *  */

    public InferenceProcessorAttributes(
        String modelId,
        String functionName,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        Map<String, String> modelConfigMaps,
        int maxPredictionTask
    ) {
        this.modelId = modelId;
        this.functionName = functionName;
        this.modelConfigMaps = modelConfigMaps;
        this.inputMaps = inputMaps;
        this.outputMaps = outputMaps;
        this.maxPredictionTask = maxPredictionTask;
    }

}
