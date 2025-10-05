/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that sets a field to a specified static value.
 * <p>
 * This processor sets a field at a specified path to a static value. It's useful for adding
 * metadata, default values, timestamps, or configuration values to documents. The processor
 * can create new fields or update existing ones.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>path</b> (required): JsonPath expression specifying where to set the value.
 *       Can be an existing path or a new path to create.</li>
 *   <li><b>value</b> (required): The value to set at the specified path.
 *       Can be any type: string, number, boolean, object, or array.</li>
 * </ul>
 * <p>
 * <b>Path Behavior:</b>
 * <ul>
 *   <li>If the path exists, it will be updated with the new value</li>
 *   <li>If the path doesn't exist, the processor attempts to create it</li>
 *   <li>Path creation works for simple nested fields (e.g., "$.parent.newField")</li>
 *   <li>The parent path must exist for new field creation to succeed</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Add a processing timestamp
 * {
 *   "type": "set_field",
 *   "path": "$.metadata.processed_at",
 *   "value": "2024-03-15T10:30:00Z"
 * }
 * 
 * // Set a default status
 * {
 *   "type": "set_field",
 *   "path": "$.status",
 *   "value": "pending"
 * }
 * 
 * // Add a boolean flag
 * {
 *   "type": "set_field",
 *   "path": "$.metadata.processed",
 *   "value": true
 * }
 * 
 * // Set a numeric value
 * {
 *   "type": "set_field",
 *   "path": "$.priority",
 *   "value": 5
 * }
 * 
 * // Set an object
 * {
 *   "type": "set_field",
 *   "path": "$.config",
 *   "value": {
 *     "enabled": true,
 *     "timeout": 30
 *   }
 * }
 * 
 * // Set an array
 * {
 *   "type": "set_field",
 *   "path": "$.tags",
 *   "value": ["important", "reviewed"]
 * }
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Sets the specified value at the target path</li>
 *   <li>If the path exists, overwrites the existing value</li>
 *   <li>If the path doesn't exist, attempts to create it</li>
 *   <li>Path creation only works for simple nested fields (not array elements)</li>
 *   <li>If path operations fail, returns the original input unchanged</li>
 *   <li>Errors are logged at warn level with details</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.SET_FIELD)
public class MLSetFieldProcessor extends AbstractMLProcessor {

    private final String targetPath;
    private final Object value;

    public MLSetFieldProcessor(Map<String, Object> config) {
        super(config);
        this.targetPath = (String) config.get("path");
        this.value = config.get("value");
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("path")) {
            throw new IllegalArgumentException("'path' is required for set_field processor");
        }
        String pathValue = (String) config.get("path");
        if (pathValue == null || pathValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'path' cannot be empty for set_field processor");
        }
        if (!config.containsKey("value")) {
            throw new IllegalArgumentException("'value' is required for set_field processor");
        }
    }

    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(jsonStr);

            if (!setValueAtPath(context)) {
                return input;
            }

            return context.json();
        } catch (Exception e) {
            log.warn("Failed to set field at path '{}': {}", targetPath, e.getMessage());
            return input;
        }
    }

    /**
     * Attempts to set a value at the target path in the document context.
     * If the path doesn't exist, tries to create it by adding a new field to the parent.
     * 
     * @param context The JsonPath document context
     * @return true if successful, false otherwise
     */
    private boolean setValueAtPath(com.jayway.jsonpath.DocumentContext context) {
        try {
            context.set(targetPath, value);
            log.debug("Successfully set value at path '{}'", targetPath);
            return true;
        } catch (Exception setException) {
            log.debug("Path '{}' doesn't exist, attempting to create it", targetPath);
            return createAndSetPath(context, setException);
        }
    }

    /**
     * Attempts to create a new path and set the value.
     * Only works for simple nested fields where the parent exists.
     * 
     * @param context The JsonPath document context
     * @param originalException The exception from the initial set attempt
     * @return true if successful, false otherwise
     */
    private boolean createAndSetPath(com.jayway.jsonpath.DocumentContext context, Exception originalException) {
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
