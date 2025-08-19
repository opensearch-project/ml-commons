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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class DeleteIndexInsightContainerTransportActionTests extends OpenSearchTestCase {
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

    DeleteIndexInsightContainerTransportAction deleteIndexInsightContainerTransportAction;
    MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightContainerDeleteRequest = MLIndexInsightContainerDeleteRequest.builder().tenantId(null).build();

        deleteIndexInsightContainerTransportAction = spy(
            new DeleteIndexInsightContainerTransportAction(
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
    public void testDeleteIndexInsightContainer_Successful() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenReturn(deleteResponse);

        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof AcknowledgedResponse);
        assertTrue(argumentCaptor.getValue().isAcknowledged());
    }

    @Test
    public void testDeleteIndexInsightContainer_FailToGetDeleteResponse() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenThrow(new RuntimeException("fail to get delete response"));

        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to get delete response");
    }

    @Test
    public void testDeleteIndexInsightContainer_FailToGetStashContext() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenThrow(new RuntimeException("fail to get delete response"));
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("fail to get context"));
        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to get context");
    }

    @Test
    public void testDeleteIndexInsightContainer_FailToGetObject() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenThrow(new RuntimeException("fail to get delete response"));
        when(sdkClient.deleteDataObjectAsync(any())).thenThrow(new RuntimeException("fail to get Object"));

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to get Object");
    }

    @Test
    public void testDeleteIndexInsightContainer_ContainerNotSet() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenReturn(deleteResponse);

        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "The container is not set yet");
    }

    @Test
    public void testDeleteIndexInsightContainer_DeleteOriginalIndexFail() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(false));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.OK);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenReturn(deleteResponse);

        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "Failed to delete original index insight data index: test-index");
    }

    @Test
    public void testDeleteIndexInsightContainer_FailToDeleteContainer() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"index_name\": \"test-index\"}");

        GetDataObjectResponse sdkGetResponse = mock(GetDataObjectResponse.class);
        when(sdkGetResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkGetResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        AdminClient adminClient = mock(AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).delete(any(), any());

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.status()).thenReturn(RestStatus.FORBIDDEN);

        DeleteDataObjectResponse sdkDeleteResponse = mock(DeleteDataObjectResponse.class);
        when(sdkDeleteResponse.deleteResponse()).thenReturn(deleteResponse);

        CompletableFuture<DeleteDataObjectResponse> futureDelete = CompletableFuture.completedFuture(sdkDeleteResponse);

        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(futureDelete);

        deleteIndexInsightContainerTransportAction.doExecute(null, mlIndexInsightContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "Fail to delete index insight container");
    }

}
