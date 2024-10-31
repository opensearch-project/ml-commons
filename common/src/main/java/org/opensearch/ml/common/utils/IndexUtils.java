/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.utils.StringUtils.validateSchema;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

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

    public static String getMappingFromFile(String path) throws IOException {
        URL url = IndexUtils.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IOException("Resource not found: " + path);
        }

        String mapping = Resources.toString(url, Charsets.UTF_8);
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Empty mapping found at: " + path);
        }

        mapping = replacePlaceholders(mapping);
        validateMapping(mapping);

        return mapping;
    }

    public static String replacePlaceholders(String mapping) throws IOException {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Mapping cannot be null or empty");
        }

        for (Map.Entry<String, String> placeholder : MAPPING_PLACEHOLDERS.entrySet()) {
            URL url = IndexUtils.class.getClassLoader().getResource(placeholder.getValue());
            if (url == null) {
                throw new IOException("Resource not found: " + placeholder.getValue());
            }

            String placeholderMapping = Resources.toString(url, Charsets.UTF_8);
            mapping = mapping.replace(placeholder.getKey(), placeholderMapping);
        }

        return mapping;
    }

    /*
        - Checks if mapping is a valid json
        - Validates mapping against a schema found in mappings/schema.json
        - Schema validates the following:
            - Below fields are present:
                - "_meta"
                    - "_meta.schema_version"
                - "properties"
            - No additional fields at root level
            - No additional fields in "_meta" object
            - "properties" is an object type
            - "_meta" is an object type
            - "_meta_.schema_version" provided type is integer
    
        Note: Validation can be made more strict if a specific schema is defined for each index.
     */
    public static void validateMapping(String mapping) throws IOException {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Mapping cannot be null or empty");
        }

        if (!StringUtils.isJson(mapping)) {
            throw new JsonSyntaxException("Mapping is not a valid JSON: " + mapping);
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

        return metaObject.get("schema_version").getAsInt();
    }
}
