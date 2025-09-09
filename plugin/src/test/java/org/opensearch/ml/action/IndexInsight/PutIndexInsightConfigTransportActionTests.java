/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;

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
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLIndex;
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

public class PutIndexInsightConfigTransportActionTests extends OpenSearchTestCase {
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

    private User user;

    PutIndexInsightConfigTransportAction putIndexInsightConfigTransportAction;
    MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightConfigPutRequest = MLIndexInsightConfigPutRequest.builder().isEnable(true).tenantId(null).build();

        putIndexInsightConfigTransportAction = spy(
            new PutIndexInsightConfigTransportAction(
                transportService,
                actionFilters,
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
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin|role-1|all_access");

    }

    @Test
    public void testCreateIndexInsightConfig_Successful() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
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
    public void testCreateIndexInsightConfig_SuccessfulToInitSystemIndices() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
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
    public void testCreateIndexInsightConfig_FailDueToInitIndices() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("fail to create index"));
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
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
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to create index");
    }

    @Test
    public void testCreateIndexInsightConfig_FailToIndexConfig() {
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
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
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
        assertEquals(argumentCaptor.getValue().getMessage(), "Failed to create index insight config");
    }

    @Test
    public void testCreateIndexInsightConfig_IndexAlreadyCreated() {
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
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
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
    public void testCreateIndexInsightConfig_FailToCreateStorageIndex() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.INDEX_INSIGHT_CONFIG), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.completedFuture(sdkPutResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(putFuture);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Test Exception"));
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.INDEX_INSIGHT_STORAGE), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "Test Exception");
    }

    @Test
    public void testCreateIndexInsightConfig_FailToPutObject() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        CompletableFuture<PutDataObjectResponse> putFuture = CompletableFuture.failedFuture(new RuntimeException("fail to put object"));
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
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to put object");
    }

    @Test
    public void testCreateIndexInsightConfig_FailToGetIndexResponse() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(indexResponse.getId()).thenReturn(DEFAULT_TENANT_ID);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenThrow(new RuntimeException("fail to get index response"));
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
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to get index response");
    }

    @Test
    public void testCreateIndexInsightConfig_FailToMultiTenant() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals(argumentCaptor.getValue().getMessage(), "You don't have permission to access this resource");
    }

    @Test
    public void testCreateIndexInsightConfig_FailDueToWrongUser() {
        ThreadContext threadContext1 = new ThreadContext(Settings.builder().build());
        when(threadPool.getThreadContext()).thenReturn(threadContext1);
        threadContext1.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin|role-1|null");

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

        putIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigPutRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            argumentCaptor.getValue().getMessage(),
            "You don't have permission to put index insight config. Please contact admin user."
        );
    }

}
