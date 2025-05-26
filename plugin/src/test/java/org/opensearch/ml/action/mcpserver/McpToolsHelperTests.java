/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import com.google.common.collect.ImmutableMap;
import org.apache.lucene.search.TotalHits;
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
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class McpToolsHelperTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;
    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());
    private McpToolsHelper mcpToolsHelper;
    @Mock
    ActionListener<Boolean> listener;

    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
                .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TestHelper.mockClientStashContext(client, settings);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        mcpToolsHelper = new McpToolsHelper(client, threadPool, toolFactoryWrapper);
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onResponse(createSearchResultResponse());
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
    }

    public void test_autoLoadAllMcpTools_toolNotInMemory() {
        McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.clear();
        McpAsyncServerHolder.getMcpAsyncServerInstance().removeTool("ListIndexTool").subscribe();
        mcpToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
        assertEquals(1L, (long) McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.get("ListIndexTool"));
    }

    public void test_autoLoadAllMcpTools_searchException() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        ActionListener<Boolean> listener = mock(ActionListener.class);
        mcpToolsHelper.autoLoadAllMcpTools(listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_autoLoadAllMcpTools_updateWithNewerVersion() {
        McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 0L);
        mcpToolsHelper.autoLoadAllMcpTools(listener);
        verify(listener).onResponse(true);
        assertEquals(1L, (long) McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.get("ListIndexTool"));
    }

    public void test_autoLoadAllMcpTools_clientThreadContextException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        mcpToolsHelper.autoLoadAllMcpTools(listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    public void test_searchToolsWithVersion_success() {
        ActionListener<List<RegisterMcpTool>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchToolsWithVersion(Arrays.asList("ListIndexTool"), actionListener);
        ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    public void test_searchToolsWithVersion_searchException() {
        ActionListener<List<RegisterMcpTool>> actionListener = mock(ActionListener.class);
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        mcpToolsHelper.searchToolsWithVersion(Arrays.asList("ListIndexTool"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_searchAllToolsWithVersion_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    public void test_searchAllTools_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<List<RegisterMcpTool>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

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
        mcpToolsHelper.searchToolsWithPrimaryTermAndSeqNo(Arrays.asList("ListIndexTool"), actionListener);
        ArgumentCaptor<SearchResponse> argumentCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(10L, argumentCaptor.getValue().getHits().getHits()[0].getPrimaryTerm());
    }

    public void test_searchToolsWithPrimaryTermAndSeqNo_searchException() {
        ActionListener<SearchResponse> actionListener = mock(ActionListener.class);
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        mcpToolsHelper.searchToolsWithPrimaryTermAndSeqNo(Arrays.asList("ListIndexTool"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_searchAllToolsWithVersion_success() {
        ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        ArgumentCaptor<Map<String, Tuple<RegisterMcpTool, Long>>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    public void test_searchAllToolsWithVersion_searchException() {
        ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> actionListener = mock(ActionListener.class);
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_searchAllTools_success() {
        ActionListener<List<RegisterMcpTool>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        ArgumentCaptor<List<RegisterMcpTool>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    public void test_searchAllTools_searchException() {
        ActionListener<List<RegisterMcpTool>> actionListener = mock(ActionListener.class);
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Network issue"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        mcpToolsHelper.searchAllTools(actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    private RegisterMcpTool getRegisterMcpTool() {
        RegisterMcpTool registerMcpTool = new RegisterMcpTool(
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
                        ), null, null
        );
        registerMcpTool.setVersion(1L);
        return registerMcpTool;
    }

    private SearchResponse createSearchResultResponse() throws IOException {
        SearchHit[] hits = new SearchHit[1];
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        hits[0] = new SearchHit(0, "ListIndexTool", null, null).sourceRef(BytesReference.bytes(getRegisterMcpTool().toXContent(builder, ToXContent.EMPTY_PARAMS)));
        hits[0].version(1L);
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
}
