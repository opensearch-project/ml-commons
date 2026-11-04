/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_ENABLED;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLToolSelectionConfig;
import org.opensearch.ml.common.spi.tools.Tool;

public class DynamicToolSelectorTest {

    private ToolEmbeddingStore embeddingStore;
    private DynamicToolSelector selector;

    @Before
    public void setUp() {
        embeddingStore = mock(ToolEmbeddingStore.class);
        selector = new DynamicToolSelector(embeddingStore);
    }

    /** Builds a real ToolSelectionSettings via resolve(), rather than mocking a value object. */
    static ToolSelectionSettings settings(boolean enabled, int minPoolSize, int topK, String... pinned) {
        return settings(enabled, minPoolSize, topK, ToolSelectionSettings.DEFAULT_MAX_DYNAMIC_TOOLS, pinned);
    }

    /** {@code enabled} sets both the cluster switch (with a default model) and the agent's opt-in. */
    static ToolSelectionSettings settings(boolean enabled, int minPoolSize, int topK, int maxDynamicTools, String... pinned) {
        Settings.Builder cluster = Settings.builder().put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), enabled);
        if (enabled) {
            cluster.put(ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID.getKey(), "embed-model");
        }
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(cluster.build(), ToolSelectionSettingsTest.TOOL_SELECTION_SETTINGS));
        MLToolSelectionConfig agentConfig = MLToolSelectionConfig
            .builder()
            .enabled(enabled)
            .pinnedTools(List.of(pinned))
            .topK(topK)
            .minPoolSize(minPoolSize)
            .maxDynamicTools(maxDynamicTools)
            .build();
        return ToolSelectionSettings.resolve(clusterService, ToolSelectionSettingsTest.agentWith(agentConfig));
    }

    static Tool tool(String description) {
        Tool tool = mock(Tool.class);
        when(tool.getDescription()).thenReturn(description);
        return tool;
    }

    static Map<String, Tool> pool(int size) {
        Map<String, Tool> tools = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            tools.put("tool" + i, tool("description " + i));
        }
        return tools;
    }

    private void stubQueryVector(float[] vector) {
        doAnswer(invocation -> {
            ActionListener<float[]> listener = invocation.getArgument(3);
            listener.onResponse(vector);
            return null;
        }).when(embeddingStore).embedQuestion(anyString(), anyString(), any(), any());
    }

    private void stubToolVectors(Map<String, float[]> vectors) {
        doAnswer(invocation -> {
            ActionListener<Map<String, float[]>> listener = invocation.getArgument(3);
            listener.onResponse(vectors);
            return null;
        }).when(embeddingStore).getToolEmbeddings(any(), anyString(), any(), any());
    }

    private List<String> search(String query, Map<String, Tool> candidates, ToolSelectionSettings s, String tenantId) {
        AtomicReference<List<String>> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        selector.search(query, candidates, s, tenantId, ActionListener.wrap(result::set, failure::set));
        if (failure.get() != null) {
            throw new AssertionError("unexpected failure", failure.get());
        }
        return result.get();
    }

    @Test
    public void testDisabledDoesNotApply() {
        ToolSelectionResult plan = selector.plan(pool(20), settings(false, 10, 5));
        assertFalse(plan.isApplied());
        assertEquals("disabled", plan.getSkipReason());
        assertEquals(20, plan.getPoolSize());
        verifyNoInteractions(embeddingStore);
    }

    @Test
    public void testNullSettingsDoesNotApply() {
        assertEquals("disabled", selector.plan(pool(20), null).getSkipReason());
    }

    @Test
    public void testEmptyOrNullPoolDoesNotApply() {
        assertEquals("no_tools", selector.plan(Map.of(), settings(true, 10, 5)).getSkipReason());
        assertEquals("no_tools", selector.plan(null, settings(true, 10, 5)).getSkipReason());
    }

    @Test
    public void testSmallAgentsSeeEveryTool() {
        assertEquals("pool_within_limit", selector.plan(pool(10), settings(true, 10, 5)).getSkipReason());
    }

    @Test
    public void testOneOverTheLimitApplies() {
        ToolSelectionResult plan = selector.plan(pool(11), settings(true, 10, 5));
        assertTrue(plan.isApplied());
        assertNull(plan.getSkipReason());
        assertEquals(11, plan.getPoolSize());
        assertEquals(11, plan.getSearchableCount());
        verifyNoInteractions(embeddingStore);
    }

    @Test
    public void testPinnedToolsKeepConfiguredOrderAndIgnoreAbsentNames() {
        // Pinned names missing from the agent are ignored; present ones keep the configured order.
        ToolSelectionResult plan = selector.plan(pool(12), settings(true, 10, 5, "tool7", "NotOnThisAgent", "tool2"));
        assertTrue(plan.isApplied());
        assertEquals(List.of("tool7", "tool2"), plan.getPinnedToolNames());
        assertEquals(10, plan.getSearchableCount());
    }

    @Test
    public void testDuplicatePinnedNamesAreCountedOnce() {
        ToolSelectionResult plan = selector.plan(pool(12), settings(true, 10, 5, "tool1", "tool1"));
        assertEquals(List.of("tool1"), plan.getPinnedToolNames());
        assertEquals(11, plan.getSearchableCount());
    }

    @Test
    public void testAllToolsPinnedDoesNotApply() {
        Map<String, Tool> tools = pool(11);
        String[] all = tools.keySet().toArray(new String[0]);
        assertEquals("all_tools_pinned", selector.plan(tools, settings(true, 10, 5, all)).getSkipReason());
    }

    @Test
    public void testAgentToolNamedSearchToolsIsNeverShadowed() {
        Map<String, Tool> tools = pool(11);
        tools.put(SearchToolsTool.NAME, tool("the owner's own tool"));
        assertEquals("name_conflict", selector.plan(tools, settings(true, 10, 5)).getSkipReason());
    }

    @Test
    public void testSearchReturnsTopKMostSimilar() {
        stubQueryVector(new float[] { 1f, 0f });
        Map<String, float[]> vectors = new LinkedHashMap<>();
        vectors.put("tool0", new float[] { 0f, 1f });
        vectors.put("tool1", new float[] { 1f, 0f });
        vectors.put("tool2", new float[] { 0.7f, 0.7f });
        stubToolVectors(vectors);

        assertEquals(List.of("tool1", "tool2"), search("q", pool(3), settings(true, 1, 2), null));
    }

    @Test
    public void testSearchEmbedsTheEffectiveToolDescriptions() {
        // The instantiated Tool's description includes per-agent overrides; the MLToolSpec value does not.
        stubQueryVector(new float[] { 1f, 0f });
        stubToolVectors(Map.of());
        Map<String, Tool> candidates = new LinkedHashMap<>();
        candidates.put("search", tool("Run a search query against an index."));
        search("q", candidates, settings(true, 1, 5), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolDescriptor>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingStore).getToolEmbeddings(captor.capture(), eq("embed-model"), any(), any());
        assertEquals("Run a search query against an index.", captor.getValue().get(0).getDescription());
    }

    @Test
    public void testSearchPassesTenantToBothEmbeddingCalls() {
        stubQueryVector(new float[] { 1f, 0f });
        stubToolVectors(Map.of());
        search("q", pool(3), settings(true, 1, 5), "tenant-1");

        verify(embeddingStore).embedQuestion(eq("q"), eq("embed-model"), eq("tenant-1"), any());
        verify(embeddingStore).getToolEmbeddings(any(), eq("embed-model"), eq("tenant-1"), any());
    }

    @Test
    public void testSearchOverNoCandidatesSkipsTheModel() {
        assertEquals(List.of(), search("q", Map.of(), settings(true, 1, 5), null));
        verifyNoInteractions(embeddingStore);
    }

    @Test
    public void testSearchWithUnusableVectorsReturnsNothingRatherThanFailing() {
        // Stale vectors of another dimension, e.g. after a model change.
        stubQueryVector(new float[] { 1f, 0f });
        stubToolVectors(Map.of("tool0", new float[] { 1f, 0f, 0f }));
        assertEquals(List.of(), search("q", pool(1), settings(true, 1, 5), null));
    }

    @Test
    public void testSearchFailsItsListenerOnQueryEmbeddingFailure() {
        // SearchToolsTool turns this into a "search unavailable" message for the model.
        doAnswer(invocation -> {
            ActionListener<float[]> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("model not deployed"));
            return null;
        }).when(embeddingStore).embedQuestion(anyString(), anyString(), any(), any());

        AtomicReference<Exception> failure = new AtomicReference<>();
        selector.search("q", pool(3), settings(true, 1, 5), null, ActionListener.wrap(r -> {}, failure::set));
        assertEquals("model not deployed", failure.get().getMessage());
        verify(embeddingStore, never()).getToolEmbeddings(any(), anyString(), any(), any());
    }

    @Test
    public void testSearchFailsItsListenerOnToolEmbeddingFailure() {
        stubQueryVector(new float[] { 1f, 0f });
        doAnswer(invocation -> {
            ActionListener<Map<String, float[]>> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("cache unavailable"));
            return null;
        }).when(embeddingStore).getToolEmbeddings(any(), anyString(), any(), any());

        AtomicReference<Exception> failure = new AtomicReference<>();
        selector.search("q", pool(3), settings(true, 1, 5), null, ActionListener.wrap(r -> {}, failure::set));
        assertEquals("cache unavailable", failure.get().getMessage());
    }

    @Test
    public void testWarmUpEmbedsEverySearchableTool() {
        stubToolVectors(Map.of());
        selector.warmUp(pool(4), settings(true, 1, 5), "tenant-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolDescriptor>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingStore).getToolEmbeddings(captor.capture(), eq("embed-model"), eq("tenant-1"), any());
        assertEquals(4, captor.getValue().size());
        verify(embeddingStore, never()).embedQuestion(anyString(), anyString(), any(), any());
    }

    @Test
    public void testWarmUpFailureIsSwallowed() {
        doAnswer(invocation -> {
            ActionListener<Map<String, float[]>> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("cache unavailable"));
            return null;
        }).when(embeddingStore).getToolEmbeddings(any(), anyString(), any(), any());

        selector.warmUp(pool(4), settings(true, 1, 5), null);
    }

    @Test
    public void testWarmUpWithNothingToEmbedIsANoOp() {
        selector.warmUp(Map.of(), settings(true, 1, 5), null);
        selector.warmUp(null, settings(true, 1, 5), null);
        verifyNoInteractions(embeddingStore);
    }

    @Test
    public void testWarmUpNeverThrowsIntoTheRequestPath() {
        doThrow(new IllegalStateException("cluster state unavailable"))
            .when(embeddingStore)
            .getToolEmbeddings(any(), anyString(), any(), any());
        selector.warmUp(pool(4), settings(true, 1, 5), null);
    }
}
