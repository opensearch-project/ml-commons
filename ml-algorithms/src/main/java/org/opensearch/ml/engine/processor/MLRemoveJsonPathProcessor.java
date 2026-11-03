/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.List;
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
 * by specifying one or more JsonPath expressions. It's useful for filtering out sensitive data,
 * removing unnecessary fields, or cleaning up JSON responses.
 * </p>
 * 
 * <h2>Configuration Parameters:</h2>
 * <ul>
 *   <li><b>paths</b> (required): A list of JsonPath expressions identifying the field(s) to remove.
 *       Examples: ["$.user.email", "$.password"], ["$.items[0]"], ["$.metadata.internal"]</li>
 * </ul>
 * 
 * <h2>Examples:</h2>
 * <pre>
 * // Remove multiple fields
 * config: {"paths": ["$.password", "$.ssn"]}
 * input:  {"username": "john", "password": "secret", "ssn": "123-45-6789"}
 * output: {"username": "john"}
 * 
 * // Remove a single field
 * config: {"paths": ["$.user.email"]}
 * input:  {"user": {"name": "John", "email": "john@example.com"}}
 * output: {"user": {"name": "John"}}
 * 
 * // Remove multiple nested fields
 * config: {"paths": ["$.user.email", "$.user.phone"]}
 * input:  {"user": {"name": "John", "email": "john@example.com", "phone": "555-1234"}}
 * output: {"user": {"name": "John"}}
 * </pre>
 * 
 * <h2>Error Handling:</h2>
 * If a path doesn't exist or an error occurs during removal, the processor logs a warning
 * and continues processing remaining paths.
 * 
 * @see AbstractMLProcessor
 * @see JsonPath
 */
@Log4j2
@Processor(MLProcessorType.REMOVE_JSONPATH)
public class MLRemoveJsonPathProcessor extends AbstractMLProcessor {

    /**
     * The list of JsonPath expressions identifying the field(s) to remove from the input.
     */
    private final List<String> paths;

    /**
     * Constructs a new MLRemoveJsonPathProcessor with the specified configuration.
     * 
     * @param config the configuration map containing the 'paths' parameter
     * @throws IllegalArgumentException if the 'paths' parameter is missing or empty
     */
    public MLRemoveJsonPathProcessor(Map<String, Object> config) {
        super(config);
        this.paths = (List<String>) config.get("paths");
    }

    /**
     * Validates that the required 'paths' configuration parameter is present and not empty.
     * 
     * @throws IllegalArgumentException if 'paths' is missing, null, empty, or contains invalid values
     */
    @Override
    protected void validateConfig() {
        if (!config.containsKey("paths")) {
            throw new IllegalArgumentException("'paths' is required for remove_jsonpath processor");
        }
        Object pathsValue = config.get("paths");
        if (pathsValue == null) {
            throw new IllegalArgumentException("'paths' cannot be null for remove_jsonpath processor");
        }
        if (!(pathsValue instanceof List)) {
            throw new IllegalArgumentException("'paths' must be a list for remove_jsonpath processor");
        }
        List<?> pathsList = (List<?>) pathsValue;
        if (pathsList.isEmpty()) {
            throw new IllegalArgumentException("'paths' cannot be empty for remove_jsonpath processor");
        }
        for (Object path : pathsList) {
            if (path == null || !(path instanceof String) || ((String) path).isBlank()) {
                throw new IllegalArgumentException("Each path in 'paths' must be a non-empty string");
            }
        }
    }

    /**
     * Processes the input by removing the field(s) specified by the JsonPath expressions.
     * <p>
     * The input is first converted to a JSON string, then parsed as a JSON document.
     * Each specified path is deleted from the document in order, and the modified document is returned.
     * </p>
     * 
     * @param input the input object to process (can be a Map, String, or any JSON-serializable object)
     * @return the modified JSON document with the specified paths removed, or the original input
     *         if an error occurs
     */
    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            DocumentContext context = JsonPath.parse(jsonStr);

            for (String path : paths) {
                try {
                    context.delete(path);
                } catch (Exception e) {
                    log.warn("Failed to remove JsonPath {}: {}", path, e.getMessage());
                }
            }

            return context.json();
        } catch (Exception e) {
            log.warn("Failed to process input for remove_jsonpath: {}", e.getMessage());
            return input;
        }
    }
}
