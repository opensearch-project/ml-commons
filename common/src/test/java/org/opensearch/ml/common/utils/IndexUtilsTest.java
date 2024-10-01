/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

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
}
