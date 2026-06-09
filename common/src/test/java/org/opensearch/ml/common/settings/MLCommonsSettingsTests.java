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
import org.opensearch.common.settings.Settings;
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
        assertTrue(MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED.getDefault(null));
    }

    @Test
    public void testRemoteAgenticMemoryDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_ENABLED.getDefault(null));
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
    public void testRemoteAgenticMemorySettingProperties() {
        assertEquals(
            "plugins.ml_commons.remote_agentic_memory_enabled",
            MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_ENABLED.getKey()
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_ENABLED
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.Dynamic)
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_ENABLED
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

    @Test
    public void testRemoteAgenticMemoryDisabledMessage() {
        String expectedMessage =
            "The remote agentic memory feature is not enabled. To enable, please update the setting plugins.ml_commons.remote_agentic_memory_enabled";
        assertEquals(expectedMessage, MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE);
    }

    @Test
    public void testStreamDisabledByDefault() {
        assertFalse(MLCommonsSettings.ML_COMMONS_STREAM_ENABLED.getDefault(null));
    }

    @Test
    public void testMaxJsonSizeDefaultValue() {
        assertEquals(100_000_000, MLCommonsSettings.ML_COMMONS_MAX_JSON_SIZE.getDefault(null).intValue());
    }

    @Test
    public void testMaxJsonSizeSettingProperties() {
        assertEquals("plugins.ml_commons.max_json_size", MLCommonsSettings.ML_COMMONS_MAX_JSON_SIZE.getKey());

        // Test setting is dynamic
        assertTrue(
            MLCommonsSettings.ML_COMMONS_MAX_JSON_SIZE.getProperties().contains(org.opensearch.common.settings.Setting.Property.Dynamic)
        );

        // Test setting is node scope
        assertTrue(
            MLCommonsSettings.ML_COMMONS_MAX_JSON_SIZE.getProperties().contains(org.opensearch.common.settings.Setting.Property.NodeScope)
        );
    }

    @Test
    public void testTrustedConnectorPrivateEndpointsRegexDefaultValue() {
        List<String> expected = List.of();
        assertEquals(expected, MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX.getDefault(null));
    }

    @Test
    public void testTrustedConnectorPrivateEndpointsRegexSettingProperties() {
        assertEquals(
            "plugins.ml_commons.trusted_connector_private_endpoints_regex",
            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX.getKey()
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.Dynamic)
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.NodeScope)
        );
    }

    @Test
    public void testConnectorRestrictedIpPatternsDefaultValue() {
        List<String> expected = List.of();
        assertEquals(expected, MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS.getDefault(null));
    }

    @Test
    public void testConnectorRestrictedIpPatternsSettingProperties() {
        assertEquals(
            "plugins.ml_commons.connector.restricted_ip_patterns",
            MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS.getKey()
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.Final)
        );

        assertTrue(
            MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS
                .getProperties()
                .contains(org.opensearch.common.settings.Setting.Property.NodeScope)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTrustedConnectorEndpointsRegex_invalidRegex_throwsException() {
        List<String> invalidRegex = List.of("(a+)+b");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(
                org.opensearch.common.settings.Settings
                    .builder()
                    .putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex)
                    .build()
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTrustedConnectorPrivateEndpointsRegex_invalidRegex_throwsException() {
        List<String> invalidRegex = List.of("(a+)+b");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX
            .get(
                org.opensearch.common.settings.Settings
                    .builder()
                    .putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX.getKey(), invalidRegex)
                    .build()
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConnectorRestrictedIpPatterns_invalidRegex_throwsException() {
        List<String> invalidRegex = List.of("(a+)+b");
        MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS
            .get(
                org.opensearch.common.settings.Settings
                    .builder()
                    .putList(MLCommonsSettings.ML_COMMONS_CONNECTOR_RESTRICTED_IP_PATTERNS.getKey(), invalidRegex)
                    .build()
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_backreference_throwsException() {
        List<String> invalidRegex = List.of("(\\w+)\\1");  // backreference
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_tooLong_throwsException() {
        String longRegex = "a".repeat(257);  // > 256 chars
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(
                Settings
                    .builder()
                    .putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), List.of(longRegex))
                    .build()
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_invalidSyntax_throwsException() {
        List<String> invalidRegex = List.of("[unclosed");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex).build());
    }

    @Test
    public void testValidateRegexSafety_validPattern_success() {
        List<String> validRegex = List.of("^https://api\\.openai\\.com/.*$");
        List<String> result = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), validRegex).build());
        assertEquals(validRegex, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_multiplePatterns_oneInvalid() {
        List<String> mixedRegex = List.of("^https://valid\\.com$", "(a+)+b", "^https://another\\.com$");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), mixedRegex).build());
    }

    @Test
    public void testValidateRegexSafety_multipleValidPatterns() {
        List<String> validRegex = List
            .of("^https://api\\.openai\\.com/.*$", "^https://api\\.cohere\\.ai/.*$", "^https://.*\\.amazonaws\\.com/.*$");
        List<String> result = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), validRegex).build());
        assertEquals(validRegex, result);
    }
}
