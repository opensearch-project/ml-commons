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
 * Processor that sets a field to a specified value (static or from another field).
 * <p>
 * This processor sets a field at a specified path to either a static value or a value
 * copied from another field. It's useful for adding metadata, default values, copying
 * fields for standardization, or renaming fields. The processor can create new fields
 * or update existing ones.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>path</b> (required): JsonPath expression specifying where to set the value.
 *       Can be an existing path or a new path to create.</li>
 *   <li><b>value</b> (conditionally required): The static value to set at the specified path.
 *       Can be any type: string, number, boolean, object, or array.
 *       Either 'value' or 'source_path' must be provided, but not both.</li>
 *   <li><b>source_path</b> (conditionally required): JsonPath expression to read the value from.
 *       The value at this path will be copied to the target path.
 *       Either 'value' or 'source_path' must be provided, but not both.</li>
 *   <li><b>default</b> (optional): Default value to use when 'source_path' is specified but
 *       the source path doesn't exist or cannot be read. Only applicable with 'source_path'.
 *       If not provided and source path fails, the original input is returned unchanged.</li>
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
 * // Add a processing timestamp (static value)
 * {
 *   "type": "set_field",
 *   "path": "$.metadata.processed_at",
 *   "value": "2024-03-15T10:30:00Z"
 * }
 * 
 * // Set a default status (static value)
 * {
 *   "type": "set_field",
 *   "path": "$.status",
 *   "value": "pending"
 * }
 * 
 * // Copy a field to a new location (dynamic value)
 * // Input: {"a": 1, "b": 2}
 * // Output: {"a": 1, "b": 2, "c": 1}
 * {
 *   "type": "set_field",
 *   "path": "$.c",
 *   "source_path": "$.a"
 * }
 * 
 * // Replace a field's value with another field's value
 * // Input: {"a": 1, "b": 2}
 * // Output: {"a": 1, "b": 1}
 * {
 *   "type": "set_field",
 *   "path": "$.b",
 *   "source_path": "$.a"
 * }
 * 
 * // Copy nested field for standardization
 * // Input: {"user": {"id": 123}}
 * // Output: {"user": {"id": 123}, "userId": 123}
 * {
 *   "type": "set_field",
 *   "path": "$.userId",
 *   "source_path": "$.user.id"
 * }
 * 
 * // Copy field with default fallback
 * // Input: {"name": "John"}
 * // Output: {"name": "John", "status": "unknown"}
 * {
 *   "type": "set_field",
 *   "path": "$.status",
 *   "source_path": "$.user.status",
 *   "default": "unknown"
 * }
 * 
 * // Set an object (static value)
 * {
 *   "type": "set_field",
 *   "path": "$.config",
 *   "value": {
 *     "enabled": true,
 *     "timeout": 30
 *   }
 * }
 * 
 * // Set an array (static value)
 * {
 *   "type": "set_field",
 *   "path": "$.tags",
 *   "value": ["important", "reviewed"]
 * }
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>If 'value' is provided: sets the static value at the target path</li>
 *   <li>If 'source_path' is provided: reads value from source path and sets it at target path</li>
 *   <li>If source path doesn't exist and 'default' is provided: uses the default value</li>
 *   <li>If source path doesn't exist and no 'default': returns original input unchanged</li>
 *   <li>If the target path exists, overwrites the existing value</li>
 *   <li>If the target path doesn't exist, attempts to create it</li>
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
    private final String sourcePath;
    private final Object defaultValue;
    private final boolean hasDefault;

    public MLSetFieldProcessor(Map<String, Object> config) {
        super(config);
        this.targetPath = (String) config.get("path");
        this.value = config.get("value");
        this.sourcePath = (String) config.get("source_path");
        this.defaultValue = config.get("default");
        this.hasDefault = config.containsKey("default");
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

        // Validate value/source_path mutual exclusivity
        boolean hasValue = config.containsKey("value");
        boolean hasSourcePath = config.containsKey("source_path");

        if (!hasValue && !hasSourcePath) {
            throw new IllegalArgumentException("Either 'value' or 'source_path' is required for set_field processor");
        }

        if (hasValue && hasSourcePath) {
            throw new IllegalArgumentException("Cannot specify both 'value' and 'source_path' for set_field processor");
        }

        if (hasSourcePath) {
            String sourcePathValue = (String) config.get("source_path");
            if (sourcePathValue == null || sourcePathValue.trim().isEmpty()) {
                throw new IllegalArgumentException("'source_path' cannot be empty for set_field processor");
            }
        }

        // Validate that 'default' is only used with 'source_path'
        if (config.containsKey("default") && !hasSourcePath) {
            throw new IllegalArgumentException("'default' can only be used with 'source_path' for set_field processor");
        }
    }

    @Override
    public Object process(Object input) {
        try {
            String jsonStr = StringUtils.toJson(input);
            com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(jsonStr);

            // Determine the value to set
            Object valueToSet = determineValue(context);
            if (valueToSet == null && sourcePath != null && !hasDefault) {
                // Source path read failed and no default provided, return original input
                return input;
            }

            if (!setValueAtPath(context, valueToSet)) {
                return input;
            }

            return context.json();
        } catch (Exception e) {
            log.warn("Failed to set field at path '{}': {}", targetPath, e.getMessage());
            return input;
        }
    }

    /**
     * Determines the value to set based on configuration.
     * If source_path is configured, reads from that path.
     * If source_path fails and default is provided, uses the default.
     * Otherwise, uses the static value.
     * 
     * @param context The JsonPath document context
     * @return The value to set, or null if source path read fails and no default
     */
    private Object determineValue(com.jayway.jsonpath.DocumentContext context) {
        if (sourcePath != null) {
            try {
                Object sourceValue = context.read(sourcePath);
                log.debug("Read value from source path '{}'", sourcePath);
                return sourceValue;
            } catch (Exception e) {
                if (hasDefault) {
                    log.debug("Failed to read from source path '{}', using default value", sourcePath);
                    return defaultValue;
                }
                log.warn("Failed to read from source path '{}' and no default provided: {}", sourcePath, e.getMessage());
                return null; // Signal failure
            }
        }
        return value; // Use static value
    }

    /**
     * Attempts to set a value at the target path in the document context.
     * If the path doesn't exist, tries to create it by adding a new field to the parent.
     * 
     * @param context The JsonPath document context
     * @param valueToSet The value to set at the target path
     * @return true if successful, false otherwise
     */
    private boolean setValueAtPath(com.jayway.jsonpath.DocumentContext context, Object valueToSet) {
        try {
            context.set(targetPath, valueToSet);
            log.debug("Successfully set value at path '{}'", targetPath);
            return true;
        } catch (Exception setException) {
            log.debug("Path '{}' doesn't exist, attempting to create it", targetPath);
            return createAndSetPath(context, valueToSet, setException);
        }
    }

    /**
     * Attempts to create a new path and set the value.
     * Only works for simple nested fields where the parent exists.
     * 
     * @param context The JsonPath document context
     * @param valueToSet The value to set at the target path
     * @param originalException The exception from the initial set attempt
     * @return true if successful, false otherwise
     */
    private boolean createAndSetPath(com.jayway.jsonpath.DocumentContext context, Object valueToSet, Exception originalException) {
        int lastDotIndex = targetPath.lastIndexOf('.');
        int lastBracketIndex = targetPath.lastIndexOf('[');

        // Can only create simple nested fields (not array elements)
        if (lastDotIndex > lastBracketIndex) {
            String parentPath = targetPath.substring(0, lastDotIndex);
            String key = targetPath.substring(lastDotIndex + 1);

            try {
                context.put(parentPath, key, valueToSet);
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
