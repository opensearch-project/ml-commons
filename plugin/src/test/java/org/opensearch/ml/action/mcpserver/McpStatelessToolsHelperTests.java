/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.McpToolBaseInput;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
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

import com.google.common.collect.ImmutableMap;

public class McpStatelessToolsHelperTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;
    @SuppressWarnings("rawtypes")
    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());
    private McpStatelessToolsHelper mcpStatelessToolsHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Reset the singleton state before each test to ensure test isolation
        resetSingletonState();

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TestHelper.mockClientStashContext(client, settings);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        mcpStatelessToolsHelper = new McpStatelessToolsHelper(client, threadPool, toolFactoryWrapper);

        // Initialize McpStatelessServerHolder for testing
        McpStatelessServerHolder.init(mcpStatelessToolsHelper);

        // Default mock behavior for search operations
        doAnswer(invocationOnMock -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onResponse(createSearchResultResponse());
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
    }

    @After
    public void tearDown() throws Exception {
        // Clean up the in-memory tools map
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.clear();
        super.tearDown();
    }

    /**
     * Resets McpStatelessServerHolder singleton state between tests.
     * Uses reflection to clear static fields, ensuring clean test isolation.
     */
    private void resetSingletonState() {
        try {
            // Reset statelessToolsHelper static field to null
            java.lang.reflect.Field statelessToolsHelperField = McpStatelessServerHolder.class.getDeclaredField("statelessToolsHelper");
            statelessToolsHelperField.setAccessible(true);
            statelessToolsHelperField.set(null, null);

            // Reset mcpStatelessAsyncServer static field to null
            java.lang.reflect.Field mcpStatelessAsyncServerField = McpStatelessServerHolder.class
                .getDeclaredField("mcpStatelessAsyncServer");
            mcpStatelessAsyncServerField.setAccessible(true);
            mcpStatelessAsyncServerField.set(null, null);

            // Reset mcpStatelessServerTransportProvider static field to null
            java.lang.reflect.Field mcpStatelessServerTransportProviderField = McpStatelessServerHolder.class
                .getDeclaredField("mcpStatelessServerTransportProvider");
            mcpStatelessServerTransportProviderField.setAccessible(true);
            mcpStatelessServerTransportProviderField.set(null, null);
        } catch (Exception e) {
            // If reflection fails, continue anyway - tests will still work but may have state pollution
        }
    }

    // ==================== SEARCH TESTS ====================

    @Test
    public void test_searchAllToolsWithVersion_success() {
        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Tuple<McpToolRegisterInput, Long>>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    @Test
    public void test_searchAllTools_success() {
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchAllTools(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<McpToolRegisterInput>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    @Test
    public void test_searchToolsWithVersion_success() {
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchToolsWithVersion(Arrays.asList("ListIndexTool"), actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<McpToolRegisterInput>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    @Test
    public void test_searchToolsWithPrimaryTermAndSeqNo_success() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            SearchResponse searchResponse = createSearchResultResponse();
            Arrays.stream(searchResponse.getHits().getHits()).forEach(x -> {
                x.setPrimaryTerm(10L);
                x.setSeqNo(10L);
            });
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        ActionListener<SearchResponse> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchToolsWithPrimaryTermAndSeqNo(Arrays.asList("ListIndexTool"), actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SearchResponse> argumentCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(10L, argumentCaptor.getValue().getHits().getHits()[0].getPrimaryTerm());
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    public void test_searchAllToolsWithVersion_searchException() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_searchAllToolsWithVersion_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpStatelessToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    // ==================== TOOL SPECIFICATION TESTS ====================

    @Test
    public void test_createToolSpecification_success() {
        McpToolBaseInput tool = new McpToolRegisterInput("ListIndexTool", "ListIndexTool", "Test tool", Map.of(), Map.of(), null, null);
        var result = mcpStatelessToolsHelper.createToolSpecification(tool);
        assertNotNull(result);
    }

    @Test
    public void test_createToolSpecification_withSchema() {
        Map<String, Object> attributes = Map
            .of("input_schema", Map.of("type", "object", "properties", Map.of("test", Map.of("type", "string"))));
        McpToolBaseInput tool = new McpToolRegisterInput("ListIndexTool", "ListIndexTool", "Test tool", Map.of(), attributes, null, null);
        var result = mcpStatelessToolsHelper.createToolSpecification(tool);
        assertNotNull(result);
    }

    @Test
    public void test_createToolSpecification_factoryNotFound() {
        McpToolBaseInput tool = new McpToolRegisterInput("NonExistentTool", "NonExistentTool", "Test tool", Map.of(), Map.of(), null, null);
        assertThrows(RuntimeException.class, () -> mcpStatelessToolsHelper.createToolSpecification(tool));
    }

    // ==================== AUTO LOAD TESTS ====================

    @Test
    public void test_autoLoadAllMcpTools_success() {
        // Mock the search to return empty results to avoid server creation
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            SearchResponse searchResponse = mock(SearchResponse.class);
            when(searchResponse.getHits()).thenReturn(SearchHits.empty());
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
    }

    @Test
    public void test_autoLoadAllMcpTools_searchException() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_autoLoadAllMcpTools_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    // ==================== TOOL LOADING SCENARIOS ====================

    @Test
    public void test_autoLoadAllMcpTools_withNewTools() {
        // Mock the search to return tools that are not in memory
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            SearchResponse searchResponse = createSearchResultResponse();
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        // Clear the in-memory tools to simulate new tools
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.clear();

        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
    }

    @Test
    public void test_autoLoadAllMcpTools_withToolVersionUpdate() {
        // Mock the search to return tools with newer versions
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            SearchResponse searchResponse = createSearchResultResponseWithVersion(2L);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        // Pre-populate with older version
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L);

        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
    }

    @Test
    public void test_autoLoadAllMcpTools_withMixedToolStates() {
        // Mock the search to return multiple tools with different states
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            SearchResponse searchResponse = createSearchResultResponseWithMultipleTools();
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        // Pre-populate with some tools
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L); // Will be updated

        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpStatelessToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
    }

    // ==================== UTILITY TESTS ====================

    @Test
    public void test_startSyncMcpToolsJob() {
        // This method schedules a job, so we just verify it doesn't throw an exception
        try {
            mcpStatelessToolsHelper.startSyncMcpToolsJob();
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
