/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

/**
 * A processor that removes fields from JSON documents using JsonPath expressions.
 * <p>
 * This processor allows you to delete specific fields or elements from JSON structures
 * by specifying a JsonPath expression. It's useful for filtering out sensitive data,
 * removing unnecessary fields, or cleaning up JSON responses.
 * </p>
 * 
 * <h2>Configuration Parameters:</h2>
 * <ul>
 *   <li><b>path</b> (required): The JsonPath expression identifying the field(s) to remove.
 *       Examples: "$.user.email", "$.items[0]", "$.metadata.internal"</li>
 * </ul>
 * 
 * <h2>Examples:</h2>
 * <pre>
 * // Remove a simple field
 * config: {"path": "$.password"}
 * input:  {"username": "john", "password": "secret"}
 * output: {"username": "john"}
 * 
 * // Remove a nested field
 * config: {"path": "$.user.email"}
 * input:  {"user": {"name": "John", "email": "john@example.com"}}
 * output: {"user": {"name": "John"}}
 * 
 * // Remove an array element
 * config: {"path": "$.items[1]"}
 * input:  {"items": ["a", "b", "c"]}
 * output: {"items": ["a", "c"]}
 * </pre>
 * 
 * <h2>Error Handling:</h2>
 * If the path doesn't exist or an error occurs during removal, the processor logs a warning
 * and returns the original input unchanged.
 * 
 * @see AbstractMLProcessor
 * @see JsonPath
 */
@Log4j2
@Processor(MLProcessorType.REMOVE_JSONPATH)
public class MLRemoveJsonPathProcessor extends AbstractMLProcessor {

    /**
     * The JsonPath expression identifying the field(s) to remove from the input.
     */
    private final String path;

    /**
     * Constructs a new MLRemoveJsonPathProcessor with the specified configuration.
     * 
     * @param config the configuration map containing the 'path' parameter
     * @throws IllegalArgumentException if the 'path' parameter is missing or empty
     */
    public MLRemoveJsonPathProcessor(Map<String, Object> config) {
        super(config);
        this.path = (String) config.get("path");
    }

    /**
     * Validates that the required 'path' configuration parameter is present and not empty.
     * 
     * @throws IllegalArgumentException if 'path' is missing, null, or blank
     */
    @Override
    protected void validateConfig() {
        if (!config.containsKey("path")) {
            throw new IllegalArgumentException("'path' is required for remove_jsonpath processor");
        }
        String pathValue = (String) config.get("path");
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalArgumentException("'path' cannot be empty for remove_jsonpath processor");
        }
    }

    /**
     * Processes the input by removing the field(s) specified by the JsonPath expression.
     * <p>
     * The input is first converted to a JSON string, then parsed as a JSON document.
     * The specified path is deleted from the document, and the modified document is returned.
     * </p>
     * 
     * @param input the input object to process (can be a Map, String, or any JSON-serializable object)
     * @return the modified JSON document with the specified path removed, or the original input
     *         if an error occurs or the path doesn't exist
     */
    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            DocumentContext context = JsonPath.parse(jsonStr);
            context.delete(path);
            return context.json();
        } catch (Exception e) {
            log.warn("Failed to remove JsonPath {}: {}", path, e.getMessage());
            return input;
        }
    }
}
