/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.util.List;
import java.util.Map;

import org.opensearch.script.TemplateScript;

import lombok.Getter;

class MLModelUtil {
    @Getter
    private final List<Map<String, String>> input_map;
    @Getter
    private final List<Map<String, String>> output_map;
    @Getter
    protected String model_id;
    @Getter
    protected List<String> input_output;
    @Getter
    protected TemplateScript.Factory target_field;
    @Getter
    protected Map<String, String> field_map;
    @Getter
    protected Map<String, String> model_config;
    @Getter
    Map<String, String> modelParameters;
    public static final String MODEL_ID = "model_id";
    public static final String INPUT_MAP = "input_map";
    public static final String OUTPUT_MAP = "output_map";
    public static final String MODEL_CONFIG = "model_config";
    public static final String IGNORE_MISSING = "ignore_missing";

    /**
     *  Utility class containing shared parameters and methods for MLModelIngest/SearchProcessors
     *  */

    public MLModelUtil(
        String model_id,
        List<Map<String, String>> input_map,
        List<Map<String, String>> output_map,
        Map<String, String> model_config
    ) {
        this.model_id = model_id;
        this.model_config = model_config;
        this.input_map = input_map;
        this.output_map = output_map;
    }

}
