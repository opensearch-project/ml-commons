/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.engine.tools.selection.DynamicToolSelectorTest.pool;
import static org.opensearch.ml.engine.tools.selection.DynamicToolSelectorTest.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;

public class SearchToolsToolTest {

    private DynamicToolSelector selector;
    private ThreadPool threadPool;
    private AtomicReference<Runnable> scheduledTimeout;
    private Map<String, Tool> poolTools;
    private Map<String, Tool> promptTools;
    private SearchToolsTool searchTool;

    @Before
    public void setUp() {
        selector = mock(DynamicToolSelector.class);
        threadPool = mock(ThreadPool.class);
        scheduledTimeout = new AtomicReference<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduledTimeout.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });

        poolTools = pool(12);
        promptTools = new LinkedHashMap<>();
        promptTools.put("tool0", poolTools.get("tool0"));
        searchTool = new SearchToolsTool(selector, settings(true, 10, 5), "tenant-1", poolTools, promptTools, threadPool);
        promptTools.put(SearchToolsTool.NAME, searchTool);
    }

    private void stubSearchResult(List<String> names) {
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(4);
            listener.onResponse(names);
            return null;
        }).when(selector).search(anyString(), any(), any(), any(), any());
    }

    private String run(String input) {
        AtomicReference<Object> result = new AtomicReference<>();
        searchTool.run(Map.of("input", input), ActionListener.wrap(result::set, e -> {
            throw new AssertionError("search_tools must never fail its listener", e);
        }));
        return (String) result.get();
    }

    @Test
    public void testPresentsAsAnOrdinaryToolWithASchema() {
        assertEquals(SearchToolsTool.NAME, searchTool.getName());
        assertEquals(SearchToolsTool.TYPE, searchTool.getType());
        assertTrue(((String) searchTool.getAttributes().get(TOOL_INPUT_SCHEMA_FIELD)).contains("\"query\""));
        assertTrue(searchTool.validate(Map.of()));
    }

    @Test
    public void testDescriptionStatesHowManyToolsAreHidden() {
        // 12 tools, 1 visible when the tool was built.
        assertTrue(searchTool.getDescription().contains("11 more are available"));
    }

    @Test
    public void testDescriptionNamesTheHiddenToolsSorted() {
        String description = searchTool.getDescription();
        assertTrue(description.contains("including: tool1, tool10, tool11, tool2,"));
        assertFalse("visible tools are not listed as hidden", description.contains("tool0,"));
        assertFalse(description.contains(SearchToolsTool.NAME + ","));
    }

    @Test
    public void testDescriptionCapsTheNumberOfListedNames() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < SearchToolsTool.MAX_LISTED_NAMES + 7; i++) {
            many.add(String.format(java.util.Locale.ROOT, "t%04d", i));
        }
        String description = SearchToolsTool.describe(many);
        assertTrue(description.contains(SearchToolsTool.MAX_LISTED_NAMES + 7 + " more are available"));
        assertTrue(description.contains("t0099 and 7 more."));
        assertFalse(description.contains("t0100"));
    }

    @Test
    public void testDescriptionWithNothingHiddenListsNothing() {
        String description = SearchToolsTool.describe(List.of());
        assertTrue(description.contains("0 more are available"));
        assertFalse(description.contains("including"));
    }

    @Test
    public void testDescriptionIsFixedForTheRun() {
        String before = searchTool.getDescription();
        stubSearchResult(List.of("tool3"));
        run("{\"query\":\"x\"}");
        assertEquals(before, searchTool.getDescription());
    }

    @Test
    public void testMatchesAreAppendedToThePromptToolsInRankOrder() {
        stubSearchResult(List.of("tool5", "tool3"));

        String result = run("{\"query\":\"check shard allocation\"}");

        assertEquals(List.of("tool0", SearchToolsTool.NAME, "tool5", "tool3"), new ArrayList<>(promptTools.keySet()));
        assertTrue(result.startsWith("Found 2 tool(s)"));
        assertTrue(result.contains("tool5: description 5"));
        assertTrue(result.contains("tool3: description 3"));
    }

    @Test
    public void testOnlyHiddenToolsAreSearched() {
        stubSearchResult(List.of());
        run("{\"query\":\"x\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Tool>> candidates = ArgumentCaptor.forClass(Map.class);
        verify(selector).search(eq("x"), candidates.capture(), any(), eq("tenant-1"), any());
        assertEquals(11, candidates.getValue().size());
        assertFalse(candidates.getValue().containsKey("tool0"));
        assertFalse(candidates.getValue().containsKey(SearchToolsTool.NAME));
    }

    @Test
    public void testFoundToolsAreNotSearchedAgain() {
        stubSearchResult(List.of("tool5"));
        run("{\"query\":\"first\"}");
        run("{\"query\":\"second\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Tool>> candidates = ArgumentCaptor.forClass(Map.class);
        verify(selector).search(eq("second"), candidates.capture(), any(), any(), any());
        assertFalse(candidates.getValue().containsKey("tool5"));
    }

    @Test
    public void testPlainTextInputIsAcceptedAsTheQuery() {
        stubSearchResult(List.of());
        run("check shard allocation");
        verify(selector).search(eq("check shard allocation"), any(), any(), any(), any());
    }

    @Test
    public void testLongDescriptionsAreShortenedInTheResult() {
        poolTools.put("verbose", DynamicToolSelectorTest.tool("word ".repeat(200)));
        stubSearchResult(List.of("verbose"));

        String result = run("{\"query\":\"x\"}");
        assertTrue(result.endsWith("..."));
        assertTrue(result.length() < 300);
    }

    @Test
    public void testUnknownNamesFromTheRankerAreIgnored() {
        stubSearchResult(List.of("ghost"));
        assertEquals(SearchToolsTool.NO_MATCH_MESSAGE, run("{\"query\":\"x\"}"));
        assertEquals(2, promptTools.size());
    }

    @Test
    public void testNoMatches() {
        stubSearchResult(List.of());
        assertEquals(SearchToolsTool.NO_MATCH_MESSAGE, run("{\"query\":\"x\"}"));
        assertEquals(2, promptTools.size());
    }

    @Test
    public void testMissingOrBlankQueryAsksForOne() {
        assertEquals(SearchToolsTool.NO_QUERY_MESSAGE, run("{\"query\":\"  \"}"));
        assertEquals(SearchToolsTool.NO_QUERY_MESSAGE, run("{}"));
        verify(selector, never()).search(anyString(), any(), any(), any(), any());
    }

    @Test
    public void testNothingLeftToFind() {
        promptTools.putAll(poolTools);
        assertEquals(SearchToolsTool.NOTHING_LEFT_MESSAGE, run("{\"query\":\"x\"}"));
        verify(selector, never()).search(anyString(), any(), any(), any(), any());
    }

    @Test
    public void testEmbeddingFailureTellsTheModelSearchIsUnavailable() {
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(4);
            listener.onFailure(new RuntimeException("model not deployed"));
            return null;
        }).when(selector).search(anyString(), any(), any(), any(), any());

        assertEquals(SearchToolsTool.UNAVAILABLE_MESSAGE, run("{\"query\":\"x\"}"));
        assertEquals(2, promptTools.size());
    }

    @Test
    public void testSynchronousExceptionTellsTheModelSearchIsUnavailable() {
        doThrow(new IllegalStateException("boom")).when(selector).search(anyString(), any(), any(), any(), any());
        assertEquals(SearchToolsTool.UNAVAILABLE_MESSAGE, run("{\"query\":\"x\"}"));
    }

    @Test
    public void testTimeoutTellsTheModelSearchIsUnavailableAndIgnoresALateResult() {
        AtomicReference<ActionListener<List<String>>> pending = new AtomicReference<>();
        doAnswer(invocation -> {
            pending.set(invocation.getArgument(4));
            return null;
        }).when(selector).search(anyString(), any(), any(), any(), any());

        AtomicReference<Object> result = new AtomicReference<>();
        searchTool.run(Map.of("input", "{\"query\":\"x\"}"), ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));
        assertNotNull("a timeout must be scheduled", scheduledTimeout.get());

        scheduledTimeout.get().run();
        assertEquals(SearchToolsTool.UNAVAILABLE_MESSAGE, result.get());

        // The embedding call finishing after the deadline must not change the tool list.
        pending.get().onResponse(List.of("tool5"));
        assertEquals(2, promptTools.size());
    }

    @Test
    public void testTimeoutUsesTheConfiguredBudget() {
        stubSearchResult(List.of());
        run("{\"query\":\"x\"}");
        verify(threadPool).schedule(any(Runnable.class), eq(TimeValue.timeValueSeconds(5)), eq(ThreadPool.Names.GENERIC));
    }

    /** A search tool over the same pool, but with the given cap. */
    private SearchToolsTool cappedAt(int maxDynamicTools) {
        promptTools.remove(SearchToolsTool.NAME);
        SearchToolsTool capped = new SearchToolsTool(
            selector,
            settings(true, 10, 5, maxDynamicTools),
            "tenant-1",
            poolTools,
            promptTools,
            threadPool
        );
        promptTools.put(SearchToolsTool.NAME, capped);
        searchTool = capped;
        return capped;
    }

    @Test
    public void testSearchAddsNoMoreThanTheRemainingCapacity() {
        cappedAt(3);
        stubSearchResult(List.of("tool1", "tool2", "tool3", "tool4", "tool5"));

        String result = run("{\"query\":\"anything\"}");

        assertEquals(List.of("tool0", SearchToolsTool.NAME, "tool1", "tool2", "tool3"), new ArrayList<>(promptTools.keySet()));
        assertTrue(result.startsWith("Found 3 tool(s)"));
        assertTrue("the model is told the cap is reached", result.endsWith(SearchToolsTool.LIMIT_MESSAGE));
    }

    @Test
    public void testSearchAtTheCapAddsNothingAndDoesNotSearch() {
        cappedAt(2);
        stubSearchResult(List.of("tool1", "tool2"));
        run("{\"query\":\"first\"}");

        assertEquals(SearchToolsTool.LIMIT_MESSAGE, run("{\"query\":\"second\"}"));
        assertEquals(4, promptTools.size());
        verify(selector, times(1)).search(anyString(), any(), any(), any(), any());
        // search_tools stays in the list: removing it would change the prompt prefix.
        assertTrue(promptTools.containsKey(SearchToolsTool.NAME));
    }

    @Test
    public void testPinnedToolsAndSearchToolsDoNotCountTowardTheCap() {
        cappedAt(1);
        stubSearchResult(List.of("tool7"));
        assertTrue(run("{\"query\":\"x\"}").startsWith("Found 1 tool(s)"));
    }

    @Test
    public void testDirectCallToAHiddenToolAppendsIt() {
        assertTrue(searchTool.adoptDirectCall("tool6"));
        assertEquals(List.of("tool0", SearchToolsTool.NAME, "tool6"), new ArrayList<>(promptTools.keySet()));
        assertEquals(poolTools.get("tool6"), promptTools.get("tool6"));
    }

    @Test
    public void testDirectCallToAVisibleOrUnknownToolChangesNothing() {
        assertFalse(searchTool.adoptDirectCall("tool0"));
        assertFalse(searchTool.adoptDirectCall(SearchToolsTool.NAME));
        assertFalse(searchTool.adoptDirectCall("NotOnThisAgent"));
        assertFalse(searchTool.adoptDirectCall(null));
        assertEquals(2, promptTools.size());
    }

    @Test
    public void testDirectCallsCountTowardTheCap() {
        cappedAt(1);
        assertTrue(searchTool.adoptDirectCall("tool6"));
        assertFalse("over the cap the tool still runs, but isn't appended", searchTool.adoptDirectCall("tool7"));
        assertEquals(SearchToolsTool.LIMIT_MESSAGE, run("{\"query\":\"x\"}"));
    }

    @Test
    public void testSynchronousExceptionAnswersTheModelOnlyOnceEvenWhenTheTimeoutFires() {
        doThrow(new IllegalStateException("boom")).when(selector).search(anyString(), any(), any(), any(), any());
        AtomicInteger responses = new AtomicInteger();

        searchTool.run(Map.of("input", "{\"query\":\"x\"}"), ActionListener.wrap(r -> responses.incrementAndGet(), e -> {
            throw new AssertionError("search_tools must never fail its listener", e);
        }));
        scheduledTimeout.get().run();

        assertEquals(1, responses.get());
    }
}
