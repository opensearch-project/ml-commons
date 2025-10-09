/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.utils.StringUtils.validateSchema;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class IndexUtils {

    /**
     * Default settings for index creation with a single shard and one replica.
     * - Sets the number of shards to 1 for better performance in small indices.
     * - Uses auto-expand replicas (0-1) to ensure high availability while minimizing resource usage.
     */
    public static final Map<String, Object> DEFAULT_INDEX_SETTINGS = Map
        .of("index.number_of_shards", "1", "index.auto_expand_replicas", "0-1");
    /**
     * Default settings for index creation with replicas on all nodes.
     * - Sets the number of shards to 1 for better performance in small indices.
     * - Uses auto-expand replicas (0-all) to ensure a replica on every node, maximizing availability.
     * - Caution: This can significantly increase storage requirements and indexing load.
     * - Suitable for small, critical indices where maximum redundancy is required.
     */
    public static final Map<String, Object> ALL_NODES_REPLICA_INDEX_SETTINGS = Map
        .of("index.number_of_shards", "1", "index.auto_expand_replicas", "0-all");

    // Note: This does not include static settings like number of shards, which can't be changed after index creation.
    public static final Map<String, Object> UPDATED_DEFAULT_INDEX_SETTINGS = Map.of("index.auto_expand_replicas", "0-1");
    public static final Map<String, Object> UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS = Map.of("index.auto_expand_replicas", "0-all");

    // Schema that validates system index mappings
    public static final String MAPPING_SCHEMA_PATH = "index-mappings/schema.json";

    // Placeholders to use within the json mapping files
    private static final String USER_PLACEHOLDER = "USER_MAPPING_PLACEHOLDER";
    private static final String CONNECTOR_PLACEHOLDER = "CONNECTOR_MAPPING_PLACEHOLDER";
    public static final Map<String, String> MAPPING_PLACEHOLDERS = Map
        .of(USER_PLACEHOLDER, "index-mappings/placeholders/user.json", CONNECTOR_PLACEHOLDER, "index-mappings/placeholders/connector.json");

    /**
     * Loads a resource file from the classpath as a String.
     * This is a utility method for loading JSON or text resources.
     *
     * @param path The path to the resource file relative to the classpath root
     * @param resourceType A descriptive name for the resource type (e.g., "mapping", "schema") for error messages
     * @return The resource content as a trimmed String
     * @throws IOException if the resource cannot be found or loaded
     * @throws IllegalArgumentException if the resource is empty
     */
    public static String loadResourceFromFile(String path, String resourceType) throws IOException {
        URL url = IndexUtils.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IOException(resourceType + " resource not found: " + path);
        }

        String content = Resources.toString(url, Charsets.UTF_8).trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Empty " + resourceType + " found at: " + path);
        }

        return content;
    }

    public static String getMappingFromFile(String path) throws IOException {
        String mapping = loadResourceFromFile(path, "Mapping");

        mapping = replacePlaceholders(mapping);
        validateMapping(mapping);

        return mapping;
    }

    public static String replacePlaceholders(String mapping) throws IOException {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Mapping cannot be null or empty");
        }

        // Preload resources into memory to avoid redundant I/O
        Map<String, String> loadedPlaceholders = new HashMap<>();
        for (Map.Entry<String, String> placeholder : MAPPING_PLACEHOLDERS.entrySet()) {
            URL url = IndexUtils.class.getClassLoader().getResource(placeholder.getValue());
            if (url == null) {
                throw new IOException("Resource not found: " + placeholder.getValue());
            }

            loadedPlaceholders.put(placeholder.getKey(), Resources.toString(url, Charsets.UTF_8));
        }

        StringBuilder result = new StringBuilder(mapping);
        for (Map.Entry<String, String> entry : loadedPlaceholders.entrySet()) {
            String placeholder = entry.getKey();
            String replacement = entry.getValue();

            // Replace all occurrences of the placeholder
            int index;
            while ((index = result.indexOf(placeholder)) != -1) {
                result.replace(index, index + placeholder.length(), replacement);
            }
        }

        return result.toString();
    }

    /**
     * Checks if the provided mapping is a valid JSON and validates it against a schema.
     *
     * <p>The schema is located at <code>mappings/schema.json</code> and enforces the following validations:</p>
     *
     * <ul>
     *   <li>Mandatory fields:
     *     <ul>
     *       <li><code>_meta</code></li>
     *       <li><code>_meta.schema_version</code></li>
     *       <li><code>properties</code></li>
     *     </ul>
     *   </li>
     *   <li>No additional fields are allowed at the root level.</li>
     *   <li>No additional fields are allowed in the <code>_meta</code> object.</li>
     *   <li><code>properties</code> must be an object type.</li>
     *   <li><code>_meta</code> must be an object type.</li>
     *   <li><code>_meta.schema_version</code> must be an integer.</li>
     * </ul>
     *
     * <p><strong>Note:</strong> Validation can be made stricter if a specific schema is defined for each index.</p>
     */
    public static void validateMapping(String mapping) throws IOException {
        if (mapping.isBlank() || !StringUtils.isJson(mapping)) {
            throw new IllegalArgumentException("Invalid or non-JSON mapping found: " + mapping);
        }

        URL url = IndexUtils.class.getClassLoader().getResource(MAPPING_SCHEMA_PATH);
        if (url == null) {
            throw new IOException("Resource not found: " + MAPPING_SCHEMA_PATH);
        }

        String schema = Resources.toString(url, Charsets.UTF_8);
        validateSchema(schema, mapping);
    }

    public static Integer getVersionFromMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Mapping cannot be null or empty");
        }

        JsonObject mappingJson = StringUtils.getJsonObjectFromString(mapping);
        if (mappingJson == null || !mappingJson.has("_meta")) {
            throw new JsonParseException("Failed to find \"_meta\" object in mapping: " + mapping);
        }

        JsonObject metaObject = mappingJson.getAsJsonObject("_meta");
        if (metaObject == null || !metaObject.has("schema_version")) {
            throw new JsonParseException("Failed to find \"schema_version\" in \"_meta\" object for mapping: " + mapping);
        }

        try {
            return metaObject.get("schema_version").getAsInt();
        } catch (NumberFormatException | ClassCastException e) {
            throw new JsonParseException("Invalid \"schema_version\" value in mapping: " + mapping, e);
        }
    }
}
