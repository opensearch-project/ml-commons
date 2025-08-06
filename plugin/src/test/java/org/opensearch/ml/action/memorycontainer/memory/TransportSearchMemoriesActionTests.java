/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemorySearchResult;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportSearchMemoriesActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Client client;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLSearchMemoriesResponse> actionListener;

    private TransportSearchMemoriesAction transportSearchMemoriesAction;

    private MLMemoryContainer mockContainer;
    private Settings settings;

    // Helper method to create a real SearchHit (since SearchHit is final and can't be mocked)
    private SearchHit createSearchHit(int docId, String id, Map<String, Object> sourceMap, float score) throws Exception {
        XContentBuilder content = XContentFactory.jsonBuilder();
        content.map(sourceMap);
        SearchHit hit = new SearchHit(docId, id, null, null);
        hit.sourceRef(BytesReference.bytes(content));
        hit.score(score);
        return hit;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        settings = Settings.builder().build();

        // Setup thread context with real instance
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Setup mock container with semantic storage
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test container")
            .memoryStorageConfig(
                MemoryStorageConfig
                    .builder()
                    .memoryIndexName("test-memory-index")
                    .semanticStorageEnabled(true)
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embedding-model-123")
                    .dimension(768)
                    .maxInferSize(5)
                    .build()
            )
            .build();

        // Initialize transport action
        transportSearchMemoriesAction = spy(
            new TransportSearchMemoriesAction(
                transportService,
                actionFilters,
                client,
                connectorAccessControlHelper,
                mlFeatureEnabledSetting,
                xContentRegistry,
                memoryContainerHelper
            )
        );
    }

    @Test
    public void testDoExecute_SuccessWithSemanticSearch() throws Exception {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "machine learning concepts";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query(query).build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);

        // Create source content for the search hit
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "Machine learning is a subset of AI");
        sourceMap.put("session_id", "session-123");
        sourceMap.put("memory_type", "FACT");
        sourceMap.put("created_time", 1700000000000L);
        sourceMap.put("last_updated_time", 1700000000000L);

        // Create a real SearchHit instead of mocking (SearchHit is final class)
        XContentBuilder content = XContentFactory.jsonBuilder();
        content.map(sourceMap);
        SearchHit searchHit = new SearchHit(0, "memory-123", null, null);
        searchHit.sourceRef(BytesReference.bytes(content));
        searchHit.score(0.95f);

        // Create SearchHits with the real SearchHit
        SearchHit[] hits = new SearchHit[] { searchHit };
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.95f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation
        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            // Verify search request
            assertEquals("test-memory-index", request.indices()[0]);
            assertNotNull(request.source());
            // Note: Size limit has been removed in the actual implementation

            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(client, times(1)).search(any(SearchRequest.class), any());

        ArgumentCaptor<MLSearchMemoriesResponse> responseCaptor = ArgumentCaptor.forClass(MLSearchMemoriesResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());

        MLSearchMemoriesResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getHits().size());
        assertEquals("memory-123", response.getHits().get(0).getMemoryId());
        assertEquals(0.95f, response.getHits().get(0).getScore(), 0.001);
    }

    @Test
    public void testDoExecute_SuccessWithoutSemanticSearch() {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "machine learning";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query(query).build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Setup container without semantic storage
        MLMemoryContainer containerWithoutSemantic = MLMemoryContainer
            .builder()
            .name("test-container")
            .memoryStorageConfig(MemoryStorageConfig.builder().memoryIndexName("test-memory-index").semanticStorageEnabled(false).build())
            .build();

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(containerWithoutSemantic);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(containerWithoutSemantic))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation
        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            // Verify it uses match query for non-semantic search
            assertNotNull(request.source());
            assertNotNull(request.source().query());

            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(containerWithoutSemantic));
        verify(client, times(1)).search(any(SearchRequest.class), any());

        ArgumentCaptor<MLSearchMemoriesResponse> responseCaptor = ArgumentCaptor.forClass(MLSearchMemoriesResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());

        MLSearchMemoriesResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(0, response.getHits().size());
    }

    @Test
    public void testDoExecute_GetContainerFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        Exception expectedError = new RuntimeException("Container not found");

        // Mock getMemoryContainer to fail
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onFailure(expectedError);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, never()).checkMemoryContainerAccess(any(), any());
        verify(client, never()).search(any(), any());
        verify(actionListener, times(1)).onFailure(expectedError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess to return false (user is null)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(false);

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(client, never()).search(any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("doesn't have permissions"));
    }

    @Test
    public void testDoExecute_SearchFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        Exception searchError = new RuntimeException("Search failed");

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation to fail
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(searchError);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(client, times(1)).search(any(SearchRequest.class), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        // TransportSearchMemoriesAction wraps the error in an OpenSearchException
        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Search execution failed"));
        assertNotNull(capturedError.getCause());
        assertEquals("Search failed", capturedError.getCause().getMessage());
    }

    @Test
    public void testDoExecute_WithSparseEncoding() {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "test query";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query(query).build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Setup container with sparse encoding
        MLMemoryContainer sparseContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .memoryStorageConfig(
                MemoryStorageConfig
                    .builder()
                    .memoryIndexName("test-memory-index")
                    .semanticStorageEnabled(true)
                    .embeddingModelType(FunctionName.SPARSE_ENCODING)
                    .embeddingModelId("sparse-model-123")
                    .maxInferSize(10)
                    .build()
            )
            .build();

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(sparseContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(sparseContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation
        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            // Verify search request for sparse encoding
            assertEquals("test-memory-index", request.indices()[0]);
            // Note: Size limit has been removed in the actual implementation

            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(sparseContainer));
        verify(client, times(1)).search(any(SearchRequest.class), any());

        ArgumentCaptor<MLSearchMemoriesResponse> responseCaptor = ArgumentCaptor.forClass(MLSearchMemoriesResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_ParseMemorySearchResult() throws Exception {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "test query";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query(query).build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock search response with multiple results
        SearchResponse mockSearchResponse = mock(SearchResponse.class);

        // Create multiple search hits using real SearchHit objects
        Map<String, Object> sourceMap1 = new HashMap<>();
        sourceMap1.put("memory", "First memory");
        sourceMap1.put("session_id", "session-1");
        sourceMap1.put("user_id", "user-1");
        sourceMap1.put("agent_id", "agent-1");
        sourceMap1.put("memory_type", "RAW_MESSAGE");
        sourceMap1.put("role", "user");
        sourceMap1.put("tags", Map.of("key1", "value1"));
        sourceMap1.put("created_time", 1700000000000L);
        sourceMap1.put("last_updated_time", 1700000001000L);

        SearchHit hit1 = createSearchHit(0, "memory-1", sourceMap1, 0.9f);

        Map<String, Object> sourceMap2 = new HashMap<>();
        sourceMap2.put("memory", "Second memory");
        sourceMap2.put("session_id", "session-2");
        sourceMap2.put("memory_type", "FACT");

        SearchHit hit2 = createSearchHit(1, "memory-2", sourceMap2, 0.8f);

        SearchHit[] hits = new SearchHit[] { hit1, hit2 };
        SearchHits searchHits = new SearchHits(hits, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 0.9f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<MLSearchMemoriesResponse> responseCaptor = ArgumentCaptor.forClass(MLSearchMemoriesResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());

        MLSearchMemoriesResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(2, response.getHits().size());

        // Verify first result
        MemorySearchResult result1 = response.getHits().get(0);
        assertEquals("memory-1", result1.getMemoryId());
        assertEquals("First memory", result1.getMemory());
        assertEquals(0.9f, result1.getScore(), 0.001);
        assertEquals("session-1", result1.getSessionId());
        assertEquals("user-1", result1.getUserId());
        assertEquals("agent-1", result1.getAgentId());
        assertEquals(MemoryType.RAW_MESSAGE, result1.getMemoryType());
        assertEquals("user", result1.getRole());
        assertNotNull(result1.getTags());
        assertEquals("value1", result1.getTags().get("key1"));

        // Verify second result
        MemorySearchResult result2 = response.getHits().get(1);
        assertEquals("memory-2", result2.getMemoryId());
        assertEquals("Second memory", result2.getMemory());
        assertEquals(0.8f, result2.getScore(), 0.001);
        assertEquals(MemoryType.FACT, result2.getMemoryType());
    }

    @Test
    public void testDoExecute_TimedOut() {
        // Arrange
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock search response with timeout
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(true);

        // Mock getMemoryContainer with 3 parameters (memoryContainerId, tenantId, listener)
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<MLSearchMemoriesResponse> responseCaptor = ArgumentCaptor.forClass(MLSearchMemoriesResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());

        MLSearchMemoriesResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertTrue(response.isTimedOut());
        assertEquals(0, response.getHits().size());
    }

    @Test
    public void testDoExecute_NullInput() {
        // Arrange
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(null).tenantId(null).build();

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof IllegalArgumentException);
        assertEquals("Search memories input is required", capturedError.getMessage());

        // Verify no other operations were performed
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
        verify(client, never()).search(any(), any());
    }

    @Test
    public void testDoExecute_BlankMemoryContainerId() {
        // Arrange - test with blank (empty string) memory container ID
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId("").query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof IllegalArgumentException);
        assertEquals("Memory container ID is required", capturedError.getMessage());

        // Verify no other operations were performed
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
        verify(client, never()).search(any(), any());
    }

    @Test
    public void testDoExecute_NullMemoryContainerId() {
        // Arrange - test with null memory container ID
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(null).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof IllegalArgumentException);
        assertEquals("Memory container ID is required", capturedError.getMessage());

        // Verify no other operations were performed
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
        verify(client, never()).search(any(), any());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabledWithNullTenantId() {
        // Arrange - when multi-tenancy is enabled, null tenant ID should fail
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(input)
            .tenantId(null)  // null tenant ID when multi-tenancy is enabled
            .build();

        // Mock multi-tenancy to be enabled
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchStatusException);
        assertEquals("You don't have permission to access this resource", capturedError.getMessage());

        // Verify no other operations were performed
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
        verify(client, never()).search(any(), any());
    }

    @Test
    public void testDoExecute_WhitespaceOnlyMemoryContainerId() {
        // Arrange - test with whitespace-only memory container ID
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId("   ").query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof IllegalArgumentException);
        assertEquals("Memory container ID is required", capturedError.getMessage());

        // Verify no other operations were performed
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
        verify(client, never()).search(any(), any());
    }

    @Test
    public void testSearchMemories_ParseSearchResponseFailure() {
        // Arrange - create a scenario where parseSearchResponse throws an exception
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock getMemoryContainer with 3 parameters
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Create a malformed SearchResponse that will cause parseSearchResponse to fail
        // We'll mock a SearchResponse with null hits which should cause NPE in parseSearchResponse
        SearchResponse malformedResponse = mock(SearchResponse.class);
        when(malformedResponse.getHits()).thenReturn(null);  // This will cause NPE in parseSearchResponse

        // Mock search operation to return malformed response
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(malformedResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Failed to parse search response"));
        assertNotNull(capturedError.getCause());

        // Verify that search was called but parsing failed
        verify(client, times(1)).search(any(SearchRequest.class), any());
    }

    @Test
    public void testSearchMemories_SearchHitWithMissingFields() {
        // Arrange - test parseSearchResponse with SearchHits missing required fields
        String memoryContainerId = "container-123";
        MLSearchMemoriesInput input = MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query("test query").build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Create SearchHit with sourceRef that will cause issues during parsing
        SearchHit hitWithBadSource = new SearchHit(1, "mem_1", null, null);
        // Set a sourceRef with invalid JSON that will cause parsing issues
        String invalidJson = "{invalid json}";
        hitWithBadSource.sourceRef(new BytesArray(invalidJson.getBytes()));

        SearchHit[] hits = new SearchHit[] { hitWithBadSource };
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse searchResponseWithBadHit = mock(SearchResponse.class);
        when(searchResponseWithBadHit.getHits()).thenReturn(searchHits);
        when(searchResponseWithBadHit.isTimedOut()).thenReturn(false);
        when(searchResponseWithBadHit.getTook()).thenReturn(new TimeValue(100));

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponseWithBadHit);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Failed to parse search response"));

        // Verify search was called
        verify(client, times(1)).search(any(SearchRequest.class), any());
    }
}
