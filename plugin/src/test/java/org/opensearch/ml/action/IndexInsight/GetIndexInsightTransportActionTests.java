/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.LOG_RELATED_INDEX_CHECK;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.STATISTICAL_DATA;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetIndexInsightTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionListener<MLIndexInsightGetResponse> actionListener;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private CompletionStage<GetDataObjectResponse> responseCompletionStage;

    @Mock
    private GetDataObjectResponse getDataObjectResponse;

    @Mock
    private Throwable throwable;

    GetIndexInsightTransportAction getIndexInsightTransportAction;
    MLIndexInsightGetRequest mlIndexInsightGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightGetRequest = MLIndexInsightGetRequest
            .builder()
            .indexName("test_index_name")
            .targetIndexInsight(STATISTICAL_DATA)
            .tenantId(null)
            .build();

        getIndexInsightTransportAction = spy(
            new GetIndexInsightTransportAction(
                transportService,
                actionFilters,
                xContentRegistry,
                client,
                sdkClient,
                mlIndicesHandler,
                clusterService
            )
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testGetIndexInsight_Successful() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
        IndexInsightTask indexInsightTask = mock(IndexInsightTask.class);

        doReturn(indexInsightTask).when(getIndexInsightTransportAction).createTask(any());
        IndexInsight insight = new IndexInsight(
            "test_index",
            "test content",
            IndexInsightTaskStatus.COMPLETED,
            STATISTICAL_DATA,
            Instant.ofEpochMilli(0)
        );
        doAnswer(invocation -> {
            ActionListener<IndexInsight> listener = invocation.getArgument(2);
            listener.onResponse(insight);
            return null;
        }).when(indexInsightTask).execute(any(), any(), any());

        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<MLIndexInsightGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getIndexInsight().getIndex(), "test_index");
    }

    @Test
    public void testGetIndexInsight_FailToAccess() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
        IndexInsightTask indexInsightTask = mock(IndexInsightTask.class);

        doReturn(indexInsightTask).when(getIndexInsightTransportAction).createTask(any());
        IndexInsight insight = new IndexInsight(
            "test_index",
            "test content",
            IndexInsightTaskStatus.COMPLETED,
            STATISTICAL_DATA,
            Instant.ofEpochMilli(0)
        );
        doAnswer(invocation -> {
            ActionListener<IndexInsight> listener = invocation.getArgument(2);
            listener.onResponse(insight);
            return null;
        }).when(indexInsightTask).execute(any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IllegalArgumentException("You don't have access"));
            return null;
        }).when(client).search(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof IllegalArgumentException);
        assertEquals("You don't have access to this index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetIndexInsight_ContainerNotInitialized() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
        IndexInsightTask indexInsightTask = mock(IndexInsightTask.class);

        doReturn(indexInsightTask).when(getIndexInsightTransportAction).createTask(any());
        IndexInsight insight = new IndexInsight(
            "test_index",
            "test content",
            IndexInsightTaskStatus.COMPLETED,
            STATISTICAL_DATA,
            Instant.ofEpochMilli(0)
        );
        doAnswer(invocation -> {
            ActionListener<IndexInsight> listener = invocation.getArgument(2);
            listener.onResponse(insight);
            return null;
        }).when(indexInsightTask).execute(any(), any(), any());

        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals("The container is not set yet", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateTask() {
        for (MLIndexInsightType taskType : List.of(FIELD_DESCRIPTION, STATISTICAL_DATA, LOG_RELATED_INDEX_CHECK)) {
            MLIndexInsightGetRequest request = new MLIndexInsightGetRequest("test_index", taskType, null);
            IndexInsightTask task = getIndexInsightTransportAction.createTask(request);
            assertEquals(task.getSourceIndex(), "test_index");
            assertEquals(task.getTaskType(), taskType);
        }

    }

}
