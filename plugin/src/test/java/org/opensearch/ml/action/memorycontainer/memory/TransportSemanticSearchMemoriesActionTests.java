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
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportSemanticSearchMemoriesActionTests extends OpenSearchTestCase {

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

    private TransportSemanticSearchMemoriesAction action;
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

        action = new TransportSemanticSearchMemoriesAction(
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
        MLSemanticSearchMemoriesRequest request = buildRequest("test query", "c1");
        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
    }

    @Test
    public void testDoExecute_NullInput() {
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(null).build();
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_BlankContainerId() {
        MLSemanticSearchMemoriesRequest request = buildRequest("test", "");
        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_NoEmbeddingModel() {
        MLSemanticSearchMemoriesRequest request = buildRequest("test", "c1");
        mockGetContainer("c1", containerWithoutEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("embedding model"));
    }

    @Test
    public void testDoExecute_AccessDenied() {
        MLSemanticSearchMemoriesRequest request = buildRequest("test", "c1");
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(false);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
    }

    @Test
    public void testDoExecute_Success() {
        MLSemanticSearchMemoriesRequest request = buildRequest("test query", "c1");
        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(2);
            l.onResponse(mockResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(), any());

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(mockResponse);
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_SuccessWithFilters() {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test")
            .k(5)
            .namespace(Map.of("user_id", "alice"))
            .tags(Map.of("topic", "food"))
            .minScore(0.5f)
            .build();
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(input).build();

        mockGetContainer("c1", containerWithEmbedding);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        SearchResponse mockResponse = mock(SearchResponse.class);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(2);
            l.onResponse(mockResponse);
            return null;
        }).when(memoryContainerHelper).searchData(any(), any(), any());

        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(mockResponse);
    }

    @Test
    public void testDoExecute_ContainerNotFound() {
        MLSemanticSearchMemoriesRequest request = buildRequest("test", "c1");
        doAnswer(inv -> {
            ActionListener<MLMemoryContainer> l = inv.getArgument(2);
            l.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("c1"), any(), any());

        action.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_BlankEmbeddingModelId() {
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
                    .strategies(null)
                    .build()
            )
            .build();
        mockGetContainer("c1", containerNoStrategies);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        action.doExecute(task, buildRequest("test", "c1"), actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    private MLSemanticSearchMemoriesRequest buildRequest(String query, String containerId) {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput.builder().memoryContainerId(containerId).query(query).build();
        return MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(input).build();
    }

    private void mockGetContainer(String containerId, MLMemoryContainer container) {
        doAnswer(inv -> {
            ActionListener<MLMemoryContainer> l = inv.getArgument(2);
            l.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(containerId), any(), any());
    }
}
