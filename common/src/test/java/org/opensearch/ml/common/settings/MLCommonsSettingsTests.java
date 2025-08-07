/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;

public class MLCommonsSettingsTests {

    @Test
    public void testMaxModelsPerNodeDefaultValue() {
        assertEquals(10, MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE.getDefault(null).intValue());
    }

    @Test
    public void testOnlyRunOnMLNodeDefaultValue() {
        assertTrue(MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE.getDefault(null));
    }

    @Test
    public void testInHousePythonModelDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL.getDefault(null));
    }

    @Test
    public void testDiskFreeSpaceThresholdDefault() {
        ByteSizeValue expected = new ByteSizeValue(5L, ByteSizeUnit.GB);
        assertEquals(expected, MLCommonsSettings.ML_COMMONS_DISK_FREE_SPACE_THRESHOLD.getDefault(null));
    }

    @Test
    public void testTrustedUrlRegexDefault() {
        String expectedRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        assertEquals(expectedRegex, MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX.getDefault(null));
    }

    @Test
    public void testRemoteModelEligibleNodeRolesDefault() {
        List<String> expected = List.of("data", "ml");
        assertEquals(expected, MLCommonsSettings.ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES.getDefault(null));
    }

    @Test
    public void testLocalModelEligibleNodeRolesDefault() {
        List<String> expected = List.of("data", "ml");
        assertEquals(expected, MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES.getDefault(null));
    }

    @Test
    public void testMultiTenancyDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED.getDefault(null));
    }

    @Test
    public void testRemoteInferenceEnabledByDefault() {
        assertTrue(MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED.getDefault(null));
    }

    @Test
    public void testAllowModelUrlDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL.getDefault(null));
    }

    @Test
    public void testAgenticMemoryDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED.getDefault(null));
    }

    @Test
    public void testAgenticMemorySettingProperties() {
        // Test setting key
        assertEquals("plugins.ml_commons.agentic_memory_enabled", MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED.getKey());

        // Test setting is dynamic
        assertTrue(
            MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.Dynamic)
        );

        // Test setting is node scope
        assertTrue(
            MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.NodeScope)
        );
    }

    @Test
    public void testAgenticMemoryDisabledMessage() {
        String expectedMessage =
            "The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled";
        assertEquals(expectedMessage, MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE);
    }
}
