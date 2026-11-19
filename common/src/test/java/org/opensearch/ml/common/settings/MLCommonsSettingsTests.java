/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
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

    /**
     * Vertex AI publishes both a regional host ({@code us-central1-aiplatform.googleapis.com}) and a global
     * one ({@code aiplatform.googleapis.com}). The regional form joins the region with a hyphen rather than a
     * dot, so a dot-separated subdomain pattern does not cover it.
     */
    private static boolean matchesAnyDefaultTrustedEndpoint(String url) {
        return MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getDefault(null).stream().anyMatch(url::matches);
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsAllowVoyageApi() {
        assertTrue(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com/v1/embeddings"));
        assertTrue(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com/v1/rerank"));
        assertTrue(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com/v1/contextualizedembeddings"));
        assertTrue(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com/v1/multimodalembeddings"));
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsRejectVoyageLookalikeHosts() {
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com.attacker.example/v1/embeddings"));
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://api.voyageai.com@attacker.example/v1/embeddings"));
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://evil-api.voyageai.com/v1/embeddings"));
        assertFalse(matchesAnyDefaultTrustedEndpoint("http://api.voyageai.com/v1/embeddings"));
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsAllowRegionalVertexAi() {
        assertTrue(
            matchesAnyDefaultTrustedEndpoint(
                "https://us-central1-aiplatform.googleapis.com/v1/projects/my-project/locations/us-central1"
                    + "/publishers/google/models/gemini-2.0-flash:generateContent"
            )
        );
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsAllowGlobalVertexAi() {
        assertTrue(
            matchesAnyDefaultTrustedEndpoint(
                "https://aiplatform.googleapis.com/v1/projects/my-project/locations/global"
                    + "/publishers/google/models/gemini-2.0-flash:generateContent"
            )
        );
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsRejectVertexAiHostSuffixSpoofing() {
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://us-central1-aiplatform.googleapis.com.evil.example/v1/predict"));
    }

    @Test
    public void testTrustedConnectorEndpointsRegexDefaultsRejectMalformedVertexAiRegionPrefix() {
        // A DNS label cannot start with a hyphen, so the region prefix must not either. These cannot
        // resolve, but the allowlist should describe only hostnames that are actually well formed.
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://--aiplatform.googleapis.com/v1/predict"));
        assertFalse(matchesAnyDefaultTrustedEndpoint("https://-us-aiplatform.googleapis.com/v1/predict"));
    }

    @Test
    public void testDynamicBatchingMemoryFractionAcceptsMaximum() {
        double value = MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION.getKey(), 0.1).build());
        assertEquals(0.1, value, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDynamicBatchingMemoryFractionRejectsAboveMaximum() {
        MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION.getKey(), 0.11).build());
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

    @Test
    public void testAuthorityWildcardWarnsForPermissivePatternsOnly() {
        RecordingAppender appender = new RecordingAppender();
        appender.start();
        Logger named = (Logger) LogManager.getLogger(MLCommonsSettings.class);
        Level previousLevel = named.getLevel();
        Configurator.setLevel(MLCommonsSettings.class.getName(), Level.WARN);
        named.addAppender(appender);
        try {
            assertTrue(MLCommonsSettings.authorityContainsWildcard(".*amazonaws\\.com"));
            assertTrue(
                MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart("^https://.*\\.internal\\.example/.*$"))
            );
            assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock-runtime\\.[^/]+\\.amazonaws"));
            assertFalse(MLCommonsSettings.authorityContainsWildcard("runtime\\.sagemaker\\.[a-z0-9-]+\\.amazonaws\\.com"));
            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
                .get(
                    Settings
                        .builder()
                        .putList(
                            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(),
                            List.of("^https://.*amazonaws\\.com/.*$", "^https://bedrock-runtime\\.[^/]+\\.amazonaws\\.com/.*$")
                        )
                        .build()
                );
            assertTrue(appender.messages.toString(), appender.messages.stream().anyMatch(message -> message.contains("amazonaws")));
            assertTrue(appender.messages.toString(), appender.messages.stream().anyMatch(message -> message.contains("[^/]")));
            appender.messages.clear();
            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
                .get(
                    Settings
                        .builder()
                        .putList(
                            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(),
                            MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY)
                        )
                        .build()
                );
            assertTrue(appender.messages.isEmpty());
        } finally {
            named.removeAppender(appender);
            Configurator.setLevel(MLCommonsSettings.class.getName(), previousLevel);
            appender.stop();
        }
    }

    @Test
    public void testAuthorityWildcardHeuristicCases() {
        // flagged: patterns without "://" are all host position
        assertTrue(MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart("^.*$")));
        assertTrue(MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart("^https.*amazonaws\\.com.*$")));
        // flagged: broad escapes, inside or outside a class
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.\\S+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.\\W+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.\\D+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.\\p{Print}+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.\\P{Alpha}+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[\\S]+\\.amazonaws\\.com"));
        // flagged: negated classes that do not exclude all of '/', '?', '#'
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^/]+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^ ]+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^.]+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^:]+\\.amazonaws\\.com"));
        assertTrue(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^/?]+\\.amazonaws\\.com"));
        // not flagged
        assertFalse(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[^/?#]+\\.amazonaws\\.com"));
        assertFalse(MLCommonsSettings.authorityContainsWildcard("bedrock\\.[a-z0-9-]+\\.amazonaws\\.com"));
        assertFalse(MLCommonsSettings.authorityContainsWildcard("host\\.example\\.com(:\\d+)?"));
        assertFalse(MLCommonsSettings.authorityContainsWildcard("([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com"));
        // the authority stops at the first '/' outside a class
        assertEquals("api\\.openai\\.com", MLCommonsSettings.authorityPart("^https://api\\.openai\\.com/.*$"));
    }

    @Test
    public void testShippedDefaultsHaveNoAuthorityWildcard() {
        for (String regex : MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY)) {
            assertFalse(regex, MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart(regex)));
        }
        for (String regex : MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_PRIVATE_ENDPOINTS_REGEX.get(Settings.EMPTY)) {
            assertFalse(regex, MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart(regex)));
        }
    }

    private static final class RecordingAppender extends AbstractAppender {
        private final List<String> messages = new java.util.ArrayList<>();

        private RecordingAppender() {
            super("authority-wildcard-test", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }

    // A zero or negative bound would clamp the dynamic batching queue's memory budget to nothing, rejecting every queued
    // predict request for the life of the node with a 429 that no backoff could clear.
    @Test(expected = IllegalArgumentException.class)
    public void testDynamicBatchingMemoryCeilingRejectsNegativeValue() {
        MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.getKey(), "-1b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDynamicBatchingMemoryCeilingRejectsZero() {
        MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.getKey(), "0b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDynamicBatchingMemoryFloorRejectsNegativeValue() {
        MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN.getKey(), "-1b").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDynamicBatchingMemoryFloorRejectsZero() {
        MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN.getKey(), "0b").build());
    }

    @Test
    public void testDynamicBatchingMemoryBoundsAcceptPositiveValues() {
        ByteSizeValue ceiling = MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.getKey(), "1gb").build());
        assertEquals(new ByteSizeValue(1L, ByteSizeUnit.GB), ceiling);

        ByteSizeValue floor = MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN
            .get(Settings.builder().put(MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN.getKey(), "32mb").build());
        assertEquals(new ByteSizeValue(32L, ByteSizeUnit.MB), floor);
    }
}
