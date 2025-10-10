/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

/**
 * A processor that iterates through array elements and applies a chain of processors to each element.
 * <p>
 * This processor is useful for transforming array elements uniformly, such as adding missing fields,
 * filtering content, or normalizing data structures across all items in an array.
 * </p>
 * 
 * <h2>Configuration Parameters:</h2>
 * <ul>
 *   <li><b>path</b> (required): JsonPath expression pointing to the array to iterate over.
 *       Examples: "$.messages[*].content[*]", "$.items[*]", "$.data.records[*]"</li>
 *   <li><b>processors</b> (required): A list of processor configurations to apply to each array element.
 *       Each element is processed independently, and the output replaces the original element.</li>
 * </ul>
 * 
 * <h2>Examples:</h2>
 * <pre>
 * // Add missing type field to content items
 * config: {
 *   "path": "$.messages[*].content[*]",
 *   "processors": [
 *     {
 *       "type": "conditional",
 *       "path": "$.type",
 *       "routes": [
 *         {
 *           "not_exists": [
 *             {
 *               "type": "conditional",
 *               "path": "$.text",
 *               "routes": [
 *                 {
 *                   "exists": [
 *                     {"type": "set_field", "path": "$.type", "value": "text"}
 *                   ]
 *                 }
 *               ]
 *             }
 *           ]
 *         }
 *       ]
 *     }
 *   ]
 * }
 * 
 * // Normalize all items in an array
 * config: {
 *   "path": "$.items[*]",
 *   "processors": [
 *     {"type": "set_field", "path": "$.processed", "value": true},
 *     {"type": "remove_jsonpath", "paths": ["$.internal_id"]}
 *   ]
 * }
 * 
 * // Transform nested arrays
 * config: {
 *   "path": "$.users[*].tags[*]",
 *   "processors": [
 *     {"type": "to_string"}
 *   ]
 * }
 * </pre>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>The path must point to an array or array elements (using [*] notation)</li>
 *   <li>Each element is processed independently with the configured processor chain</li>
 *   <li>The output of the processor chain replaces the original element</li>
 *   <li>If the path doesn't exist or doesn't point to an array, returns input unchanged</li>
 *   <li>If processing an element fails, the original element is kept</li>
 *   <li>The modified array is set back at the original path</li>
 * </ul>
 * 
 * <h2>Error Handling:</h2>
 * If the path doesn't exist, doesn't point to an array, or an error occurs during processing,
 * the processor logs a warning and returns the original input unchanged.
 * 
 * @see AbstractMLProcessor
 * @see ProcessorChain
 * @see JsonPath
 */
@Log4j2
@Processor(MLProcessorType.FOR_EACH)
public class MLForEachProcessor extends AbstractMLProcessor {

    /**
     * JsonPath expression pointing to the array to iterate over.
     */
    private final String path;

    /**
     * List of processors to apply to each array element.
     */
    private final List<MLProcessor> processors;

    /**
     * Constructs a new MLForEachProcessor with the specified configuration.
     * 
     * @param config the configuration map containing 'path' and 'processors' parameters
     * @throws IllegalArgumentException if required parameters are missing or invalid
     */
    public MLForEachProcessor(Map<String, Object> config) {
        super(config);
        this.path = (String) config.get("path");
        Object processorsConfig = config.get("processors");
        this.processors = ProcessorChain.parseProcessorConfigs(processorsConfig);

        if (this.processors.isEmpty()) {
            throw new IllegalArgumentException("'processors' list cannot be empty for for_each processor");
        }
    }

    /**
     * Validates that the required configuration parameters are present and valid.
     * 
     * @throws IllegalArgumentException if 'path' or 'processors' is missing, null, or invalid
     */
    @Override
    protected void validateConfig() {
        if (!config.containsKey("path")) {
            throw new IllegalArgumentException("'path' is required for for_each processor");
        }
        String pathValue = (String) config.get("path");
        if (pathValue == null || pathValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'path' cannot be empty for for_each processor");
        }

        if (!config.containsKey("processors")) {
            throw new IllegalArgumentException("'processors' is required for for_each processor");
        }
    }

    /**
     * Processes the input by iterating through the array at the specified path and applying
     * the configured processors to each element.
     * <p>
     * The input is first converted to a JSON string, then parsed as a JSON document.
     * The array at the specified path is read, and each element is processed through the
     * processor chain. The modified array is then set back at the original path.
     * </p>
     * 
     * @param input the input object to process (can be a Map, String, or any JSON-serializable object)
     * @return the modified JSON document with processed array elements, or the original input
     *         if an error occurs or the path doesn't point to an array
     */
    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            DocumentContext context = JsonPath.parse(jsonStr);

            // Read the array
            Object arrayObj;
            try {
                arrayObj = context.read(path);
            } catch (Exception e) {
                log.warn("Path '{}' does not exist in input: {}", path, e.getMessage());
                return input;
            }

            // Validate it's an array
            if (!(arrayObj instanceof List)) {
                log.warn("Path '{}' does not point to an array, found type: {}", path, arrayObj.getClass().getSimpleName());
                return input;
            }

            List<?> array = (List<?>) arrayObj;
            if (array.isEmpty()) {
                log.debug("Array at path '{}' is empty, nothing to process", path);
                return input;
            }

            // Process each element
            List<Object> processedArray = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                Object element = array.get(i);
                try {
                    Object processed = ProcessorChain.applyProcessors(element, processors);
                    processedArray.add(processed);
                } catch (Exception e) {
                    log.warn("Failed to process element at index {} in path '{}': {}", i, path, e.getMessage());
                    // Keep original element on error
                    processedArray.add(element);
                }
            }

            // Set the processed array back
            // Remove [*] wildcards from path for setting
            String setPath = path.replaceAll("\\[\\*\\]$", "");
            context.set(setPath, processedArray);
            return context.json();

        } catch (Exception e) {
            log.warn("Failed to process for_each at path '{}': {}", path, e.getMessage());
            return input;
        }
    }
}
