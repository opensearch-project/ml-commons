/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

public class IndexUtilsTest {

    @Test
    public void testDefaultIndexSettingsContainsExpectedValues() {
        Map<String, Object> indexSettings = IndexUtils.DEFAULT_INDEX_SETTINGS;
        assertEquals("index.number_of_shards should be 1", indexSettings.get("index.number_of_shards"), "1");
        assertEquals("index.auto_expand_replicas should be 0-1", indexSettings.get("index.auto_expand_replicas"), "0-1");
        assertEquals("INDEX_SETTINGS should contain exactly 2 settings", 2, indexSettings.size());
    }

    @Test
    public void testAllNodesReplicaIndexSettingsContainsExpectedValues() {
        Map<String, Object> indexSettings = IndexUtils.ALL_NODES_REPLICA_INDEX_SETTINGS;
        assertEquals("index.number_of_shards should be 1", indexSettings.get("index.number_of_shards"), "1");
        assertEquals("index.auto_expand_replicas should be 0-all", indexSettings.get("index.auto_expand_replicas"), "0-all");
        assertEquals("INDEX_SETTINGS should contain exactly 2 settings", 2, indexSettings.size());
    }

    @Test
    public void testUpdatedDefaultIndexSettingsContainsExpectedValues() {
        Map<String, Object> updatedIndexSettings = IndexUtils.UPDATED_DEFAULT_INDEX_SETTINGS;
        assertEquals("index.auto_expand_replicas should be 0-1", updatedIndexSettings.get("index.auto_expand_replicas"), "0-1");
        assertEquals("INDEX_SETTINGS should contain exactly 1 settings", 1, updatedIndexSettings.size());
    }

    @Test
    public void testUpdatedAllNodesReplicaIndexSettingsContainsExpectedValues() {
        Map<String, Object> updatedIndexSettings = IndexUtils.UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS;
        assertEquals("index.auto_expand_replicas should be 0-all", updatedIndexSettings.get("index.auto_expand_replicas"), "0-all");
        assertEquals("INDEX_SETTINGS should contain exactly 1 settings", 1, updatedIndexSettings.size());
    }

    @Test
    public void testGetMappingFromFile() {
        String expectedMapping = "{\n"
            + "  \"_meta\": {\n"
            + "    \"schema_version\": \"1\"\n"
            + "  },\n"
            + "  \"properties\": {\n"
            + "    \"test_field_1\": {\n"
            + "      \"type\": \"test_type_1\"\n"
            + "    },\n"
            + "    \"test_field_2\": {\n"
            + "      \"type\": \"test_type_2\"\n"
            + "    },\n"
            + "    \"test_field_3\": {\n"
            + "      \"type\": \"test_type_3\"\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
        try {
            String actualMapping = IndexUtils.getMappingFromFile("mappings/test-mapping.json");
            assertEquals(expectedMapping, actualMapping);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file at path: mappings/test-mapping.json");
        }
    }

    @Test
    public void testGetMappingFromFileFileNotFound() {
        String path = "mappings/test-mapping-not-found.json";
        IOException e = assertThrows(IOException.class, () -> IndexUtils.getMappingFromFile(path));
        assertEquals("Resource not found: " + path, e.getMessage());
    }

    @Test
    public void testGetMappingFromFilesMalformedJson() {
        String path = "mappings/test-mapping-malformed.json";
        JsonSyntaxException e = assertThrows(JsonSyntaxException.class, () -> IndexUtils.getMappingFromFile(path));
        assertEquals("Mapping is not a valid JSON: " + path, e.getMessage());
    }

    @Test
    public void testGetVersionFromMapping() {
        Integer expectedVersion = 1;
        String mapping = "{\n"
            + "  \"_meta\": {\n"
            + "    \"schema_version\": \"1\"\n"
            + "  },\n"
            + "  \"properties\": {\n"
            + "    \"test_field_1\": {\n"
            + "      \"type\": \"test_type_1\"\n"
            + "    },\n"
            + "    \"test_field_2\": {\n"
            + "      \"type\": \"test_type_2\"\n"
            + "    },\n"
            + "    \"test_field_3\": {\n"
            + "      \"type\": \"test_type_3\"\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

        assertEquals(expectedVersion, IndexUtils.getVersionFromMapping(mapping));
    }

    @Test
    public void testGetVersionFromMappingNoMeta() {
        String mapping = "{\n"
            + "  \"properties\": {\n"
            + "    \"test_field_1\": {\n"
            + "      \"type\": \"test_type_1\"\n"
            + "    },\n"
            + "    \"test_field_2\": {\n"
            + "      \"type\": \"test_type_2\"\n"
            + "    },\n"
            + "    \"test_field_3\": {\n"
            + "      \"type\": \"test_type_3\"\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

        JsonParseException e = assertThrows(JsonParseException.class, () -> IndexUtils.getVersionFromMapping(mapping));
        assertEquals("Failed to find \"_meta\" object in mapping: " + mapping, e.getMessage());
    }

    @Test
    public void testGetVersionFromMappingNoSchemaVersion() {
        String mapping = "{\n"
            + "  \"_meta\": {\n"
            + "  },\n"
            + "  \"properties\": {\n"
            + "    \"test_field_1\": {\n"
            + "      \"type\": \"test_type_1\"\n"
            + "    },\n"
            + "    \"test_field_2\": {\n"
            + "      \"type\": \"test_type_2\"\n"
            + "    },\n"
            + "    \"test_field_3\": {\n"
            + "      \"type\": \"test_type_3\"\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

        JsonParseException e = assertThrows(JsonParseException.class, () -> IndexUtils.getVersionFromMapping(mapping));
        assertEquals("Failed to find \"schema_version\" in \"_meta\" object for mapping: " + mapping, e.getMessage());
    }
}
