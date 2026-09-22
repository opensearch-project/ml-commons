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
import org.opensearch.common.unit.TimeValue;
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
    public void testUnifiedAgentApiEnabledByDefault() {
        assertTrue(MLCommonsSettings.ML_COMMONS_UNIFIED_AGENT_API_ENABLED.getDefault(null));
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

    @Test
    public void testBatchQueueMemoryFractionAcceptsMaximum() {
        double value = MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION.getKey(), 0.1).build());
        assertEquals(0.1, value, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueMemoryFractionRejectsAboveMaximum() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION.getKey(), 0.11).build());
    }

    @Test
    public void testValidateRegexSafety_optionalGroupWithInnerQuantifier_success() {
        List<String> validRegex = List.of("^https?://api\\.example\\.com(:\\d+)?/.*$");
        List<String> result = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), validRegex).build());
        assertEquals(validRegex, result);
    }

    @Test
    public void testValidateRegexSafety_boundedQuantifier_success() {
        List<String> validRegex = List.of("^https?://api\\.example\\.com(:\\d{1,5})?/.*$");
        List<String> result = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), validRegex).build());
        assertEquals(validRegex, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_nestedUnboundedQuantifier_throwsException() {
        List<String> invalidRegex = List.of("^https://host(:\\d+)*/.*$");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex).build());
    }

    /**
     * A counted repetition of a group that already contains a quantifier backtracks exponentially, so it must
     * be rejected. This is the shape {@link #testValidateRegexSafety_boundedQuantifier_success()} does not
     * cover: there the "{1,5}" sits *inside* the group, which is harmless, so that test passes either way.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_countedRepetitionOfQuantifiedGroup_throwsException() {
        List<String> invalidRegex = List.of("^https://host(a+){1,1000}/.*$");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateRegexSafety_openEndedRepetitionOfQuantifiedGroup_throwsException() {
        List<String> invalidRegex = List.of("^https://host(a+){1,}/.*$");
        MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), invalidRegex).build());
    }

    /**
     * The default trusted-endpoint patterns are validated by the same validator, so a tightening of the guard
     * must not reject any of them.
     */
    @Test
    public void testValidateRegexSafety_defaultTrustedEndpointPatterns_success() {
        List<String> defaults = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY);
        List<String> result = MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
            .get(Settings.builder().putList(MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), defaults).build());
        assertEquals(defaults, result);
    }

    // A zero or negative bound would clamp the batch queue's memory budget to nothing, rejecting every queued
    // predict request for the life of the node with a 429 that no backoff could clear.
    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueMemoryCeilingRejectsNegativeValue() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING.getKey(), "-1b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueMemoryCeilingRejectsZero() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING.getKey(), "0b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueMemoryFloorRejectsNegativeValue() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR.getKey(), "-1b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueMemoryFloorRejectsZero() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR.getKey(), "0b").build());
    }

    @Test
    public void testBatchQueueMemoryBoundsAcceptPositiveValues() {
        ByteSizeValue ceiling = MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING.getKey(), "1gb").build());
        assertEquals(new ByteSizeValue(1L, ByteSizeUnit.GB), ceiling);

        ByteSizeValue floor = MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR.getKey(), "32mb").build());
        assertEquals(new ByteSizeValue(32L, ByteSizeUnit.MB), floor);
    }

    // A zero TTL makes every sweep evict every queue, so no queue survives long enough to coalesce anything.
    @Test(expected = IllegalArgumentException.class)
    public void testBatchQueueIdleTtlRejectsZero() {
        MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL.getKey(), "0s").build());
    }

    @Test
    public void testBatchQueueIdleTtlAcceptsOneSecond() {
        assertEquals(
            TimeValue.timeValueSeconds(1),
            MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL
                .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL.getKey(), "1s").build())
        );
    }
}
