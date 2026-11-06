/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSelectionConfig;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.tools.selection.SearchToolsTool;
import org.opensearch.ml.engine.tools.selection.ToolSelectionResult;
import org.opensearch.ml.engine.tools.selection.ToolSelectionSettings;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Covers how the runner turns a tool-search plan into the tool map handed to the LLM, and the
 * observability hook alongside it.
 */
public class MLChatAgentRunnerV2ToolSelectionTest {

    private ClusterService clusterService;
    private MLChatAgentRunnerV2 runner;
    private MLAgent agent;

    @Before
    public void setUp() {
        clusterService = mock(ClusterService.class);
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_TOOL_SELECTION_ENABLED.getKey(), true)
            .put(ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID.getKey(), "embed-model")
            .build();
        when(clusterService.getClusterSettings())
            .thenReturn(
                new ClusterSettings(
                    settings,
                    Set
                        .of(
                            ML_COMMONS_TOOL_SELECTION_ENABLED,
                            ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID,
                            ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT
                        )
                )
            );
        Client client = mock(Client.class);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.EMPTY));
        runner = new MLChatAgentRunnerV2(
            client,
            Settings.EMPTY,
            clusterService,
            NamedXContentRegistry.EMPTY,
            Map.of(),
            mock(SdkClient.class),
            mock(Encryptor.class)
        );
        agent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("m").build())
            .toolSelection(MLToolSelectionConfig.builder().enabled(true).pinnedTools(List.of("explain", "search")).minPoolSize(3).build())
            .build();
    }

    private Map<String, Tool> pool() {
        Map<String, Tool> tools = new LinkedHashMap<>();
        for (String name : List.of("search", "msearch", "explain", "mapping", "stats")) {
            Tool tool = mock(Tool.class);
            when(tool.getName()).thenReturn(name);
            when(tool.getDescription()).thenReturn("does " + name);
            tools.put(name, tool);
        }
        return tools;
    }

    private Map<String, Tool> buildPromptTools(Map<String, Tool> tools) {
        return buildPromptTools(tools, List.of());
    }

    private Map<String, Tool> buildPromptTools(Map<String, Tool> tools, List<Message> history) {
        ToolSelectionResult plan = ToolSelectionResult.applied(List.of("explain", "search"), tools.size());
        return runner.pinnedToolsPlusSearch(agent, tools, plan, ToolSelectionSettings.resolve(clusterService, agent), history);
    }

    private static Message assistantCalling(String... toolNames) {
        Message message = new Message("assistant", List.of());
        List<ToolCall> calls = new ArrayList<>();
        for (String name : toolNames) {
            calls.add(new ToolCall("id-" + name, "function", new ToolCall.ToolFunction(name, "{}")));
        }
        message.setToolCalls(calls);
        return message;
    }

    @Test
    public void testToolsCalledInHistoryAreShownAfterThePinnedTools() {
        List<Message> history = List.of(new Message("user", List.of()), assistantCalling("stats"), assistantCalling("mapping"));

        Map<String, Tool> promptTools = buildPromptTools(pool(), history);

        assertEquals(List.of("explain", "search", "mapping", "stats", SearchToolsTool.NAME), new ArrayList<>(promptTools.keySet()));
    }

    @Test
    public void testHistoryToolsAreUnknownOrSearchToolsIgnoredAndLimited() {
        List<Message> history = List
            .of(
                assistantCalling("msearch", "stats"),
                assistantCalling("not_configured", SearchToolsTool.NAME),
                assistantCalling("mapping")
            );

        assertEquals(List.of("mapping", "stats"), MLChatAgentRunnerV2.toolsCalledInHistory(history, pool(), 2));
        assertEquals(List.of(), MLChatAgentRunnerV2.toolsCalledInHistory(null, pool(), 2));
    }

    @Test
    public void testHistoryToolsDoNotCountTowardTheCap() {
        agent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("m").build())
            .toolSelection(
                MLToolSelectionConfig
                    .builder()
                    .enabled(true)
                    .pinnedTools(List.of("explain", "search"))
                    .minPoolSize(3)
                    .maxDynamicTools(1)
                    .build()
            )
            .build();
        Map<String, Tool> promptTools = buildPromptTools(pool(), List.of(assistantCalling("mapping")));
        SearchToolsTool searchTool = (SearchToolsTool) promptTools.get(SearchToolsTool.NAME);

        assertTrue(searchTool.adoptDirectCall("stats"));
        assertTrue(promptTools.containsKey("stats"));
    }

    @Test
    public void testPromptToolsArePinnedInOrderThenSearch() {
        // This prefix never changes during a run; found tools are appended after it.
        Map<String, Tool> promptTools = buildPromptTools(pool());
        assertEquals(List.of("explain", "search", SearchToolsTool.NAME), new ArrayList<>(promptTools.keySet()));
    }

    @Test
    public void testPinnedToolsAreTheSameInstances() {
        Map<String, Tool> tools = pool();
        Map<String, Tool> promptTools = buildPromptTools(tools);
        assertSame(tools.get("explain"), promptTools.get("explain"));
        assertSame(tools.get("search"), promptTools.get("search"));
    }

    @Test
    public void testSearchToolCountsTheHiddenTools() {
        Tool searchTool = buildPromptTools(pool()).get(SearchToolsTool.NAME);
        assertTrue(searchTool instanceof SearchToolsTool);
        assertTrue(searchTool.getDescription().contains("3 more are available"));
    }

    @Test
    public void testPromptToolsIsANewMutableMapAndTheAgentPoolIsUntouched() {
        // Appending to the prompt map must not touch the agent's full tool pool.
        Map<String, Tool> tools = pool();
        Map<String, Tool> promptTools = buildPromptTools(tools);
        assertNotSame(tools, promptTools);
        promptTools.put("mapping", tools.get("mapping"));
        assertEquals(5, tools.size());
        assertFalse(tools.containsKey(SearchToolsTool.NAME));
    }

    @Test
    public void testSetupDoesNotWaitForWarmUp() {
        // The warm-up never completes here; setup must not wait for it.
        assertEquals(3, buildPromptTools(pool()).size());
    }

    @Test
    public void testHiddenToolCalledDirectlyIsAppendedToThePromptTools() {
        Map<String, Tool> promptTools = buildPromptTools(pool());
        runner.adoptDirectCalls(promptTools, List.of(Map.of("tool_name", "mapping"), Map.of("tool_name", "search")));
        assertEquals(List.of("explain", "search", SearchToolsTool.NAME, "mapping"), new ArrayList<>(promptTools.keySet()));
    }

    @Test
    public void testAdoptDirectCallsDoesNothingWithoutToolSearch() {
        // Tool search not applied: the prompt tools are the agent's own map, with no search tool.
        Map<String, Tool> tools = pool();
        runner.adoptDirectCalls(tools, List.of(Map.of("tool_name", "mapping")));
        assertEquals(5, tools.size());
    }

    @Test
    public void testLogSelectionToleratesEveryResultShape() {
        runner.logSelection("agent-1", ToolSelectionResult.applied(List.of("a", "b"), 10));
        runner.logSelection("agent-1", ToolSelectionResult.notApplied(10, "disabled"));
        runner.logSelection(null, ToolSelectionResult.notApplied(0, "no_tools"));
        runner.logSelection(null, null);
    }
}
