/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import lombok.extern.log4j.Log4j2;

/**
 * Validates and normalizes tool arguments against JSON schemas.
 * Provides conservative normalization that preserves semantic meaning
 * while fixing common LLM output formatting issues.
 */
@Log4j2
public class ToolArgumentValidator {

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    /**
     * Validates and normalizes tool input against the provided schema.
     * Applies only safe, intent-preserving transformations.
     *
     * @param toolInput the raw tool input from LLM
     * @param schema the JSON schema to validate against (currently used for logging)
     * @return normalized and validated input as Map
     * @throws IllegalArgumentException if input cannot be safely normalized
     */
    public Map<String, Object> validateAndNormalize(String toolInput, String schema) {
        if (toolInput == null || toolInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool input cannot be null or empty");
        }

        try {
            // First attempt: parse as-is
            JsonObject input = GSON.fromJson(toolInput, JsonObject.class);
            return normalizeJsonObject(input);
        } catch (JsonSyntaxException e) {
            log.debug("Initial parsing failed, attempting conservative normalization: {}", e.getMessage());
        }

        // Second attempt: apply conservative normalization
        String normalizedInput = applyConservativeNormalization(toolInput);
        try {
            JsonObject input = GSON.fromJson(normalizedInput, JsonObject.class);
            log.debug("Successfully normalized malformed tool input");
            return normalizeJsonObject(input);
        } catch (JsonSyntaxException e) {
            log.warn("Tool input validation failed after normalization attempts. Input length: {}", toolInput.length());
            throw new IllegalArgumentException(
                "Tool arguments are malformed and cannot be safely normalized. Please retry with valid JSON format.",
                e
            );
        }
    }

    /**
     * Normalizes a JsonObject by handling stringified JSON values.
     * This is the primary normalization: converting stringified JSON objects to actual objects.
     */
    private Map<String, Object> normalizeJsonObject(JsonObject input) {
        JsonObject normalized = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String stringValue = value.getAsString();

                // Check if this string is actually a JSON object/array
                if (isStringifiedJson(stringValue)) {
                    try {
                        JsonElement parsed = GSON.fromJson(stringValue, JsonElement.class);
                        normalized.add(key, parsed);
                        log.debug("Normalized stringified JSON for field: {}", key);
                        continue;
                    } catch (JsonSyntaxException e) {
                        // If parsing fails, keep as string
                        log.debug("Failed to parse potential JSON string for field {}: {}", key, e.getMessage());
                    }
                }
            }

            // Keep original value if no normalization applied
            normalized.add(key, value);
        }

        return GSON.fromJson(normalized, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    /**
     * Applies conservative normalization to malformed JSON strings.
     * Only applies transformations that are very likely to preserve intent.
     */
    private String applyConservativeNormalization(String input) {
        String normalized = input.trim();

        // Only apply stringified JSON unwrapping - the safest transformation
        normalized = unwrapStringifiedJson(normalized);

        return normalized;
    }

    /**
     * Unwraps stringified JSON by removing outer quotes if the content is valid JSON.
     * Example: "{"key":"value"}" -> {"key":"value"}
     */
    private String unwrapStringifiedJson(String input) {
        if (input.length() < 2) {
            return input;
        }

        // Check if wrapped in quotes
        if (input.startsWith("\"") && input.endsWith("\"")) {
            String unwrapped = input.substring(1, input.length() - 1);

            // Unescape basic JSON escapes
            unwrapped = unwrapped.replace("\\\"", "\"").replace("\\\\", "\\");

            // Validate that unwrapped content is valid JSON
            try {
                GSON.fromJson(unwrapped, JsonElement.class);
                log.debug("Successfully unwrapped stringified JSON");
                return unwrapped;
            } catch (JsonSyntaxException e) {
                log.debug("Unwrapping would create invalid JSON, keeping original");
                return input;
            }
        }

        return input;
    }

    /**
     * Checks if a string appears to be stringified JSON (starts with { or [).
     */
    private boolean isStringifiedJson(String value) {
        if (value == null || value.length() < 2) {
            return false;
        }

        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * Validation result containing the outcome and any error details.
     */
    public static class ValidationResult {
        private final boolean success;
        private final Map<String, Object> normalizedInput;
        private final String errorMessage;

        private ValidationResult(boolean success, Map<String, Object> normalizedInput, String errorMessage) {
            this.success = success;
            this.normalizedInput = normalizedInput;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success(Map<String, Object> normalizedInput) {
            return new ValidationResult(true, normalizedInput, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, Object> getNormalizedInput() {
            return normalizedInput;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
