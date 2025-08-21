/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.FIXED_INDEX_INSIGHT_CONTAINER_ID;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class CreateIndexInsightConfigTransportActionTests extends OpenSearchTestCase {
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
    ActionListener<AcknowledgedResponse> actionListener;

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

    PutIndexInsightConfigTransportAction putIndexInsightConfigTransportAction;
    MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightConfigPutRequest = MLIndexInsightConfigPutRequest
            .builder()
            .containerName("test_index_name")
            .tenantId(null)
            .build();

        putIndexInsightConfigTransportAction = spy(
            new PutIndexInsightConfigTransportAction(
                transportService,
                actionFilters,
                xContentRegistry,
                mlFeatureEnabledSetting,
                client,
                sdkClient,
                mlIndicesHandler
            )
        );

        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testCreateIndexInsightContainer_Successful() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, "test_index");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(createIndexResponse);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof AcknowledgedResponse);
        assertTrue(argumentCaptor.getValue().isAcknowledged());
    }

    @Test
    public void testCreateIndexInsightContainer_SuccessfulToInitSystemIndices() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception(new IndexNotFoundException("not find failure")));

        when(sdkClient.getDataObjectAsync(any())).thenReturn(failedFuture);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, "test_index");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(createIndexResponse);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof AcknowledgedResponse);
        assertTrue(argumentCaptor.getValue().isAcknowledged());
    }

    @Test
    public void testCreateIndexInsightContainer_FailDueToGetObject() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception(new RuntimeException("not find failure")));

        when(sdkClient.getDataObjectAsync(any())).thenReturn(failedFuture);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, "test_index");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(createIndexResponse);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getCause().getMessage(), "not find failure");
    }

    @Test
    public void testCreateIndexInsightContainer_ContainerNotExist() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, "test_index");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(createIndexResponse);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(
            argumentCaptor.getValue().getMessage(),
            "Index insight container is already set. If you want to update, please delete it first."
        );
    }

    @Test
    public void testCreateIndexInsightContainer_FailToIndexContainer() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.NOOP);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        CreateIndexResponse createIndexResponse = new CreateIndexResponse(true, true, "test_index");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(createIndexResponse);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "Failed to create index insight container");
    }

    @Test
    public void testCreateIndexInsightContainer_IndexAlreadyCreated() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        ResourceAlreadyExistsException existsException = mock(ResourceAlreadyExistsException.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(existsException);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof AcknowledgedResponse);
        assertTrue(argumentCaptor.getValue().isAcknowledged());
    }

    @Test
    public void testCreateIndexInsightContainer_FailToCreateIndexContainer() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(FIXED_INDEX_INSIGHT_CONTAINER_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        RuntimeException runtimeException = new RuntimeException("Test Exception");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(runtimeException);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "Test Exception");
    }

}
