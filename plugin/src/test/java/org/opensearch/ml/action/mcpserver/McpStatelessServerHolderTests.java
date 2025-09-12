/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import reactor.core.publisher.Mono;

public class McpStatelessServerHolderTests extends OpenSearchTestCase {

    @Mock
    private McpToolsHelper mcpStatelessToolsHelper;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private McpStatelessAsyncServer mcpStatelessAsyncServer;
    @Mock
    private OpenSearchMcpStatelessServerTransportProvider transportProvider;
    @Mock
    private McpStatelessServerHolder holder;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        TestHelper.resetMcpStatelessServerHolder();

        // Setup common mocks
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        TestHelper.mockClientStashContext(client, settings);
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenReturn(null);

        holder = new McpStatelessServerHolder(mcpStatelessToolsHelper, client, threadPool);
    }

    @After
    public void tearDown() throws Exception {
        // Reset all static fields to ensure clean test isolation
        TestHelper.resetMcpStatelessServerHolder();
        super.tearDown();
    }

    @Test
    public void testConstructor() {
        assertNotNull("Holder should be created", holder);
    }

    @Test
    public void testGetMcpStatelessServerTransportProvider_WhenInitialized() throws Exception {
        // Set up initialized state
        java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, true);

        java.lang.reflect.Field providerField = McpStatelessServerHolder.class.getDeclaredField("mcpStatelessServerTransportProvider");
        providerField.setAccessible(true);
        providerField.set(null, transportProvider);

        OpenSearchMcpStatelessServerTransportProvider result = holder.getMcpStatelessServerTransportProvider();

        assertEquals("Should return the same provider", transportProvider, result);
    }

    @Test
    public void testGetMcpStatelessAsyncServerInstance_WhenInitialized() throws Exception {
        // Set up initialized state
        java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, true);

        java.lang.reflect.Field serverField = McpStatelessServerHolder.class.getDeclaredField("mcpStatelessAsyncServer");
        serverField.setAccessible(true);
        serverField.set(null, mcpStatelessAsyncServer);

        McpStatelessAsyncServer result = holder.getMcpStatelessAsyncServerInstance();

        assertEquals("Should return the same server", mcpStatelessAsyncServer, result);
    }

    @Test
    public void testStartSyncMcpToolsJob() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        holder.startSyncMcpToolsJob();

        verify(threadPool).schedule(any(Runnable.class), eq(TimeValue.timeValueSeconds(10)), eq("opensearch_ml_general"));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        CountDownLatch latch = new CountDownLatch(5);
        AtomicReference<OpenSearchMcpStatelessServerTransportProvider> providerRef = new AtomicReference<>();
        AtomicBoolean allSame = new AtomicBoolean(true);

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    OpenSearchMcpStatelessServerTransportProvider provider = holder.getMcpStatelessServerTransportProvider();
                    OpenSearchMcpStatelessServerTransportProvider existing = providerRef.getAndSet(provider);
                    if (existing != null && existing != provider) {
                        allSame.set(false);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS));
        assertTrue("All threads should get the same provider", allSame.get());
        assertNotNull("Provider should not be null", providerRef.get());
    }

    @Test
    public void testSingletonBehavior() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        OpenSearchMcpStatelessServerTransportProvider provider1 = holder.getMcpStatelessServerTransportProvider();
        OpenSearchMcpStatelessServerTransportProvider provider2 = holder.getMcpStatelessServerTransportProvider();
        McpStatelessAsyncServer server1 = holder.getMcpStatelessAsyncServerInstance();
        McpStatelessAsyncServer server2 = holder.getMcpStatelessAsyncServerInstance();

        assertSame("Providers should be the same instance", provider1, provider2);
        assertSame("Servers should be the same instance", server1, server2);
    }

    @Test
    public void testInitialize_AlreadyInitialized() {
        // Set up initialized state first
        try {
            java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.set(null, true);
        } catch (Exception e) {
            fail("Failed to set up test state: " + e.getMessage());
        }

        holder.initialize();

        // Should not call searchAllToolsWithVersion when already initialized
        verify(mcpStatelessToolsHelper, times(0)).searchAllToolsWithVersion(any());
    }

    @Test
    public void testStaticFieldsManagement() {
        // Test that static fields can be accessed and modified
        try {
            java.lang.reflect.Field toolsField = McpStatelessServerHolder.class.getDeclaredField("IN_MEMORY_MCP_TOOLS");
            toolsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Long> tools = (java.util.Map<String, Long>) toolsField.get(null);

            assertNotNull("IN_MEMORY_MCP_TOOLS should not be null", tools);
            assertTrue("IN_MEMORY_MCP_TOOLS should be empty initially", tools.isEmpty());

            // Test adding a tool
            tools.put("test-tool", 1L);
            assertEquals("Should have one tool", 1, tools.size());
            assertTrue("Should contain test-tool", tools.containsKey("test-tool"));

        } catch (Exception e) {
            fail("Failed to test static fields: " + e.getMessage());
        }
    }

    @Test
    public void testConstants() {
        // Test that constants are accessible
        try {
            java.lang.reflect.Field intervalField = McpStatelessServerHolder.class.getDeclaredField("SYNC_MCP_TOOLS_JOB_INTERVAL");
            intervalField.setAccessible(true);
            int interval = (int) intervalField.get(null);

            assertEquals("SYNC_MCP_TOOLS_JOB_INTERVAL should be 10", 10, interval);

        } catch (Exception e) {
            fail("Failed to test constants: " + e.getMessage());
        }
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);
        AtomicBoolean allSuccessful = new AtomicBoolean(true);

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    // Test concurrent access to static fields
                    java.lang.reflect.Field toolsField = McpStatelessServerHolder.class.getDeclaredField("IN_MEMORY_MCP_TOOLS");
                    toolsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Long> tools = (java.util.Map<String, Long>) toolsField.get(null);

                    tools.put("thread-" + Thread.currentThread().getId(), System.currentTimeMillis());

                } catch (Exception e) {
                    allSuccessful.set(false);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS));
        assertTrue("All threads should succeed", allSuccessful.get());
    }

    // ==================== AUTO LOAD TESTS ====================

    @Test
    public void test_autoLoadAllMcpTools_success() {
        // Mock searchAllToolsWithVersion to return empty result (no tools)
        doAnswer(invocationOnMock -> {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener = invocationOnMock.getArgument(0);
            listener.onResponse(new java.util.HashMap<>());
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
    }

    @Test
    public void test_autoLoadAllMcpTools_searchException() {
        doAnswer(invocationOnMock -> {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener = invocationOnMock.getArgument(0);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_autoLoadAllMcpTools_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    // ==================== TOOL LOADING SCENARIOS ====================

    @Test
    public void test_autoLoadAllMcpTools_withNewTools() throws Exception {
        // Set up initialized state with mocked server
        java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, true);

        java.lang.reflect.Field serverField = McpStatelessServerHolder.class.getDeclaredField("mcpStatelessAsyncServer");
        serverField.setAccessible(true);
        serverField.set(null, mcpStatelessAsyncServer);

        // Mock MCP server addTool method to handle any tool specification
        when(mcpStatelessAsyncServer.addTool(any())).thenReturn(Mono.empty());

        // Mock searchAllToolsWithVersion to return tools that are not in memory
        doAnswer(invocationOnMock -> {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener = invocationOnMock.getArgument(0);
            Map<String, Tuple<McpToolRegisterInput, Long>> toolsWithVersion = new java.util.HashMap<>();
            McpToolRegisterInput tool = getRegisterMcpTool();
            toolsWithVersion.put(tool.getName(), Tuple.tuple(tool, 1L));
            listener.onResponse(toolsWithVersion);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        // Clear the in-memory tools to simulate new tools
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.clear();

        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
        verify(mcpStatelessAsyncServer).addTool(any());
        assertTrue("Tool should be in memory cache", McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.containsKey("ListIndexTool"));
        assertEquals("Tool version should be 1", Long.valueOf(1L), McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.get("ListIndexTool"));
    }

    @Test
    public void test_autoLoadAllMcpTools_withToolVersionUpdate() throws Exception {
        // Set up initialized state with mocked server
        java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, true);

        java.lang.reflect.Field serverField = McpStatelessServerHolder.class.getDeclaredField("mcpStatelessAsyncServer");
        serverField.setAccessible(true);
        serverField.set(null, mcpStatelessAsyncServer);

        // Mock MCP server methods
        when(mcpStatelessAsyncServer.removeTool(any())).thenReturn(Mono.empty());
        when(mcpStatelessAsyncServer.addTool(any())).thenReturn(Mono.empty());

        // Mock searchAllToolsWithVersion to return tools with newer versions
        doAnswer(invocationOnMock -> {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener = invocationOnMock.getArgument(0);
            Map<String, Tuple<McpToolRegisterInput, Long>> toolsWithVersion = new java.util.HashMap<>();
            McpToolRegisterInput tool = getRegisterMcpTool();
            toolsWithVersion.put(tool.getName(), Tuple.tuple(tool, 2L)); // Newer version
            listener.onResponse(toolsWithVersion);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        // Pre-populate with older version
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L);

        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
        verify(mcpStatelessAsyncServer).addTool(any());
        verify(mcpStatelessAsyncServer).removeTool(any());
        assertTrue("Tool should be in memory cache", McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.containsKey("ListIndexTool"));
        assertEquals("Tool version should be 2", Long.valueOf(2L), McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.get("ListIndexTool"));
    }

    @Test
    public void test_autoLoadAllMcpTools_withMixedToolStates() throws Exception {
        // Set up initialized state with mocked server
        java.lang.reflect.Field initializedField = McpStatelessServerHolder.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, true);

        java.lang.reflect.Field serverField = McpStatelessServerHolder.class.getDeclaredField("mcpStatelessAsyncServer");
        serverField.setAccessible(true);
        serverField.set(null, mcpStatelessAsyncServer);

        // Mock MCP server methods
        when(mcpStatelessAsyncServer.removeTool(any())).thenReturn(Mono.empty());
        when(mcpStatelessAsyncServer.addTool(any())).thenReturn(Mono.empty());

        // Mock searchAllToolsWithVersion to return multiple tools with different states
        doAnswer(invocationOnMock -> {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener = invocationOnMock.getArgument(0);
            Map<String, Tuple<McpToolRegisterInput, Long>> toolsWithVersion = new java.util.HashMap<>();

            // Tool 1: ListIndexTool with version 2 (will be updated)
            McpToolRegisterInput tool1 = getRegisterMcpTool();
            toolsWithVersion.put(tool1.getName(), Tuple.tuple(tool1, 2L));

            // Tool 2: Another tool (new tool)
            McpToolRegisterInput tool2 = new McpToolRegisterInput(
                "AnotherListIndexTool",
                "ListIndexTool",
                "Another list tool description",
                Map.of(),
                Map.of(),
                null,
                null
            );
            tool2.setVersion(1L);
            toolsWithVersion.put(tool2.getName(), Tuple.tuple(tool2, 1L));

            listener.onResponse(toolsWithVersion);
            return null;
        }).when(mcpStatelessToolsHelper).searchAllToolsWithVersion(any());

        // Pre-populate with some tools
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L); // Will be updated

        ActionListener<Boolean> listener = mock(ActionListener.class);
        holder.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
        assertTrue("Tool should be in memory cache", McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.containsKey("AnotherListIndexTool"));
        assertEquals("Tool version should be 2", Long.valueOf(2L), McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.get("ListIndexTool"));
    }

    // ==================== UTILITY TESTS ====================

    @Test
    public void test_startSyncMcpToolsJob() {
        // This method schedules a job, so we just verify it doesn't throw an exception
        try {
            holder.startSyncMcpToolsJob();
            // If we get here, no exception was thrown
        } catch (Exception e) {
            fail("startSyncMcpToolsJob should not throw an exception: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private McpToolRegisterInput getRegisterMcpTool() {
        McpToolRegisterInput registerMcpTool = new McpToolRegisterInput(
            "ListIndexTool",
            "ListIndexTool",
            "OpenSearch index name list, separated by comma. for example: [\\\"index1\\\", \\\"index2\\\"], use empty array [] to list all indices in the cluster",
            Map.of(),
            Map
                .of(
                    "type",
                    "object",
                    "properties",
                    Map.of("indices", Map.of("type", "array", "items", Map.of("type", "string"))),
                    "additionalProperties",
                    false
                ),
            null,
            null
        );
        registerMcpTool.setVersion(1L);
        return registerMcpTool;
    }

    private SearchResponse createSearchResultResponse() throws IOException {
        return createSearchResultResponseWithVersion(1L);
    }

    private SearchResponse createSearchResultResponseWithVersion(long version) throws IOException {
        SearchHit[] hits = new SearchHit[1];
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        hits[0] = new SearchHit(0, "ListIndexTool", null, null)
            .sourceRef(BytesReference.bytes(getRegisterMcpTool().toXContent(builder, ToXContent.EMPTY_PARAMS)));
        hits[0].version(version);
        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            1,
            1,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }

    private SearchResponse createSearchResultResponseWithMultipleTools() throws IOException {
        SearchHit[] hits = new SearchHit[2];

        // Tool 1: ListIndexTool with version 2 (will be updated)
        XContentBuilder builder1 = XContentBuilder.builder(XContentType.JSON.xContent());
        hits[0] = new SearchHit(0, "ListIndexTool", null, null)
            .sourceRef(BytesReference.bytes(getRegisterMcpTool().toXContent(builder1, ToXContent.EMPTY_PARAMS)));
        hits[0].version(2L);

        // Tool 2: Another ListIndexTool with different name (new tool)
        McpToolRegisterInput anotherTool = new McpToolRegisterInput(
            "AnotherListIndexTool",
            "ListIndexTool", // Use the same type that has a factory
            "Another list tool description",
            Map.of(),
            Map.of(),
            null,
            null
        );
        anotherTool.setVersion(1L);
        XContentBuilder builder2 = XContentBuilder.builder(XContentType.JSON.xContent());
        hits[1] = new SearchHit(1, "AnotherListIndexTool", null, null)
            .sourceRef(BytesReference.bytes(anotherTool.toXContent(builder2, ToXContent.EMPTY_PARAMS)));
        hits[1].version(1L);

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            1,
            1,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }
}
