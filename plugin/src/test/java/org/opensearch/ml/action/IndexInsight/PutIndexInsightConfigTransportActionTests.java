/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_AGENT_NAME;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
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
        when(mlFeatureEnabledSetting.isIndexInsightEnabled()).thenReturn(true);
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

    @Test
    public void testCreateIndexInsightConfig_WithAgentId() {
        MLIndexInsightConfigPutRequest request = new MLIndexInsightConfigPutRequest(false, null, "agent-1");
        mockAgentGet(true);
        mockInitIndices();
        mockPut();

        putIndexInsightConfigTransportAction.doExecute(null, request, actionListener);

        ArgumentCaptor<MLAgentGetRequest> agentGetCaptor = ArgumentCaptor.forClass(MLAgentGetRequest.class);
        verify(client).execute(eq(MLAgentGetAction.INSTANCE), agentGetCaptor.capture(), any());
        assertEquals("agent-1", agentGetCaptor.getValue().getAgentId());
        verify(mlIndicesHandler).initMLConfigIndex(any());

        ArgumentCaptor<PutDataObjectRequest> putCaptor = ArgumentCaptor.forClass(PutDataObjectRequest.class);
        verify(sdkClient, times(2)).putDataObjectAsync(putCaptor.capture());
        PutDataObjectRequest agentPut = putCaptor.getAllValues().get(0);
        assertEquals(ML_CONFIG_INDEX, agentPut.index());
        assertEquals(INDEX_INSIGHT_AGENT_NAME, agentPut.id());
        MLConfig agentConfig = (MLConfig) agentPut.dataObject();
        assertEquals(INDEX_INSIGHT_AGENT_NAME, agentConfig.getType());
        assertEquals("agent-1", agentConfig.getConfiguration().getAgentId());
        assertEquals(ML_INDEX_INSIGHT_CONFIG_INDEX, putCaptor.getAllValues().get(1).index());

        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().isAcknowledged());
    }

    @Test
    public void testCreateIndexInsightConfig_AgentNotFound() {
        MLIndexInsightConfigPutRequest request = new MLIndexInsightConfigPutRequest(true, null, "missing-agent");
        mockAgentGet(false);
        mockInitIndices();
        mockPut();

        putIndexInsightConfigTransportAction.doExecute(null, request, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find agent with the provided agent id: missing-agent", argumentCaptor.getValue().getMessage());
        verify(mlIndicesHandler, never()).initMLConfigIndex(any());
        verify(sdkClient, never()).putDataObjectAsync(any());
    }

    @Test
    public void testCreateIndexInsightConfig_WithAgentId_AgentConfigPutFailure() {
        MLIndexInsightConfigPutRequest request = new MLIndexInsightConfigPutRequest(true, null, "agent-1");
        mockAgentGet(true);
        mockInitIndices();
        when(sdkClient.putDataObjectAsync(any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail to put agent config")));

        putIndexInsightConfigTransportAction.doExecute(null, request, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("fail to put agent config", argumentCaptor.getValue().getMessage());
        verify(sdkClient, times(1)).putDataObjectAsync(any());
        verify(mlIndicesHandler, never()).initMLIndexIfAbsent(eq(MLIndex.INDEX_INSIGHT_CONFIG), any());
    }

    @Test
    public void testCreateIndexInsightConfig_WithoutAgentId_DoesNotTouchAgentConfig() {
        mockInitIndices();
        mockPut();

        putIndexInsightConfigTransportAction.doExecute(null, new MLIndexInsightConfigPutRequest(false, null), actionListener);

        verify(client, never()).execute(eq(MLAgentGetAction.INSTANCE), any(), any());
        verify(mlIndicesHandler, never()).initMLConfigIndex(any());
        ArgumentCaptor<PutDataObjectRequest> putCaptor = ArgumentCaptor.forClass(PutDataObjectRequest.class);
        verify(sdkClient, times(1)).putDataObjectAsync(putCaptor.capture());
        assertEquals(ML_INDEX_INSIGHT_CONFIG_INDEX, putCaptor.getValue().index());
        verify(actionListener).onResponse(any());
    }

    private void mockAgentGet(boolean exists) {
        doAnswer(invocation -> {
            ActionListener<MLAgentGetResponse> listener = invocation.getArgument(2);
            if (exists) {
                listener.onResponse(mock(MLAgentGetResponse.class));
            } else {
                MLAgentGetRequest agentGetRequest = invocation.getArgument(1);
                listener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Failed to find agent with the provided agent id: " + agentGetRequest.getAgentId(),
                            RestStatus.NOT_FOUND
                        )
                    );
            }
            return null;
        }).when(client).execute(eq(MLAgentGetAction.INSTANCE), any(), any());
    }

    private void mockInitIndices() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());
    }

    private void mockPut() {
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        PutDataObjectResponse sdkPutResponse = mock(PutDataObjectResponse.class);
        when(sdkPutResponse.indexResponse()).thenReturn(indexResponse);
        when(sdkClient.putDataObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(sdkPutResponse));
    }

}
