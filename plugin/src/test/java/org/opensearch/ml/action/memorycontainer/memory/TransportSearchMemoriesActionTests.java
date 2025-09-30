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
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
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
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private Task task;

    @Mock
    private ActionListener<SearchResponse> actionListener;

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

        // Mock ML feature settings
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Setup mock container with semantic storage
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory-index")
                    .disableHistory(true)
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
                memoryContainerHelper
            )
        );
    }

    @Test
    public void testDoExecute_SuccessWithSemanticSearch() throws Exception {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "machine learning concepts";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", query));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());

        SearchResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getHits().getHits().length);
        assertEquals("memory-123", response.getHits().getHits()[0].getId());
        assertEquals(0.95f, response.getHits().getHits()[0].getScore(), 0.001);
    }

    @Test
    public void testDoExecute_SuccessWithoutSemanticSearch() {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "machine learning";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", query));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Setup container without semantic storage
        MLMemoryContainer containerWithoutSemantic = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory-index").disableHistory(false).build())
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(containerWithoutSemantic));
        verify(memoryContainerHelper, times(1)).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());

        SearchResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(0, response.getHits().getHits().length);
    }

    @Test
    public void testDoExecute_GetContainerFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
        verify(memoryContainerHelper, never()).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());
        verify(actionListener, times(1)).onFailure(expectedError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
        verify(memoryContainerHelper, never()).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(searchError);
            return null;
        }).when(memoryContainerHelper).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", query));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Setup container with sparse encoding
        MLMemoryContainer sparseContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory-index")
                    .disableHistory(true)
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any(), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(sparseContainer));
        verify(memoryContainerHelper, times(1)).searchData(any(MemoryConfiguration.class), any(SearchDataObjectRequest.class), any());

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_ParseMemorySearchResult() throws Exception {
        // Arrange
        String memoryContainerId = "container-123";
        String query = "test query";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", query));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());

        SearchResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(2, response.getHits().getHits().length);

        // Verify first result
        hit1 = response.getHits().getHits()[0];
        assertEquals("memory-1", hit1.getId());
        assertEquals(0.9f, hit1.getScore(), 0.001);
        Map<String, Object> source1 = hit1.getSourceAsMap();
        assertEquals("First memory", source1.get("memory"));
        assertEquals("session-1", source1.get("session_id"));
        assertEquals("user-1", source1.get("user_id"));
        assertEquals("agent-1", source1.get("agent_id"));
        assertEquals("RAW_MESSAGE", source1.get("memory_type"));
        assertEquals("user", source1.get("role"));

        // Verify second result
        hit2 = response.getHits().getHits()[1];
        assertEquals("memory-2", hit2.getId());
        assertEquals(0.8f, hit2.getScore(), 0.001);
        Map<String, Object> source2 = hit2.getSourceAsMap();
        assertEquals("Second memory", source2.get("memory"));
        assertEquals("session-2", source2.get("session_id"));
        assertEquals("FACT", source2.get("memory_type"));
    }

    @Test
    public void testDoExecute_TimedOut() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());

        SearchResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertTrue(response.isTimedOut());
        assertEquals(0, response.getHits().getHits().length);
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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("")
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(null)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("   ")
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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
    public void testSearchMemories_SearchHitWithMissingFields() {
        // Arrange - test parseSearchResponse with SearchHits missing required fields
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
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

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Test exception"));

        // Verify search was called
        verify(memoryContainerHelper, times(1)).searchData(any(), any(SearchDataObjectRequest.class), any());
    }

    @Test
    public void testDoExecute_FeatureDisabled() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Agentic Memory APIs are not enabled"));

        // Verify no container access was attempted
        verify(memoryContainerHelper, never()).getMemoryContainer(any(), any(), any());
    }

    @Test
    public void testDoExecute_NonAdminUserAppliesOwnerFilter() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock non-admin user
        org.opensearch.commons.authuser.User nonAdminUser = new org.opensearch.commons.authuser.User(
            "regular-user",
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyMap()
        );
        when(memoryContainerHelper.isAdminUser(nonAdminUser)).thenReturn(false);

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - verify addOwnerIdFilter was called for non-admin user
        verify(memoryContainerHelper, times(1)).addOwnerIdFilter(isNull(), any(SearchSourceBuilder.class));
        verify(actionListener, times(1)).onResponse(any());
    }

    @Test
    public void testDoExecute_WithSpecificMemoryType() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .memoryType("working")  // Specify working memory type
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<SearchDataObjectRequest> requestCaptor = ArgumentCaptor.forClass(SearchDataObjectRequest.class);
        verify(memoryContainerHelper, times(1)).searchData(any(), requestCaptor.capture(), any());

        SearchDataObjectRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        // Verify the request was created with the specific memory type index
        assertNotNull(capturedRequest.indices());
    }

    @Test
    public void testDoExecute_ExceptionDuringSearchExecution() {
        // Arrange
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation to throw an exception
        doAnswer(invocation -> { throw new RuntimeException("Unexpected error during search"); })
            .when(memoryContainerHelper)
            .searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Failed to build search request"));
    }

    @Test
    public void testDoExecute_AdminUserDoesNotApplyOwnerFilter() {
        // Arrange - test that admin users do NOT get owner ID filter applied
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock admin user
        org.opensearch.commons.authuser.User adminUser = new org.opensearch.commons.authuser.User(
            "admin-user",
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyMap()
        );
        when(memoryContainerHelper.isAdminUser(isNull())).thenReturn(true);

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - verify addOwnerIdFilter was NOT called for admin user
        verify(memoryContainerHelper, never()).addOwnerIdFilter(any(), any(SearchSourceBuilder.class));
        verify(actionListener, times(1)).onResponse(any());
    }

    @Test
    public void testDoExecute_WithTenantId() {
        // Arrange - test search with explicit tenant ID
        String memoryContainerId = "container-123";
        String tenantId = "tenant-456";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(tenantId).build();

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        when(mockSearchResponse.isTimedOut()).thenReturn(false);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), eq(tenantId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), eq(tenantId), any());
        ArgumentCaptor<SearchDataObjectRequest> requestCaptor = ArgumentCaptor.forClass(SearchDataObjectRequest.class);
        verify(memoryContainerHelper, times(1)).searchData(any(), requestCaptor.capture(), any());

        SearchDataObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(tenantId, capturedRequest.tenantId());
        verify(actionListener, times(1)).onResponse(any());
    }

    @Test
    public void testDoExecute_SearchHitWithInvalidMemoryType() {
        // Test the catch block for invalid memory type parsing (lines 200-204)
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Create search hit with invalid memory type to test the catch block (though it just logs warning)
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "test content");
        sourceMap.put("memory_type", "INVALID_TYPE");  // Invalid memory type to test catch block

        try {
            XContentBuilder content = XContentFactory.jsonBuilder();
            content.map(sourceMap);
            SearchHit hit = new SearchHit(0, "mem-1", null, null);
            hit.sourceRef(BytesReference.bytes(content));
            hit.score(0.9f);

            SearchHit[] hits = new SearchHit[] { hit };
            SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.9f);
            when(mockSearchResponse.getHits()).thenReturn(searchHits);
        } catch (Exception e) {
            fail("Failed to create test data: " + e.getMessage());
        }

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - should still call onResponse even with invalid memory type (just logs warning)
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testDoExecute_BuildSearchRequestException() {
        // Test the catch block in searchMemories method (lines 157-160)
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock memoryConfig.getIndexName() to throw exception
        MemoryConfiguration badConfig = mock(MemoryConfiguration.class);
        when(badConfig.getIndexName(any())).thenThrow(new RuntimeException("Index name error"));
        MLMemoryContainer badContainer = MLMemoryContainer
            .builder()
            .name("bad-container")
            .configuration(badConfig)
            .build();

        // Mock getMemoryContainer to return bad container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(badContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(badContainer))).thenReturn(true);

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - should call onFailure due to exception building search request
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof org.opensearch.OpenSearchException);
        assertTrue(capturedError.getMessage().contains("Failed to build search request"));
    }

    @Test
    public void testDoExecute_WithNonAdminUserAppliesOwnerFilter() {
        // Test that non-admin users get owner filter applied (line 131-133)
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock isAdminUser to return false
        when(memoryContainerHelper.isAdminUser(isNull())).thenReturn(false);

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - verify addOwnerIdFilter was called for non-admin user
        verify(memoryContainerHelper, times(1)).addOwnerIdFilter(isNull(), any(SearchSourceBuilder.class));
        verify(actionListener, times(1)).onResponse(any());
    }

    @Test
    public void testDoExecute_WithAdminUserSkipsOwnerFilter() {
        // Test that admin users do NOT get owner filter applied (line 131 - false branch)
        String memoryContainerId = "container-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test"));
        MLSearchMemoriesInput input = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLSearchMemoriesRequest searchRequest = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(input).tenantId(null).build();

        // Mock admin user
        when(memoryContainerHelper.isAdminUser(isNull())).thenReturn(true);

        // Mock search response
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any(), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock search operation
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(SearchDataObjectRequest.class), any());

        // Act
        transportSearchMemoriesAction.doExecute(task, searchRequest, actionListener);

        // Assert - verify addOwnerIdFilter was NOT called for admin user
        verify(memoryContainerHelper, never()).addOwnerIdFilter(any(), any(SearchSourceBuilder.class));
        verify(actionListener, times(1)).onResponse(any());
    }
}
