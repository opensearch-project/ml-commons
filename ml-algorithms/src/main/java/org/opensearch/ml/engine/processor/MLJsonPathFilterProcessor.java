/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that extracts and filters data using JsonPath expressions.
 * <p>
 * This processor evaluates a JsonPath expression against the input document and returns
 * the matching value(s). It's useful for extracting specific fields, filtering arrays,
 * or transforming nested data structures into simpler forms.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>path</b> (required): JsonPath expression to evaluate against the input.
 *       Supports the full JsonPath syntax including filters, wildcards, and array operations.</li>
 *   <li><b>default</b> (optional): Value to return if the path is not found.
 *       If not specified, returns the original input when the path doesn't exist.</li>
 * </ul>
 * <p>
 * <b>JsonPath Syntax Examples:</b>
 * <ul>
 *   <li><b>$.field</b> - Extract a top-level field</li>
 *   <li><b>$.parent.child</b> - Extract a nested field</li>
 *   <li><b>$.items[0]</b> - Extract first array element</li>
 *   <li><b>$.items[*]</b> - Extract all array elements</li>
 *   <li><b>$.items[*].name</b> - Extract specific field from all array elements</li>
 *   <li><b>$.items[?(@.price &lt; 10)]</b> - Filter array elements by condition</li>
 *   <li><b>$..name</b> - Recursively find all 'name' fields</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Extract a nested field
 * {
 *   "type": "jsonpath_filter",
 *   "path": "$.user.email"
 * }
 * Input: {"user": {"name": "John", "email": "john@example.com"}}
 * Output: "john@example.com"
 * 
 * // Extract all items from an array
 * {
 *   "type": "jsonpath_filter",
 *   "path": "$.products[*].name"
 * }
 * Input: {"products": [{"name": "A", "price": 10}, {"name": "B", "price": 20}]}
 * Output: ["A", "B"]
 * 
 * // Filter array elements by condition
 * {
 *   "type": "jsonpath_filter",
 *   "path": "$.items[?(@.active == true)]"
 * }
 * Input: {"items": [{"id": 1, "active": true}, {"id": 2, "active": false}]}
 * Output: [{"id": 1, "active": true}]
 * 
 * // Extract with default fallback
 * {
 *   "type": "jsonpath_filter",
 *   "path": "$.optional.field",
 *   "default": "not_found"
 * }
 * Input: {"other": "data"}
 * Output: "not_found"
 * 
 * // Recursively find all matching fields
 * {
 *   "type": "jsonpath_filter",
 *   "path": "$..id"
 * }
 * Input: {"user": {"id": 1, "profile": {"id": 2}}}
 * Output: [1, 2]
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Evaluates the JsonPath expression against the input document</li>
 *   <li>Returns the extracted value(s) - can be a single value, array, or object</li>
 *   <li>If the path is not found, returns the default value or original input</li>
 *   <li>If JsonPath evaluation fails, returns the original input</li>
 *   <li>Supports all standard JsonPath operators and filters</li>
 *   <li>Errors are logged at warn level with details</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.JSONPATH_FILTER)
public class MLJsonPathFilterProcessor extends AbstractMLProcessor {

    private final String path;
    private final Object defaultValue;

    public MLJsonPathFilterProcessor(Map<String, Object> config) {
        super(config);
        this.path = (String) config.get("path");
        this.defaultValue = config.get("default");
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("path")) {
            throw new IllegalArgumentException("'path' is required for jsonpath_filter processor");
        }
        String pathValue = (String) config.get("path");
        if (pathValue == null || pathValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'path' cannot be empty for jsonpath_filter processor");
        }
    }

    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            return JsonPath.read(jsonStr, path);
        } catch (PathNotFoundException e) {
            log.debug("JsonPath '{}' not found in input", path);
            return defaultValue != null ? defaultValue : input;
        } catch (Exception e) {
            log.warn("Failed to apply JsonPath '{}': {}", path, e.getMessage());
            return defaultValue != null ? defaultValue : input;
        }
    }
}
