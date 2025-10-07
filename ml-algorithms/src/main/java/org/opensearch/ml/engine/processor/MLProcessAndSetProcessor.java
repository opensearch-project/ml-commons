/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that applies a chain of nested processors to the input and sets the result at a target path.
 * <p>
 * This processor allows you to transform data through a series of processors and then place the
 * transformed result at a specific location in the document using JsonPath. It's useful for
 * complex transformations where you need to process data and store it in a new or existing field.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>path</b> (required): JsonPath expression specifying where to set the processed result.
 *       Can be an existing path or a new path to create.</li>
 *   <li><b>processors</b> (required): A list of processor configurations to apply sequentially.
 *       The output of each processor becomes the input to the next.</li>
 * </ul>
 * <p>
 * <b>Path Behavior:</b>
 * <ul>
 *   <li>If the path exists, it will be updated with the processed value</li>
 *   <li>If the path doesn't exist, the processor attempts to create it</li>
 *   <li>Path creation works for simple nested fields (e.g., "$.parent.newField")</li>
 *   <li>The parent path must exist for new field creation to succeed</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Process input and set result at a new field
 * {
 *   "type": "process_and_set",
 *   "path": "$.processed.result",
 *   "processors": [
 *     {
 *       "type": "to_string"
 *     },
 *     {
 *       "type": "regex_replace",
 *       "pattern": "[^a-zA-Z0-9]",
 *       "replacement": "_"
 *     }
 *   ]
 * }
 * 
 * // Extract and transform nested data
 * {
 *   "type": "process_and_set",
 *   "path": "$.summary.userInfo",
 *   "processors": [
 *     {
 *       "type": "keep_fields",
 *       "fields": ["username", "email"]
 *     },
 *     {
 *       "type": "to_string"
 *     }
 *   ]
 * }
 * 
 * // Chain multiple transformations
 * {
 *   "type": "process_and_set",
 *   "path": "$.metadata.tags",
 *   "processors": [
 *     {
 *       "type": "extract_json",
 *       "path": "$.rawTags"
 *     },
 *     {
 *       "type": "jsonpath_filter",
 *       "path": "$[?(@.active == true)]"
 *     }
 *   ]
 * }
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Processors are applied sequentially to the input</li>
 *   <li>The final processed result is set at the target path</li>
 *   <li>If path setting fails, attempts to create the path by adding a new field to the parent</li>
 *   <li>If processing or path operations fail, the original input is returned unchanged</li>
 *   <li>Errors are logged at warn level with details about the failure</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.PROCESS_AND_SET)
public class MLProcessAndSetProcessor extends AbstractMLProcessor {

    private final String targetPath;
    private final List<MLProcessor> nestedProcessors;

    public MLProcessAndSetProcessor(Map<String, Object> config) {
        super(config);
        this.targetPath = (String) config.get("path");
        Object processorsConfig = config.get("processors");
        this.nestedProcessors = ProcessorChain.parseProcessorConfigs(processorsConfig);

        if (this.nestedProcessors.isEmpty()) {
            throw new IllegalArgumentException("'processors' list cannot be empty for process_and_set processor");
        }
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("path")) {
            throw new IllegalArgumentException("'path' is required for process_and_set processor");
        }
        String pathValue = (String) config.get("path");
        if (pathValue == null || pathValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'path' cannot be empty for process_and_set processor");
        }
        if (!config.containsKey("processors")) {
            throw new IllegalArgumentException("'processors' is required for process_and_set processor");
        }
    }

    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(jsonStr);

            // Apply the processor chain to the input
            Object processedValue = ProcessorChain.applyProcessors(input, nestedProcessors);

            // Try to set the value at the target path
            if (!setValueAtPath(context, processedValue)) {
                return input;
            }

            return context.json();
        } catch (Exception e) {
            log.warn("Failed to process and set at path '{}': {}", targetPath, e.getMessage());
            return input;
        }
    }

    /**
     * Attempts to set a value at the target path in the document context.
     * If the path doesn't exist, tries to create it by adding a new field to the parent.
     * 
     * @param context The JsonPath document context
     * @param value The value to set
     * @return true if successful, false otherwise
     */
    private boolean setValueAtPath(com.jayway.jsonpath.DocumentContext context, Object value) {
        try {
            context.set(targetPath, value);
            return true;
        } catch (Exception setException) {
            log.debug("Failed to set value at path '{}': {}. Attempting to create it", targetPath, setException.getMessage());
            return createAndSetPath(context, value, setException);
        }
    }

    /**
     * Attempts to create a new path and set the value.
     * Only works for simple nested fields where the parent exists.
     * 
     * @param context The JsonPath document context
     * @param value The value to set
     * @param originalException The exception from the initial set attempt
     * @return true if successful, false otherwise
     */
    private boolean createAndSetPath(com.jayway.jsonpath.DocumentContext context, Object value, Exception originalException) {
        int lastDotIndex = targetPath.lastIndexOf('.');
        int lastBracketIndex = targetPath.lastIndexOf('[');

        // Can only create simple nested fields (not array elements)
        if (lastDotIndex > lastBracketIndex) {
            String parentPath = targetPath.substring(0, lastDotIndex);
            String key = targetPath.substring(lastDotIndex + 1);

            try {
                context.put(parentPath, key, value);
                log.debug("Successfully created new field '{}' at parent path '{}'", key, parentPath);
                return true;
            } catch (Exception putException) {
                log
                    .warn(
                        "Failed to create new path '{}': parent path '{}' may not exist or is not an object. Error: {}",
                        targetPath,
                        parentPath,
                        putException.getMessage()
                    );
                return false;
            }
        } else {
            log
                .warn(
                    "Failed to set path '{}' and cannot create it (array paths not supported for creation): {}",
                    targetPath,
                    originalException.getMessage()
                );
            return false;
        }
    }
}
