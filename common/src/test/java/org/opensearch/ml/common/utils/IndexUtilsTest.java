/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class IndexUtilsTest {

        @Test
        public void testIndexSettingsContainsExpectedValues() {
            Map<String, Object> indexSettings = IndexUtils.INDEX_SETTINGS;
            assertEquals("index.number_of_shards should be 1", indexSettings.get("index.number_of_shards"), "1");
            assertEquals("index.auto_expand_replicas should be 0-1", indexSettings.get("index.auto_expand_replicas"), "0-1");
            assertEquals("INDEX_SETTINGS should contain exactly 2 settings", 2, indexSettings.size());
        }

        @Test
        public void testUpdatedIndexSettingsContainsExpectedValues() {
            Map<String, Object> updatedIndexSettings = IndexUtils.UPDATED_INDEX_SETTINGS;
            assertEquals("index.auto_expand_replicas should be 0-1", updatedIndexSettings.get("index.auto_expand_replicas"), "0-1");
            assertEquals("INDEX_SETTINGS should contain exactly 1 settings", 1, updatedIndexSettings.size());
        }

}
