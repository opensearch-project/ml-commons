/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.IdsQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.McpToolBaseInput;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.engine.tools.AgentTool;
import org.opensearch.ml.engine.tools.IndexInsightTool;
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
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;
import com.networknt.schema.Schema;

import io.modelcontextprotocol.spec.McpSchema;

public class McpToolsHelperTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;
    @SuppressWarnings("rawtypes")
    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());
    private McpToolsHelper mcpToolsHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TestHelper.mockClientStashContext(client, settings);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        mcpToolsHelper = new McpToolsHelper(client, toolFactoryWrapper);

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
        super.tearDown();
    }

    // ==================== SEARCH TESTS ====================

    @Test
    public void test_searchAllToolsWithVersion_success() {
        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Tuple<McpToolRegisterInput, Long>>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    @Test
    public void test_searchAllTools_success() {
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<McpToolRegisterInput>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
    }

    @Test
    public void test_searchAllTools_indexNotFound_returnsEmpty() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new IndexNotFoundException("no index"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<McpToolRegisterInput>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().isEmpty());
    }

    @Test
    public void test_searchToolsWithVersion_success() {
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchToolsWithVersion(Arrays.asList("ListIndexTool"), actionListener);
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
        mcpToolsHelper.searchToolsWithPrimaryTermAndSeqNo(Arrays.asList("ListIndexTool"), actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SearchResponse> argumentCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(10L, argumentCaptor.getValue().getHits().getHits()[0].getPrimaryTerm());
    }

    /**
     * Regression test for https://github.com/opensearch-project/ml-commons/issues/5032: the tool lookup used an
     * analysed, default-OR match query on the `name` text field, so names sharing a single token (e.g. `vfy-list-tool`
     * and `vfy-mapping-tool`) all matched and `_remove` deleted unrelated tools. Tool documents are keyed by name, so
     * the lookup must be an exact document-id query.
     */
    @Test
    public void test_searchToolsWithVersion_usesExactIdsQuery() {
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchToolsWithVersion(Arrays.asList("vfy-list-tool", "vfy-mapping-tool"), actionListener);

        QueryBuilder query = captureSearchQuery();
        assertTrue("Expected IdsQueryBuilder but got: " + query.getClass().getSimpleName(), query instanceof IdsQueryBuilder);
        assertEquals(Set.of("vfy-list-tool", "vfy-mapping-tool"), ((IdsQueryBuilder) query).ids());
    }

    @Test
    public void test_searchToolsWithPrimaryTermAndSeqNo_usesExactIdsQuery() {
        ActionListener<SearchResponse> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchToolsWithPrimaryTermAndSeqNo(Arrays.asList("vfy-list-tool"), actionListener);

        QueryBuilder query = captureSearchQuery();
        assertTrue("Expected IdsQueryBuilder but got: " + query.getClass().getSimpleName(), query instanceof IdsQueryBuilder);
        assertEquals(Set.of("vfy-list-tool"), ((IdsQueryBuilder) query).ids());
    }

    private QueryBuilder captureSearchQuery() {
        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchRequestCaptor.capture(), isA(ActionListener.class));
        return searchRequestCaptor.getValue().source().query();
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
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Network issue", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_searchAllToolsWithVersion_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_searchAllToolsWithVersion_parseIOException() throws IOException {
        setupMalformedJsonResponse();
        ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllToolsWithVersion(actionListener);
        verifyCompositeIOException(actionListener);
    }

    @Test
    public void test_searchAllTools_searchException() {
        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new OpenSearchException("Search failed"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search mcp tools index with error: Search failed", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_searchAllTools_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("Client error"));
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Client error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_searchAllTools_parseIOException() throws IOException {
        setupMalformedJsonResponse();
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchAllTools(actionListener);
        verifyCompositeIOException(actionListener);
    }

    @Test
    public void test_searchToolsWithVersion_parseIOException() throws IOException {
        setupMalformedJsonResponse();
        ActionListener<List<McpToolRegisterInput>> actionListener = mock(ActionListener.class);
        mcpToolsHelper.searchToolsWithVersion(Arrays.asList("ListIndexTool"), actionListener);
        verifyCompositeIOException(actionListener);
    }

    // ==================== TOOL SPECIFICATION TESTS ====================

    @Test
    public void test_createToolSpecification_success() {
        McpToolBaseInput tool = new McpToolRegisterInput("ListIndexTool", "ListIndexTool", "Test tool", Map.of(), Map.of(), null, null);
        var result = mcpToolsHelper.createToolSpecification(tool);
        assertNotNull(result);
    }

    @Test
    public void test_createToolSpecification_withSchema() {
        Map<String, Object> attributes = Map
            .of("input_schema", Map.of("type", "object", "properties", Map.of("test", Map.of("type", "string"))));
        McpToolBaseInput tool = new McpToolRegisterInput("ListIndexTool", "ListIndexTool", "Test tool", Map.of(), attributes, null, null);
        var result = mcpToolsHelper.createToolSpecification(tool);
        assertNotNull(result);
    }

    @Test
    public void test_createToolSpecification_factoryNotFound() {
        McpToolBaseInput tool = new McpToolRegisterInput("NonExistentTool", "NonExistentTool", "Test tool", Map.of(), Map.of(), null, null);
        assertThrows(RuntimeException.class, () -> mcpToolsHelper.createToolSpecification(tool));
    }

    @Test
    public void test_createToolSpecification_callTool_stringOutput() {
        McpSchema.CallToolResult result = callMockTool("plain text output");
        assertFalse(result.isError());
        assertEquals("plain text output", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    @Test
    public void test_createToolSpecification_callTool_modelTensorOutput() {
        ModelTensor tensor = ModelTensor.builder().name("MockTool").dataAsMap(Map.of("took", 5)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        McpSchema.CallToolResult result = callMockTool(output);
        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("\"name\":\"MockTool\""));
        assertTrue(text.contains("\"took\":5"));
    }

    @Test
    public void test_createToolSpecification_noSchema_advertisesObjectSchema() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        McpSchema.JsonSchema schema = mcpToolsHelper.createToolSpecification(tool).tool().inputSchema();
        assertEquals("object", schema.type());
    }

    @Test
    public void test_createToolSpecification_noSchema_usesFactoryDefaultSchema() {
        String defaultSchema = "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}}}";
        mockToolFactory(mockToolWithOutput("ok"), Map.of("input_schema", defaultSchema));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        McpSchema.JsonSchema schema = mcpToolsHelper.createToolSpecification(tool).tool().inputSchema();
        assertEquals("object", schema.type());
        assertTrue(schema.properties().containsKey("question"));
    }

    @Test
    public void test_createToolSpecification_registeredSchemaWinsOverFactoryDefault() {
        mockToolFactory(mockToolWithOutput("ok"), Map.of("input_schema", "{\"type\":\"object\",\"properties\":{\"question\":{}}}"));
        Map<String, Object> attributes = Map
            .of("input_schema", Map.of("type", "object", "properties", Map.of("index", Map.of("type", "string"))));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), attributes, null, null);
        McpSchema.JsonSchema schema = mcpToolsHelper.createToolSpecification(tool).tool().inputSchema();
        assertEquals(Set.of("index"), schema.properties().keySet());
    }

    @Test
    public void test_createToolSpecification_schemaWithoutType_isTreatedAsObject() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        Map<String, Object> attributes = Map
            .of("input_schema", Map.of("properties", Map.of("question", Map.of("type", "string")), "required", List.of("question")));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), attributes, null, null);
        McpSchema.JsonSchema schema = mcpToolsHelper.createToolSpecification(tool).tool().inputSchema();
        assertEquals("object", schema.type());
        assertTrue(schema.properties().containsKey("question"));
        assertEquals(List.of("question"), schema.required());
    }

    @Test
    public void test_createToolSpecification_emptySchema_isTreatedAsObject() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        McpToolBaseInput tool = new McpToolRegisterInput(
            "MockTool",
            "MockTool",
            "Test tool",
            Map.of(),
            Map.of("input_schema", "{}"),
            null,
            null
        );
        assertEquals("object", mcpToolsHelper.createToolSpecification(tool).tool().inputSchema().type());
    }

    @Test
    public void test_createToolSpecification_invalidSchema_throws() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        McpToolBaseInput tool = new McpToolRegisterInput(
            "MockTool",
            "MockTool",
            "Test tool",
            Map.of(),
            Map.of("input_schema", "{not json"),
            null,
            null
        );
        assertThrows(IllegalArgumentException.class, () -> mcpToolsHelper.createToolSpecification(tool));
    }

    @Test
    public void test_createToolSpecification_nonObjectSchemaType_throws() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        McpToolBaseInput tool = new McpToolRegisterInput(
            "MockTool",
            "MockTool",
            "Test tool",
            Map.of(),
            Map.of("input_schema", "{\"type\":\"string\"}"),
            null,
            null
        );
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> mcpToolsHelper.createToolSpecification(tool));
        assertTrue(e.getMessage().contains("\"string\""));
    }

    @Test
    public void test_createToolSpecification_nullSchema_isTreatedAsObject() {
        mockToolFactory(mockToolWithOutput("ok"), null);
        McpToolBaseInput tool = new McpToolRegisterInput(
            "MockTool",
            "MockTool",
            "Test tool",
            Map.of(),
            Map.of("input_schema", "null"),
            null,
            null
        );
        assertEquals("object", mcpToolsHelper.createToolSpecification(tool).tool().inputSchema().type());
    }

    @Test
    public void test_createToolSpecification_unusableFactoryDefaultSchema_fallsBackToObject() {
        mockToolFactory(mockToolWithOutput("ok"), Map.of("input_schema", "{\"type\":\"string\",\"properties\":{\"q\":{}}}"));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        McpSchema.JsonSchema schema = mcpToolsHelper.createToolSpecification(tool).tool().inputSchema();
        assertEquals("object", schema.type());
        assertNull(schema.properties());
    }

    @Test
    public void test_createToolSpecification_instanceSchemaWinsOverFactoryDefault() {
        Tool mockTool = mockToolWithOutput("ok");
        when(mockTool.getAttributes()).thenReturn(Map.of("input_schema", "{\"type\":\"object\",\"properties\":{\"a\":{}}}"));
        mockToolFactory(mockTool, Map.of("input_schema", "{\"type\":\"object\",\"properties\":{\"b\":{}}}"));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        assertEquals(Set.of("a"), mcpToolsHelper.createToolSpecification(tool).tool().inputSchema().properties().keySet());
    }

    @Test
    public void test_createToolSpecification_invalidInstanceSchema_throws() {
        // Only factory defaults fall back; a broken instance schema still fails, as before.
        Tool mockTool = mockToolWithOutput("ok");
        when(mockTool.getAttributes()).thenReturn(Map.of("input_schema", "{not json"));
        mockToolFactory(mockTool, Map.of("input_schema", "{\"type\":\"object\"}"));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        assertThrows(IllegalArgumentException.class, () -> mcpToolsHelper.createToolSpecification(tool));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_createToolSpecification_factoryDefaultAttributesThrow_fallsBackToObject() {
        Tool mockTool = mockToolWithOutput("ok");
        mockToolFactory(mockTool, null);
        when(toolFactoryWrapper.getToolsFactories().get("MockTool").getDefaultAttributes()).thenThrow(new IllegalStateException("boom"));
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        assertEquals("object", mcpToolsHelper.createToolSpecification(tool).tool().inputSchema().type());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void test_createToolSpecification_builtInFactoriesWithoutSchema_advertiseObjectSchema() {
        AgentTool.Factory.getInstance().init(client);
        IndexInsightTool.Factory.getInstance().init(client);
        Map<String, Tool.Factory> factories = Map
            .of(
                AgentTool.TYPE,
                AgentTool.Factory.getInstance(),
                IndexInsightTool.TYPE,
                IndexInsightTool.Factory.getInstance(),
                ListIndexTool.TYPE,
                ListIndexTool.Factory.getInstance()
            );
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(factories);
        for (String type : factories.keySet()) {
            McpToolBaseInput tool = new McpToolRegisterInput(type, type, null, Map.of("agent_id", "agent"), Map.of(), null, null);
            assertEquals(type, "object", mcpToolsHelper.createToolSpecification(tool).tool().inputSchema().type());
        }
        McpToolBaseInput agentTool = new McpToolRegisterInput(
            "agent",
            AgentTool.TYPE,
            null,
            Map.of("agent_id", "agent"),
            Map.of(),
            null,
            null
        );
        assertTrue(mcpToolsHelper.createToolSpecification(agentTool).tool().inputSchema().properties().containsKey("question"));
    }

    @Test
    public void test_createToolSpecification_callTool_failure_returnsIsErrorResult() {
        Tool mockTool = mock(Tool.class);
        doAnswer(invocationOnMock -> {
            ActionListener<Object> listener = invocationOnMock.getArgument(1);
            listener
                .onFailure(
                    new OpenSearchSecurityException("no permissions for [cluster:admin/opensearch/ml/execute]", RestStatus.FORBIDDEN)
                );
            return null;
        }).when(mockTool).run(any(), any());
        McpSchema.CallToolResult result = callTool(mockTool);
        assertTrue(result.isError());
        assertEquals("no permissions for [cluster:admin/opensearch/ml/execute]", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    @Test
    public void test_createToolSpecification_callTool_runThrows_returnsIsErrorResult() {
        Tool mockTool = mock(Tool.class);
        doThrow(new IllegalArgumentException("Agent ID not registered in tool")).when(mockTool).run(any(), any());
        McpSchema.CallToolResult result = callTool(mockTool);
        assertTrue(result.isError());
        assertEquals("Agent ID not registered in tool", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    @Test
    public void test_createToolSpecification_callTool_failureWithoutMessage_usesExceptionType() {
        Tool mockTool = mock(Tool.class);
        doAnswer(invocationOnMock -> {
            ActionListener<Object> listener = invocationOnMock.getArgument(1);
            listener.onFailure(new NullPointerException());
            return null;
        }).when(mockTool).run(any(), any());
        McpSchema.CallToolResult result = callTool(mockTool);
        assertTrue(result.isError());
        assertEquals("NullPointerException", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    // ==================== ARGUMENT VALIDATION TESTS ====================

    private static final Map<String, Object> QUESTION_REQUIRED_SCHEMA = Map
        .of(
            "input_schema",
            "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"},\"size\":{\"type\":\"integer\"}},"
                + "\"required\":[\"question\"]}"
        );

    @Test
    public void test_callTool_missingRequiredArgument_returnsIsErrorWithoutRunning() {
        Tool mockTool = mockToolWithOutput("ran");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, QUESTION_REQUIRED_SCHEMA, Map.of());
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals("Invalid arguments for tool MockTool: required property 'question' not found", text);
        verify(mockTool, never()).run(any(), any());
    }

    @Test
    public void test_callTool_nullArguments_areValidatedAsEmptyObject() {
        Tool mockTool = mockToolWithOutput("ran");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, QUESTION_REQUIRED_SCHEMA, null);
        assertTrue(result.isError());
        verify(mockTool, never()).run(any(), any());
    }

    @Test
    public void test_callTool_wrongArgumentType_returnsIsErrorWithoutRunning() {
        Tool mockTool = mockToolWithOutput("ran");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("question", "why");
        arguments.put("size", "ten");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, QUESTION_REQUIRED_SCHEMA, arguments);
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals("Invalid arguments for tool MockTool: /size: string found, integer expected", text);
        verify(mockTool, never()).run(any(), any());
    }

    @Test
    public void test_callTool_validArguments_runTool() {
        Tool mockTool = mockToolWithOutput("ran");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, QUESTION_REQUIRED_SCHEMA, Map.of("question", "why", "size", 3));
        assertFalse(result.isError());
        assertEquals("ran", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    @Test
    public void test_callTool_additionalPropertiesFalse_allowsUndeclaredArgument() {
        // Undeclared properties are allowed, as in the MCP TypeScript and Python SDKs; tools read extra parameters.
        Tool mockTool = mockToolWithOutput("ran");
        Map<String, Object> attributes = Map
            .of("input_schema", "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, attributes, Map.of("q", "x", "other", "y"));
        assertFalse(result.isError());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_callTool_jsonStringForObjectOrArray_isAcceptedAndPassedUnchanged() {
        Tool mockTool = mockToolWithOutput("ran");
        Map<String, Object> attributes = Map
            .of(
                "input_schema",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"object\"},\"indices\":{\"type\":\"array\","
                    + "\"items\":{\"type\":\"string\"}},\"size\":{\"type\":\"integer\"}},\"required\":[\"query\"]}"
            );
        Map<String, Object> arguments = Map.of("query", "{\"match_all\":{}}", "indices", "[\"a\",\"b\"]", "size", "3");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, attributes, arguments);
        assertFalse(((McpSchema.TextContent) result.content().get(0)).text(), result.isError());
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockTool).run(captor.capture(), any());
        assertEquals("{\"match_all\":{}}", captor.getValue().get("query"));
        assertEquals("[\"a\",\"b\"]", captor.getValue().get("indices"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_callTool_singleValueForArray_isAccepted() {
        Tool mockTool = mockToolWithOutput("ran");
        Map<String, Object> attributes = Map
            .of("input_schema", "{\"type\":\"object\",\"properties\":{\"index\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}");
        assertFalse(callToolWithSchema(mockTool, attributes, Map.of("index", "my-index")).isError());
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockTool).run(captor.capture(), any());
        assertEquals("my-index", captor.getValue().get("index"));
    }

    @Test
    public void test_callTool_argumentsWrappedInInput_areValidated() {
        Map<String, Object> attributes = QUESTION_REQUIRED_SCHEMA;
        assertFalse(callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of("input", "{\"question\":\"why\"}")).isError());
        McpSchema.CallToolResult result = callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of("input", "{\"size\":\"x\"}"));
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text, text.contains("required property 'question' not found"));
        assertTrue(text, text.contains("/size: string found, integer expected"));
    }

    @Test
    public void test_callTool_inputKeysWinOverTopLevel_likeTheTool() {
        Map<String, Object> arguments = Map.of("question", "why", "size", "x", "input", "{\"size\":3}");
        assertFalse(callToolWithSchema(mockToolWithOutput("ran"), QUESTION_REQUIRED_SCHEMA, arguments).isError());
        Map<String, Object> wrongInside = Map.of("question", "why", "size", 3, "input", "{\"size\":\"x\"}");
        assertTrue(callToolWithSchema(mockToolWithOutput("ran"), QUESTION_REQUIRED_SCHEMA, wrongInside).isError());
    }

    @Test
    public void test_callTool_inputAsObject_isUnwrapped() {
        Map<String, Object> arguments = Map.of("input", Map.of("question", "why"));
        assertFalse(callToolWithSchema(mockToolWithOutput("ran"), QUESTION_REQUIRED_SCHEMA, arguments).isError());
    }

    @Test
    public void test_callTool_inputNotStrictJson_skipsValidation() {
        // Tools parse "input" with lenient Gson, so a call Jackson can't read is passed to the tool unchecked.
        Tool mockTool = mockToolWithOutput("ran");
        assertFalse(callToolWithSchema(mockTool, QUESTION_REQUIRED_SCHEMA, Map.of("input", "{'question':'why'}")).isError());
        verify(mockTool).run(any(), any());
    }

    @Test
    public void test_callTool_coercionEdgeCases() {
        Map<String, Object> attributes = Map
            .of(
                "input_schema",
                "{\"type\":\"object\",\"properties\":{\"n\":{\"type\":[\"integer\",\"null\"]},\"i\":{\"type\":\"array\","
                    + "\"items\":{\"type\":\"string\"}},\"s\":{\"type\":\"string\"},\"c\":{\"type\":\"integer\"}}}"
            );
        // "null" for a nullable integer, "" for an array, a JSON-looking string for a string, 3.0 (Gson clients) for an integer.
        Map<String, Object> ok = new HashMap<>();
        ok.put("n", "null");
        ok.put("i", "");
        ok.put("s", "{\"a\":1}");
        ok.put("c", 3.0);
        McpSchema.CallToolResult result = callToolWithSchema(mockToolWithOutput("ran"), attributes, ok);
        assertFalse(((McpSchema.TextContent) result.content().get(0)).text(), result.isError());
        McpSchema.CallToolResult bad = callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of("n", "x"));
        assertTrue(bad.isError());
        assertTrue(((McpSchema.TextContent) bad.content().get(0)).text().contains("/n: "));
    }

    @Test
    public void test_callTool_jsonStringOfWrongType_isRejected() {
        Tool mockTool = mockToolWithOutput("ran");
        Map<String, Object> attributes = Map.of("input_schema", "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"object\"}}}");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, attributes, Map.of("query", "not json"));
        assertTrue(result.isError());
        assertEquals(
            "Invalid arguments for tool MockTool: /query: string found, object expected",
            ((McpSchema.TextContent) result.content().get(0)).text()
        );
        verify(mockTool, never()).run(any(), any());
    }

    @Test
    public void test_callTool_enumAndNestedPointer_areReported() {
        Map<String, Object> attributes = Map
            .of(
                "input_schema",
                "{\"type\":\"object\",\"properties\":{\"mode\":{\"enum\":[\"a\",\"b\"]},\"filter\":{\"type\":\"object\","
                    + "\"properties\":{\"size\":{\"type\":\"integer\"}}}}}"
            );
        McpSchema.CallToolResult result = callToolWithSchema(
            mockToolWithOutput("ran"),
            attributes,
            Map.of("mode", "c", "filter", Map.of("size", "big"))
        );
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text, text.contains("/mode: "));
        assertTrue(text, text.contains("/filter/size: string found, integer expected"));
    }

    @Test
    public void test_callTool_internalRef_isResolved() {
        Map<String, Object> attributes = Map
            .of(
                "input_schema",
                "{\"type\":\"object\",\"properties\":{\"n\":{\"$ref\":\"#/$defs/count\"}},\"$defs\":{\"count\":{\"type\":\"integer\"}}}"
            );
        assertFalse(callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of("n", 2)).isError());
        McpSchema.CallToolResult result = callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of("n", Map.of()));
        assertTrue(result.isError());
        assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("/n: "));
    }

    @Test
    public void test_callTool_messagesDoNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            McpSchema.CallToolResult result = callToolWithSchema(mockToolWithOutput("ran"), QUESTION_REQUIRED_SCHEMA, Map.of());
            assertEquals(
                "Invalid arguments for tool MockTool: required property 'question' not found",
                ((McpSchema.TextContent) result.content().get(0)).text()
            );
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void test_callTool_noSchema_acceptsAnyArguments() {
        // A tool registered without a schema advertises {"type":"object"}, which accepts any object.
        Tool mockTool = mockToolWithOutput("ran");
        McpSchema.CallToolResult result = callToolWithSchema(mockTool, Map.of(), Map.of("anything", 1));
        assertFalse(result.isError());
    }

    @Test
    public void test_callTool_manyErrors_reportsFirstFewAndCount() {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            properties.append(i == 0 ? "" : ",").append("\"p").append(i).append("\":{\"type\":\"string\"}");
            required.append(i == 0 ? "" : ",").append("\"p").append(i).append("\"");
        }
        Map<String, Object> attributes = Map
            .of("input_schema", "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[" + required + "]}");
        McpSchema.CallToolResult result = callToolWithSchema(mockToolWithOutput("ran"), attributes, Map.of());
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text, text.endsWith("(and 2 more)"));
    }

    @Test
    public void test_validateArguments_unresolvableRef_skipsValidation() {
        // An external $ref is not fetched; validation fails internally, and the tool stays callable instead of
        // failing every call.
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object",
            Map.of("q", Map.of("$ref", "https://example.invalid/missing.json")),
            null,
            null,
            null,
            null
        );
        Schema compiled = McpToolsHelper.compileArgumentSchema("t", schema);
        assertNotNull(compiled);
        assertNull(McpToolsHelper.validateArguments("t", compiled, schema, Map.of("q", "x")));
    }

    @Test
    public void test_validateArguments_nullSchema_returnsNull() {
        assertNull(McpToolsHelper.validateArguments("t", null, null, Map.of()));
    }

    // ==================== HELPER METHODS ====================

    private McpSchema.CallToolResult callMockTool(Object toolOutput) {
        return callTool(mockToolWithOutput(toolOutput));
    }

    @SuppressWarnings("unchecked")
    private Tool mockToolWithOutput(Object toolOutput) {
        Tool mockTool = mock(Tool.class);
        doAnswer(invocationOnMock -> {
            ActionListener<Object> listener = invocationOnMock.getArgument(1);
            listener.onResponse(toolOutput);
            return null;
        }).when(mockTool).run(any(), any());
        return mockTool;
    }

    @SuppressWarnings("unchecked")
    private void mockToolFactory(Tool mockTool, Map<String, Object> defaultAttributes) {
        Tool.Factory<Tool> mockFactory = mock(Tool.Factory.class);
        when(mockFactory.create(any())).thenReturn(mockTool);
        when(mockFactory.getDefaultAttributes()).thenReturn(defaultAttributes);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(Map.of("MockTool", mockFactory));
    }

    private McpSchema.CallToolResult callToolWithSchema(Tool mockTool, Map<String, Object> attributes, Map<String, Object> arguments) {
        mockToolFactory(mockTool, null);
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), attributes, null, null);
        var specification = mcpToolsHelper.createToolSpecification(tool);
        return specification.callHandler().apply(null, new McpSchema.CallToolRequest("MockTool", arguments)).block();
    }

    private McpSchema.CallToolResult callTool(Tool mockTool) {
        mockToolFactory(mockTool, null);
        McpToolBaseInput tool = new McpToolRegisterInput("MockTool", "MockTool", "Test tool", Map.of(), Map.of(), null, null);
        var specification = mcpToolsHelper.createToolSpecification(tool);
        return specification.callHandler().apply(null, new McpSchema.CallToolRequest("MockTool", Map.of())).block();
    }

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

    private void setupMalformedJsonResponse() throws IOException {
        String malformedJson =
            "{\"name\":\"test\",\"type\":\"test\",\"description\":\"test\",\"parameters\":{},\"attributes\":{},\"version\":1,\"malformed\":}";
        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "ListIndexTool", null, null).sourceRef(new BytesArray(malformedJson.getBytes()));
        hits[0].version(1L);

        SearchResponse searchResponse = new SearchResponse(
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

        doAnswer(invocationOnMock -> {
            ActionListener<SearchResponse> listener = invocationOnMock.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
    }

    private void verifyCompositeIOException(ActionListener<?> actionListener) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());

        Exception exception = argumentCaptor.getValue();
        assertTrue(
            "Expected OpenSearchException but got: " + exception.getClass().getSimpleName(),
            exception instanceof OpenSearchException
        );

        OpenSearchException openSearchException = (OpenSearchException) exception;
        assertTrue("Expected error message to contain 'Failed to parse'", openSearchException.getMessage().contains("Failed to parse"));

        // Verify that the original IOException is suppressed
        Throwable[] suppressed = openSearchException.getSuppressed();
        assertEquals("Expected exactly one suppressed exception", 1, suppressed.length);
        assertTrue("Expected suppressed exception to be IOException", suppressed[0] instanceof IOException);
    }

}
