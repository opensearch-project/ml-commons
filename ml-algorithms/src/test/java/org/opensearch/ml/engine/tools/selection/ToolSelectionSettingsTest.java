/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT;

import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSelectionConfig;

public class ToolSelectionSettingsTest {

    static final Set<Setting<?>> TOOL_SELECTION_SETTINGS = Set
        .of(ML_COMMONS_TOOL_SELECTION_ENABLED, ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID, ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT);

    private static final MLToolSelectionConfig OPTED_IN = MLToolSelectionConfig.builder().enabled(true).build();

    private ClusterService clusterServiceWith(Settings settings) {
        return clusterServiceWith(new ClusterSettings(settings, TOOL_SELECTION_SETTINGS));
    }

    private ClusterService clusterServiceWith(ClusterSettings clusterSettings) {
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        return clusterService;
    }

    private static Settings clusterOn(String embeddingModelId) {
        return Settings
            .builder()
            .put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), true)
            .put(ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID.getKey(), embeddingModelId)
            .build();
    }

    static MLAgent agentWith(MLToolSelectionConfig toolSelection) {
        return MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("llm-model").build())
            .toolSelection(toolSelection)
            .build();
    }

    private ToolSelectionSettings resolve(Settings cluster, MLToolSelectionConfig agentConfig) {
        return ToolSelectionSettings.resolve(clusterServiceWith(cluster), agentWith(agentConfig));
    }

    @Test
    public void testOffByDefaultWithBuiltInDefaults() {
        ToolSelectionSettings resolved = resolve(Settings.EMPTY, null);
        assertFalse(resolved.isEnabled());
        assertEquals("", resolved.getEmbeddingModelId());
        assertEquals(List.of(), resolved.getPinnedTools());
        assertEquals(5, resolved.getTopK());
        assertEquals(10, resolved.getMinPoolSize());
        assertEquals(15, resolved.getMaxDynamicTools());
        assertEquals(5000L, resolved.getSearchTimeout().millis());
    }

    @Test
    public void testOnWhenClusterSwitchModelAndAgentOptInAllHold() {
        assertTrue(resolve(clusterOn("embed-model"), OPTED_IN).isEnabled());
    }

    @Test
    public void testAgentThatDidNotOptInIsOffEvenWithTheClusterSwitchOn() {
        assertFalse(resolve(clusterOn("embed-model"), null).isEnabled());
        assertFalse(resolve(clusterOn("embed-model"), MLToolSelectionConfig.builder().pinnedTools(List.of("a")).build()).isEnabled());
    }

    @Test
    public void testAgentThatExplicitlyOptsOutIsOff() {
        assertFalse(resolve(clusterOn("embed-model"), MLToolSelectionConfig.builder().enabled(false).build()).isEnabled());
    }

    @Test
    public void testClusterSwitchOffOverridesAgentOptIn() {
        Settings switchOff = Settings
            .builder()
            .put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), false)
            .put(ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID.getKey(), "embed-model")
            .build();
        assertFalse(resolve(switchOff, OPTED_IN).isEnabled());
    }

    @Test
    public void testOffWithoutAnyEmbeddingModel() {
        assertFalse(resolve(Settings.builder().put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), true).build(), OPTED_IN).isEnabled());
    }

    @Test
    public void testNullAgentIsOff() {
        assertFalse(ToolSelectionSettings.resolve(clusterServiceWith(clusterOn("embed-model")), null).isEnabled());
    }

    @Test
    public void testClusterSwitchIsReadOnEveryCall() {
        ClusterSettings clusterSettings = new ClusterSettings(clusterOn("embed-model"), TOOL_SELECTION_SETTINGS);
        ClusterService clusterService = clusterServiceWith(clusterSettings);
        MLAgent agent = agentWith(OPTED_IN);
        assertTrue(ToolSelectionSettings.resolve(clusterService, agent).isEnabled());

        clusterSettings.applySettings(Settings.builder().put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), false).build());

        assertFalse(ToolSelectionSettings.resolve(clusterService, agent).isEnabled());
    }

    @Test
    public void testAgentModelIsUsedWhenTheClusterHasNone() {
        Settings switchOnly = Settings.builder().put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), true).build();
        ToolSelectionSettings resolved = resolve(
            switchOnly,
            MLToolSelectionConfig.builder().enabled(true).embeddingModelId("agent-model").build()
        );
        assertTrue(resolved.isEnabled());
        assertEquals("agent-model", resolved.getEmbeddingModelId());
    }

    @Test
    public void testAgentModelOverridesTheClusterDefault() {
        ToolSelectionSettings resolved = resolve(
            clusterOn("cluster-model"),
            MLToolSelectionConfig.builder().enabled(true).embeddingModelId("agent-model").build()
        );
        assertEquals("agent-model", resolved.getEmbeddingModelId());
    }

    @Test
    public void testClusterModelIsTheDefault() {
        assertEquals("cluster-model", resolve(clusterOn("cluster-model"), OPTED_IN).getEmbeddingModelId());
    }

    @Test
    public void testAgentValuesAreApplied() {
        MLToolSelectionConfig config = MLToolSelectionConfig
            .builder()
            .enabled(true)
            .pinnedTools(List.of("SearchIndexTool", "MyTeamTool"))
            .topK(3)
            .minPoolSize(20)
            .maxDynamicTools(6)
            .build();
        ToolSelectionSettings resolved = resolve(clusterOn("embed-model"), config);
        assertEquals(List.of("SearchIndexTool", "MyTeamTool"), resolved.getPinnedTools());
        assertEquals(3, resolved.getTopK());
        assertEquals(20, resolved.getMinPoolSize());
        assertEquals(6, resolved.getMaxDynamicTools());
    }

    @Test
    public void testUnsetAgentValuesFallBackToBuiltInDefaults() {
        ToolSelectionSettings resolved = resolve(clusterOn("embed-model"), MLToolSelectionConfig.builder().enabled(true).topK(3).build());
        assertEquals(3, resolved.getTopK());
        assertEquals(List.of(), resolved.getPinnedTools());
        assertEquals(10, resolved.getMinPoolSize());
        assertEquals(15, resolved.getMaxDynamicTools());
    }

    @Test
    public void testSearchTimeoutAlwaysComesFromTheCluster() {
        Settings cluster = Settings
            .builder()
            .put(clusterOn("embed-model"))
            .put(ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT.getKey(), "2s")
            .build();
        assertEquals(2000L, resolve(cluster, OPTED_IN).getSearchTimeout().millis());
    }
}
