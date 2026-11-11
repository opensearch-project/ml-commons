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
    public void testToolsCalledInHistoryAreAllNamesSorted() {
        List<Message> history = List
            .of(
                assistantCalling("msearch", "stats"),
                assistantCalling("not_configured", SearchToolsTool.NAME),
                assistantCalling("mapping")
            );

        assertEquals(
            List.of("mapping", "msearch", "not_configured", SearchToolsTool.NAME, "stats"),
            new ArrayList<>(MLChatAgentRunnerV2.toolsCalledInHistory(history))
        );
        assertTrue(MLChatAgentRunnerV2.toolsCalledInHistory(null).isEmpty());
    }

    @Test
    public void testEveryHistoryToolIsShownWhateverTheCapAndUnknownOnesAreSkipped() {
        // A pinned tool called in history must not take a slot, and the cap must not drop older calls.
        agent = agentWithCap(1);
        List<Message> history = List
            .of(assistantCalling("stats"), assistantCalling("explain", "not_configured"), assistantCalling("msearch", "mapping"));

        Map<String, Tool> promptTools = buildPromptTools(pool(), history);

        assertEquals(
            List.of("explain", "search", "mapping", "msearch", "stats", SearchToolsTool.NAME),
            new ArrayList<>(promptTools.keySet())
        );
    }

    @Test
    public void testTheSameHistoryToolsAlwaysRenderInTheSameOrder() {
        // Provider prompt caches match on the exact prefix, and tools come first in it.
        Map<String, Tool> earlier = buildPromptTools(pool(), List.of(assistantCalling("stats"), assistantCalling("mapping")));
        Map<String, Tool> later = buildPromptTools(pool(), List.of(assistantCalling("mapping"), assistantCalling("stats")));

        assertEquals(new ArrayList<>(earlier.keySet()), new ArrayList<>(later.keySet()));
    }

    @Test
    public void testSearchToolsStaysDefinedWhenAnEarlierRunUsedItButSearchNoLongerApplies() {
        agent = agent.toBuilder().toolSelection(MLToolSelectionConfig.builder().enabled(false).build()).build();
        Map<String, Tool> tools = pool();

        Map<String, Tool> promptTools = runner.selectPromptTools(agent, tools, List.of(assistantCalling(SearchToolsTool.NAME)), "a");

        assertEquals(6, promptTools.size());
        assertTrue(promptTools.keySet().containsAll(tools.keySet()));
        assertTrue(promptTools.get(SearchToolsTool.NAME).getDescription().contains("All of your tools are already visible"));
        assertEquals("the agent's own tool map is untouched", 5, tools.size());
    }

    @Test
    public void testWithoutToolSearchOrEarlierSearchesTheAgentsToolMapIsUsedAsIs() {
        agent = agent.toBuilder().toolSelection(MLToolSelectionConfig.builder().enabled(false).build()).build();
        Map<String, Tool> tools = pool();

        assertSame(tools, runner.selectPromptTools(agent, tools, List.of(assistantCalling("stats")), "a"));
    }

    @Test
    public void testSelectPromptToolsAppliesTheSearchPlan() {
        Map<String, Tool> promptTools = runner.selectPromptTools(agent, pool(), List.of(), "a");

        assertEquals(List.of("explain", "search", SearchToolsTool.NAME), new ArrayList<>(promptTools.keySet()));
    }

    @Test
    public void testSearchOnlyTurnsDoNotUseAnIterationUpToTheBound() {
        Map<String, Tool> promptTools = buildPromptTools(pool());
        List<Map<String, String>> searchOnly = List.of(Map.of("tool_name", SearchToolsTool.NAME));

        List<Map<String, String>> searchAndCall = List.of(Map.of("tool_name", SearchToolsTool.NAME), Map.of("tool_name", "search"));

        assertFalse("a turn that also calls another tool counts", MLChatAgentRunnerV2.isFreeSearchTurn(promptTools, searchAndCall));
        for (int i = 0; i < SearchToolsTool.FREE_SEARCH_TURNS; i++) {
            assertTrue(MLChatAgentRunnerV2.isFreeSearchTurn(promptTools, searchOnly));
        }
        assertFalse("free search turns are bounded", MLChatAgentRunnerV2.isFreeSearchTurn(promptTools, searchOnly));
        assertFalse("no tool search, no free turn", MLChatAgentRunnerV2.isFreeSearchTurn(pool(), searchOnly));
    }

    private MLAgent agentWithCap(int maxDynamicTools) {
        return MLAgent
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
                    .maxDynamicTools(maxDynamicTools)
                    .build()
            )
            .build();
    }

    @Test
    public void testHistoryToolsDoNotCountTowardTheCap() {
        agent = agentWithCap(1);
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
