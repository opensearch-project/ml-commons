/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.engine.annotation.Processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that extracts JSON content from text strings.
 * <p>
 * This processor searches for JSON objects or arrays within a text string and extracts them
 * as structured data. It's useful for parsing responses from LLMs or APIs that return JSON
 * embedded in text, or for extracting JSON from log messages and other unstructured text.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>extract_type</b> (optional): Type of JSON to extract. Default is "auto".
 *     <ul>
 *       <li>"auto" - Extracts the first JSON object or array found</li>
 *       <li>"object" - Only extracts JSON objects (starting with '{')</li>
 *       <li>"array" - Only extracts JSON arrays (starting with '[')</li>
 *     </ul>
 *   </li>
 *   <li><b>default</b> (optional): Value to return if JSON extraction fails or no JSON is found.
 *       If not specified, returns the original input on failure.</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Auto-detect and extract any JSON
 * {
 *   "type": "extract_json"
 * }
 * 
 * // Extract only JSON objects
 * {
 *   "type": "extract_json",
 *   "extract_type": "object"
 * }
 * 
 * // Extract JSON array with default fallback
 * {
 *   "type": "extract_json",
 *   "extract_type": "array",
 *   "default": []
 * }
 * 
 * // Extract from LLM response
 * Input: "Here is the data: {\"name\": \"John\", \"age\": 30}"
 * Output: {"name": "John", "age": 30}
 * 
 * // Extract from mixed content
 * Input: "Results: [{\"id\": 1}, {\"id\": 2}] - processed successfully"
 * Output: [{"id": 1}, {"id": 2}]
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Only processes String inputs; non-string inputs are returned unchanged</li>
 *   <li>Searches for the first occurrence of JSON in the text</li>
 *   <li>Parses and validates the JSON structure</li>
 *   <li>Returns structured data (Map for objects, List for arrays)</li>
 *   <li>If extraction fails or no JSON is found, returns the default value or original input</li>
 *   <li>If extract_type is specified but wrong type is found, returns default or original input</li>
 *   <li>Parsing errors are logged at warn level</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.EXTRACT_JSON)
public class MLExtractJsonProcessor extends AbstractMLProcessor {

    private static final String EXTRACT_TYPE_AUTO = "auto";
    private static final String EXTRACT_TYPE_OBJECT = "object";
    private static final String EXTRACT_TYPE_ARRAY = "array";

    private final String extractType;
    private final Object defaultValue;

    // TODO: Some place use XContentParser to parse json. Make code consistent.
    // Need to research if we should use XContentParser or ObjectMapper first.
    private final ObjectMapper mapper;

    public MLExtractJsonProcessor(Map<String, Object> config) {
        super(config);
        this.extractType = (String) config.getOrDefault("extract_type", EXTRACT_TYPE_AUTO);
        this.defaultValue = config.get("default");
        this.mapper = new ObjectMapper();
    }

    @Override
    protected void validateConfig() {
        if (config.containsKey("extract_type")) {
            String type = (String) config.get("extract_type");
            if (!EXTRACT_TYPE_AUTO.equalsIgnoreCase(type)
                && !EXTRACT_TYPE_OBJECT.equalsIgnoreCase(type)
                && !EXTRACT_TYPE_ARRAY.equalsIgnoreCase(type)) {
                throw new IllegalArgumentException("'extract_type' must be 'auto', 'object', or 'array', got: " + type);
            }
        }
    }

    @Override
    public Object process(Object input) {
        if (!(input instanceof String)) {
            return input;
        }

        String text = (String) input;
        if (text.trim().isEmpty()) {
            return defaultValue != null ? defaultValue : input;
        }

        try {
            int start = findJsonStart(text);
            if (start < 0) {
                log.debug("No JSON found in text");
                return defaultValue != null ? defaultValue : input;
            }

            JsonNode jsonNode = mapper.readTree(text.substring(start));

            if (EXTRACT_TYPE_OBJECT.equalsIgnoreCase(extractType)) {
                if (jsonNode.isObject()) {
                    return mapper.convertValue(jsonNode, Map.class);
                }
                log.debug("Expected JSON object but found {}", jsonNode.getNodeType());
                return defaultValue != null ? defaultValue : input;
            }

            if (EXTRACT_TYPE_ARRAY.equalsIgnoreCase(extractType)) {
                if (jsonNode.isArray()) {
                    return mapper.convertValue(jsonNode, List.class);
                }
                log.debug("Expected JSON array but found {}", jsonNode.getNodeType());
                return defaultValue != null ? defaultValue : input;
            }

            // auto detect
            if (jsonNode.isObject()) {
                return mapper.convertValue(jsonNode, Map.class);
            }
            if (jsonNode.isArray()) {
                return mapper.convertValue(jsonNode, List.class);
            }

            log.debug("JSON node is neither object nor array: {}", jsonNode.getNodeType());
            return defaultValue != null ? defaultValue : input;

        } catch (Exception e) {
            log.warn("Failed to extract JSON from text: {}", e.getMessage());
            return defaultValue != null ? defaultValue : input;
        }
    }

    private int findJsonStart(String text) {
        if (EXTRACT_TYPE_OBJECT.equalsIgnoreCase(extractType)) {
            return text.indexOf('{');
        }

        if (EXTRACT_TYPE_ARRAY.equalsIgnoreCase(extractType)) {
            return text.indexOf('[');
        }

        // Auto mode: find first JSON structure (object or array)
        int startBrace = text.indexOf('{');
        int startBracket = text.indexOf('[');

        if (startBrace < 0) {
            return startBracket;
        }
        if (startBracket < 0) {
            return startBrace;
        }
        return Math.min(startBrace, startBracket);
    }
}
