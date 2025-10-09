/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.opensearch.OpenSearchParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for auto-generating JSONPath expressions from JSON Schema.
 *
 * This class analyzes the "output" schema from MLModel's modelInterface field
 * and generates a JSONPath expression to extract LLM text responses from
 * connector-specific dataAsMap structures.
 *
 * The generator looks for fields marked with the custom schema property
 * "x-llm-output": true to identify the target LLM text field.
 *
 * Example Usage:
 * <pre>
 * String outputSchema = model.getModelInterface().get("output");
 * String llmResultPath = LlmResultPathGenerator.generate(outputSchema);
 * // Returns: "$.choices[0].message.content" (for OpenAI)
 * // or: "$.content[0].text" (for Bedrock Claude)
 * </pre>
 */
@Log4j2
public class LlmResultPathGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Custom JSON Schema extension marker for LLM output fields
    private static final String LLM_OUTPUT_MARKER = "x-llm-output";

    /**
     * Generates a JSONPath expression from the model's output schema.
     *
     * This method searches for fields marked with "x-llm-output": true in the schema.
     * It is designed to work with properly annotated schemas from supported models
     * (GPT-4o-mini, GPT-5, Claude 3.7+).
     *
     * If no marker is found, returns null and the caller should use a default fallback path.
     *
     * @param outputSchemaJson The JSON Schema string from model.interface.output
     * @return JSONPath expression (e.g., "$.choices[0].message.content"), or null if no marker found
     * @throws IOException if schema parsing fails
     * @throws OpenSearchParseException if schema structure is invalid
     */
    public static String generate(String outputSchemaJson) throws IOException {
        if (outputSchemaJson == null || outputSchemaJson.trim().isEmpty()) {
            log.warn("Output schema is null or empty, cannot generate llm_result_path");
            return null;
        }

        try {
            JsonNode schemaRoot = MAPPER.readTree(outputSchemaJson);

            // Navigate to dataAsMap schema node using hardcoded path; if not found, search from root
            JsonNode searchRoot = navigateToDataAsMapSchema(schemaRoot);
            if (searchRoot == null) {
                log.debug("No dataAsMap schema found, searching from root");
                searchRoot = schemaRoot;
            }

            // Search for LLM output field with x-llm-output marker
            String jsonPath = findLlmTextField(searchRoot, "$");

            if (jsonPath == null) {
                log.warn("Could not find field with x-llm-output marker in schema");
                return null;
            }

            log.debug("Generated llm_result_path: {}", jsonPath);
            return jsonPath;

        } catch (Exception e) {
            log.error("Failed to generate llm_result_path from schema", e);
            throw new OpenSearchParseException("Schema parsing error: " + e.getMessage(), e);
        }
    }

    /**
     * Navigates to the dataAsMap schema node using the rigid ModelTensorOutput structure.
     *
     * The path follows the serialization structure defined by:
     * - ModelTensorOutput.INFERENCE_RESULT_FIELD = "inference_results"
     * - ModelTensors.OUTPUT_FIELD = "output"
     * - ModelTensor.DATA_AS_MAP_FIELD = "dataAsMap"
     *
     * Schema path: properties.inference_results.items.properties.output.items.properties.dataAsMap
     *
     * @param schemaRoot The root schema node
     * @return The dataAsMap schema node if found, null otherwise
     */
    private static JsonNode navigateToDataAsMapSchema(JsonNode schemaRoot) {
        if (schemaRoot == null || schemaRoot.isMissingNode()) {
            return null;
        }

        // Follow the rigid ModelTensorOutput → ModelTensors → ModelTensor structure
        JsonNode dataAsMapSchema = schemaRoot
            .path("properties")
            .path("inference_results")
            .path("items")
            .path("properties")
            .path("output")
            .path("items")
            .path("properties")
            .path("dataAsMap");

        return dataAsMapSchema.isMissingNode() ? null : dataAsMapSchema;
    }

    /**
     * Recursively searches for the LLM text field marked with "x-llm-output": true.
     *
     * @param schemaNode The current schema node to search
     * @param currentPath The current JSONPath being built
     * @return JSONPath expression to the LLM text field, or null if not found
     */
    private static String findLlmTextField(JsonNode schemaNode, String currentPath) {
        return findLlmTextFieldWithMarker(schemaNode, currentPath);
    }

    /**
     * Searches ONLY for fields with explicit "x-llm-output": true marker.
     * Does NOT use any heuristic field name matching.
     *
     * @param schemaNode The current schema node to search
     * @param currentPath The current JSONPath being built
     * @return JSONPath expression if marker found, null otherwise
     */
    private static String findLlmTextFieldWithMarker(JsonNode schemaNode, String currentPath) {
        if (schemaNode == null || schemaNode.isMissingNode()) {
            return null;
        }

        // Check if this field has the x-llm-output marker
        JsonNode marker = schemaNode.get(LLM_OUTPUT_MARKER);
        if (marker != null && marker.isBoolean() && marker.asBoolean()) {
            return currentPath;
        }

        // Get the type of this schema node
        JsonNode typeNode = schemaNode.get("type");
        String type = typeNode != null && typeNode.isTextual() ? typeNode.asText() : null;

        // If it's an object, recursively search properties
        if ("object".equals(type) || schemaNode.has("properties")) {
            JsonNode properties = schemaNode.get("properties");
            if (properties != null && properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String fieldName = field.getKey();
                    JsonNode fieldSchema = field.getValue();

                    String newPath = currentPath + "." + fieldName;
                    String result = findLlmTextFieldWithMarker(fieldSchema, newPath);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        // If it's an array, navigate into items
        if ("array".equals(type) || schemaNode.has("items")) {
            JsonNode items = schemaNode.get("items");
            if (items != null) {
                String newPath = currentPath + "[0]";
                String result = findLlmTextFieldWithMarker(items, newPath);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Validates that a generated JSONPath can be parsed and applied.
     *
     * This is a basic validation that checks if the path syntax is valid.
     * It does not validate against actual data.
     *
     * @param jsonPath The JSONPath expression to validate
     * @return true if the path appears valid, false otherwise
     */
    public static boolean isValidJsonPath(String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            return false;
        }

        // Basic validation: must start with $
        if (!jsonPath.startsWith("$")) {
            return false;
        }

        // Check for balanced brackets
        int bracketCount = 0;
        for (char c : jsonPath.toCharArray()) {
            if (c == '[')
                bracketCount++;
            if (c == ']')
                bracketCount--;
            if (bracketCount < 0)
                return false;
        }

        return bracketCount == 0;
    }

}
