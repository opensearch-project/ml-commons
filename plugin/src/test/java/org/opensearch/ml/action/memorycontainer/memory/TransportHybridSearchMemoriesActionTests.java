/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportHybridSearchMemoriesActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Client client;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private MemoryContainerHelper memoryContainerHelper;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private Task task;
    @Mock
    private ActionListener<SearchResponse> actionListener;

    private TransportHybridSearchMemoriesAction action;
    private MLMemoryContainer containerWithEmbedding;
    private MLMemoryContainer containerWithoutEmbedding;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        ArrayList<MemoryStrategy> strategies = new ArrayList<>();
        strategies.add(MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).build());

        containerWithEmbedding = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embed-123")
                    .llmId("test-llm-id")
                    .dimension(1024)
                    .strategies(strategies)
                    .build()
            )
            .build();

        containerWithoutEmbedding = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(MemoryConfiguration.builder().indexPrefix("test").build())
            .build();

        action = new TransportHybridSearchMemoriesAction(
            transportService,
            actionFilters,
            client,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    @Test
    public void testDoExecute_FeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        MLHybridSearchMemoriesRequest request = buildRequest("test", "c1");
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_NullInput() {
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(null).build();
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_BlankContainerId() {
        MLHybridSearchMemoriesRequest request = buildRequest("test", " ");
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_NoEmbeddingModel() {
        MLHybridSearchMemoriesRequest request = buildRequest("test", "c1");
        mockGetContainer("c1", containerWithoutEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("embedding model"));
    }

    @Test
    public void testDoExecute_AccessDenied() {
        MLHybridSearchMemoriesRequest request = buildRequest("test", "c1");
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(false);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
    }

    @Test
    public void testDoExecute_Success() {
        MLHybridSearchMemoriesRequest request = buildRequest("test query", "c1");
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(mockResponse);
        verify(client).search(argThat(req -> {
            assertTrue(req.indices()[0].contains("test-memory-long-term"));
            // Inline pipeline — no named pipeline, but searchPipelineSource should be set
            assertNull(req.pipeline());
            assertNotNull(req.source().searchPipelineSource());
            Map<String, Object> pipelineSource = req.source().searchPipelineSource();
            assertTrue(pipelineSource.containsKey("phase_results_processors"));
            // Verify weights are Double (not Float) — OpenSearch ConfigurationUtils requires Double
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> processors = (List<Map<String, Object>>) pipelineSource.get("phase_results_processors");
            @SuppressWarnings("unchecked")
            Map<String, Object> normProcessor = (Map<String, Object>) processors.get(0).get("normalization-processor");
            @SuppressWarnings("unchecked")
            Map<String, Object> combination = (Map<String, Object>) normProcessor.get("combination");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) combination.get("parameters");
            @SuppressWarnings("unchecked")
            List<Object> weights = (List<Object>) parameters.get("weights");
            assertTrue("weights must be Double not Float", weights.get(0) instanceof Double);
            assertEquals(0.5, (Double) weights.get(0), 0.001);
            assertEquals(0.5, (Double) weights.get(1), 0.001);
            return true;
        }), any());
    }

    @Test
    public void testDoExecute_NoStrategies() {
        MLMemoryContainer containerNoStrategies = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embed-123")
                    .dimension(1024)
                    .strategies(new ArrayList<>())
                    .build()
            )
            .build();

        MLHybridSearchMemoriesRequest request = buildRequest("test", "c1");
        mockGetContainer("c1", containerNoStrategies);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("memory strategies"));
    }

    @Test
    public void testDoExecute_Success_WithSystemIndex() {
        // Container with useSystemIndex = true
        ArrayList<MemoryStrategy> strategies = new ArrayList<>();
        strategies.add(MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).build());
        MLMemoryContainer sysContainer = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embed-123")
                    .llmId("test-llm-id")
                    .dimension(1024)
                    .strategies(strategies)
                    .useSystemIndex(true)
                    .build()
            )
            .build();

        MLHybridSearchMemoriesRequest request = buildRequest("test query", "c1");
        mockGetContainer("c1", sysContainer);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(mockResponse);
    }

    @Test
    public void testDoExecute_ContainerNotFound() {
        MLHybridSearchMemoriesRequest request = buildRequest("test", "c1");
        doAnswer(inv -> {
            ActionListener<MLMemoryContainer> l = inv.getArgument(2);
            l.onFailure(new OpenSearchStatusException("Not found", RestStatus.NOT_FOUND));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("c1"), any(), any());

        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_SearchFailure() {
        MLHybridSearchMemoriesRequest request = buildRequest("test query", "c1");
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("search failed"));
            return null;
        }).when(client).search(any(), any());

        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any());
    }

    @Test
    public void testDoExecute_WithMinScoreAndFilter() {
        // Covers minScore != null and filter != null branches in executeHybridSearch
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test")
            .k(5)
            .minScore(0.5f)
            .filter(org.opensearch.index.query.QueryBuilders.termQuery("strategy_type", "SEMANTIC"))
            .build();
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(input).build();

        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());

        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(mockResponse);
    }

    @Test
    public void testDoExecute_BlankEmbeddingModelId() {
        // Covers isBlank(embeddingModelId) branch
        MLMemoryContainer containerBlankModelId = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("")
                    .dimension(1024)
                    .strategies(new ArrayList<>(List.of(MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).build())))
                    .build()
            )
            .build();
        mockGetContainer("c1", containerBlankModelId);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_SystemIndex_SearchFailure_WrapsException() {
        // Verifies error wrapping is consistent between system-index and non-system-index paths
        ArrayList<MemoryStrategy> strategies = new ArrayList<>();
        strategies.add(MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).build());
        MLMemoryContainer sysContainer = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embed-123")
                    .llmId("test-llm-id")
                    .dimension(1024)
                    .strategies(strategies)
                    .useSystemIndex(true)
                    .build()
            )
            .build();

        mockGetContainer("c1", sysContainer);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("search failed"));
            return null;
        }).when(client).search(any(), any());

        action.doExecute(task, buildRequest("test", "c1"), actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchException);
        assertTrue(captor.getValue().getMessage().contains("Hybrid search execution failed"));
    }

    @Test
    public void testDoExecute_NullMemoryConfig() {
        // Covers memoryConfig == null branch
        MLMemoryContainer containerNullConfig = MLMemoryContainer.builder().name("test").configuration(null).build();
        mockGetContainer("c1", containerNullConfig);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_EmptyStrategies() {
        // Covers strategies.isEmpty() branch
        MLMemoryContainer containerEmptyStrategies = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embed-123")
                    .dimension(1024)
                    .strategies(new ArrayList<>())
                    .build()
            )
            .build();
        mockGetContainer("c1", containerEmptyStrategies);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_NonEmptyPostFilter_Applied() {
        // Covers postFilter with non-empty filter clauses (BoolQueryBuilder with filters)
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test")
            .namespace(Map.of("user_id", "alice"))
            .build();
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(input).build();
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());
        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(mockResponse);
    }

    @Test
    public void testDoExecute_TenantValidationFails() {
        // Covers validateTenantId returning false branch (line 82)
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest
            .builder()
            .mlHybridSearchMemoriesInput(MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build())
            .tenantId(null)
            .build();
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any());
    }

    @Test
    public void testDoExecute_NullEmbeddingModelType() {
        // Covers memoryConfig.getEmbeddingModelType() == null branch (line 101)
        // Use a container with no embedding config at all (type is null)
        MLMemoryContainer containerNullType = MLMemoryContainer
            .builder()
            .name("test")
            .configuration(MemoryConfiguration.builder().indexPrefix("test").build())
            .build();
        mockGetContainer("c1", containerNullType);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_NonAdminUser_OwnerFilterApplied() {
        // Covers isAdminUser(null) == false branch — user is null, isAdminUser returns false
        when(memoryContainerHelper.isAdminUser(isNull())).thenReturn(false);
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), any())).thenReturn(true);
        mockGetContainer("c1", containerWithEmbedding);
        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onResponse(mockResponse);
    }

    @Test
    public void testDoExecute_AdminUser_NoOwnerFilter() {
        // Covers isAdminUser(null) == true branch — admin user, ownerId stays null
        when(memoryContainerHelper.isAdminUser(isNull())).thenReturn(true);
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), any())).thenReturn(true);
        mockGetContainer("c1", containerWithEmbedding);
        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mockResponse);
            return null;
        }).when(client).search(any(), any());
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onResponse(mockResponse);
    }

    private MLHybridSearchMemoriesRequest buildRequest(String query, String containerId) {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId(containerId).query(query).build();
        return MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(input).build();
    }

    private void mockGetContainer(String containerId, MLMemoryContainer container) {
        doAnswer(inv -> {
            ActionListener<MLMemoryContainer> l = inv.getArgument(2);
            l.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(containerId), any(), any());
    }
}
