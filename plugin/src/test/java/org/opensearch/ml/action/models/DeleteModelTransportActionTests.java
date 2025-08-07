/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.action.models.DeleteModelTransportAction.BULK_FAILURE_MSG;
import static org.opensearch.ml.action.models.DeleteModelTransportAction.OS_STATUS_EXCEPTION_MESSAGE;
import static org.opensearch.ml.action.models.DeleteModelTransportAction.SEARCH_FAILURE_MSG;
import static org.opensearch.ml.action.models.DeleteModelTransportAction.TIMEOUT_MSG;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.*;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.ingest.GetPipelineAction;
import org.opensearch.action.ingest.GetPipelineResponse;
import org.opensearch.action.search.GetSearchPipelineAction;
import org.opensearch.action.search.GetSearchPipelineResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.ScrollableHitSource;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.utils.AgentModelsSearcher;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteModelTransportActionTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    DeleteResponse deleteResponse;

    @Mock
    BulkByScrollResponse bulkByScrollResponse;

    @Mock
    SearchResponse searchResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLModelManager mlModelManager;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    ClusterService clusterService;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    DeleteModelTransportAction deleteModelTransportAction;
    MLModelDeleteRequest mlModelDeleteRequest;
    ThreadContext threadContext;
    MLModel model;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private AgentModelsSearcher agentModelsSearcher;

    @Mock
    private GetSearchPipelineResponse getSearchPipelineResponse;

    @Mock
    private org.opensearch.search.pipeline.PipelineConfiguration searchPipelineConfiguration;

    @Mock
    GetPipelineResponse getIngestionPipelineResponse;

    private BulkByScrollResponse emptyBulkByScrollResponse;

    private Map<String, Object> configDataMap;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        // Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId("test_id").build();

        Settings settings = Settings.builder().put(ML_COMMONS_SAFE_DELETE_WITH_USAGE_CHECK.getKey(), true).build();
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_SAFE_DELETE_WITH_USAGE_CHECK);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        deleteResponse = new DeleteResponse(new ShardId(new Index(ML_MODEL_INDEX, "_na_"), 1), "taskId", 1, 1, 1, true);

        deleteModelTransportAction = spy(
            new DeleteModelTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                settings,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                agentModelsSearcher,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        prepare();
    }

    @Test
    public void testDeleteModel_Success() throws IOException {

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    @Test
    public void testDeleteRemoteModel_Success() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(deleteResponse.getId(), captor.getValue().getId());
        assertEquals(deleteResponse.getResult(), captor.getValue().getResult());
    }

    @Test
    public void testDeleteRemoteModel_deleteModelController_failed() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteLocalModel_deleteModelController_failed() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteRemoteModel_deleteModelChunks_failed() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteHiddenModel_Success() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, true);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(true).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(deleteResponse.getId(), captor.getValue().getId());
        assertEquals(deleteResponse.getResult(), captor.getValue().getResult());
    }

    @Test
    public void testDeleteHiddenModel_NoSuperAdminPermission() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, true);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(false).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModel_Success_AlgorithmNotNull() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(deleteResponse.getId(), captor.getValue().getId());
        assertEquals(deleteResponse.getResult(), captor.getValue().getResult());
    }

    @Test
    public void test_UserHasNoAccessException() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, "modelGroupID", false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModel_CheckModelState() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(MLModelState.DEPLOYING, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testDeleteModel_ModelNotFoundException() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Fail to find model"));
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModel_deleteModelController_ResourceNotFoundException() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener, times(1)).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ValidationFailedException() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteRemoteModel_modelNotFound_ResourceNotFoundException() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("resource not found"));
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue().getMessage().equals("Failed to find model");
    }

    @Test
    public void testDeleteRemoteModel_modelNotFound_RuntimeException() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue().getMessage().equals("Failed to delete data object from index .plugins-ml-model");
    }

    @Test
    public void testModelNotFound_modelChunks_modelController_delete_success() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(null);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModelChunks_Success() {
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<Boolean> deleteChunksListener = mock(ActionListener.class);
        deleteModelTransportAction.deleteModelChunks("test_id", false, deleteChunksListener);
        verify(deleteChunksListener).onResponse(true);
    }

    @Ignore
    public void testDeleteModel_ThreadContextError() {
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("thread context error"));
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("thread context error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailToDeleteModel() {
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<Boolean> deleteChunksListener = mock(ActionListener.class);
        deleteModelTransportAction.deleteModelChunks("test_id", false, deleteChunksListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteChunksListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailToDeleteAllModelChunks() {
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure(ML_MODEL_INDEX, "test_id", new RuntimeException("Error!"));
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(Arrays.asList(failure));
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<Boolean> deleteChunksListener = mock(ActionListener.class);
        deleteModelTransportAction.deleteModelChunks("test_id", false, deleteChunksListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteChunksListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + BULK_FAILURE_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailToDeleteAllModelChunks_TimeOut() {
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure(ML_MODEL_INDEX, "test_id", new RuntimeException("Error!"));
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(Arrays.asList(failure));
        when(bulkByScrollResponse.isTimedOut()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<Boolean> deleteChunksListener = mock(ActionListener.class);
        deleteModelTransportAction.deleteModelChunks("test_id", false, deleteChunksListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteChunksListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + TIMEOUT_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailToDeleteAllModelChunks_SearchFailure() {
        ScrollableHitSource.SearchFailure searchFailure = new ScrollableHitSource.SearchFailure(
            new RuntimeException("error"),
            ML_MODEL_INDEX,
            123,
            "node_id"
        );
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(new ArrayList<>());
        when(bulkByScrollResponse.isTimedOut()).thenReturn(false);
        when(bulkByScrollResponse.getSearchFailures()).thenReturn(Arrays.asList(searchFailure));
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<Boolean> deleteChunksListener = mock(ActionListener.class);
        deleteModelTransportAction.deleteModelChunks("test_id", false, deleteChunksListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteChunksListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + SEARCH_FAILURE_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_BlockedByIngestPipeline() throws IOException {
        Map<String, Object> ingestPipelineConfig1 = Map.of("model_id", "test_id");
        Map<String, Object> ingestPipelineConfig2 = Map.of("nothing", "test_id");
        when(getIngestionPipelineResponse.toString())
            .thenReturn(StringUtils.toJson(Map.of("ingest_1", ingestPipelineConfig1, "ingest_2", ingestPipelineConfig2)));
        // when(getIngestionPipelineResponse.pipelines())
        // .thenReturn(List.of(ingestPipelineConfiguration, independentIngestPipelineConfiguration));
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getIngestionPipelineResponse);
            return null;
        }).when(client).execute(eq(GetPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 ingest pipelines are still using this model, please delete or update the pipelines first: [ingest_1]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_BlockedByAgent() throws IOException {
        XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
        content.startObject();
        content.field(MLAgent.IS_HIDDEN_FIELD, false);
        content.endObject();
        SearchHit hit = new SearchHit(1, "1", null, null).sourceRef(BytesReference.bytes(content));
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(searchResponse.getHits()).thenReturn(searchHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 agents are still using this model, please delete or update the agents first, all visible agents are: [1]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_NoAgentIndex() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(true).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("", ""));
            return null;
        }).when(client).search(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    public void testDeleteModel_failToCheckAgent() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(true).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IllegalArgumentException("fail to search agent index."));
            return null;
        }).when(client).search(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("fail to search agent index.", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_failToCheckPipeline() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(true).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IllegalArgumentException("fail to search pipeline index."));
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("fail to search pipeline index.", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_BlockedBySearchPipelineAndIngestionPipeline() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        when(getSearchPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of("search_1", configDataMap)));
        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getSearchPipelineResponse);
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());

        when(getIngestionPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of("ingest_1", Map.of("model_id", "test_id"))));
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getIngestionPipelineResponse);
            return null;
        }).when(client).execute(eq(GetPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(2)).onFailure(argumentCaptor.capture());
        String totalErrorMessage = argumentCaptor.getValue().getMessage();
        String[] separateErrorMessages = totalErrorMessage.split("\\. ");
        Set<String> generateErrorMessages = new HashSet<>(List.of(separateErrorMessages));
        Set<String> expectedErrorMessages = new HashSet<>(
            List
                .of(
                    "1 ingest pipelines are still using this model, please delete or update the pipelines first: [ingest_1]",
                    "1 search pipelines are still using this model, please delete or update the pipelines first: [search_1]"
                )
        );
        Boolean flag = false;
        for (String errorMessage : generateErrorMessages) {
            if (!expectedErrorMessages.contains(errorMessage)) {
                flag = true;
            }
        }
        assertEquals(flag, false);
    }

    public void testDeleteModel_BlockedBySearchPipeline() throws IOException {
        //org.opensearch.search.pipeline.PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        when(getSearchPipelineResponse.toString()).thenReturn(
                StringUtils.toJson(Map.of("search_1", configDataMap, "indenpendent_search", Map.of("nothing", "nothinh")))
        );

        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getSearchPipelineResponse);
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("1 search pipelines are still using this model, please delete or update the pipelines first: [search_1]", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_BlockedBySearchPipelineSingleModelId() throws IOException {
        Map<String, Object> configDataMapWithSingleModelId = Map
            .of("model_id", "test_id", "list_model_id", List.of("test_id"), "test_map_id", Map.of("map_model_id", "test_id"));

        when(getSearchPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of("search_1", configDataMapWithSingleModelId)));

        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getSearchPipelineResponse);
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 search pipelines are still using this model, please delete or update the pipelines first: [search_1]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_BlockedBySearchPipelineListModelId() throws IOException {
        Map<String, Object> configDataMapWithListModelId = Map
            .of("single_model_id", "test_id", "model_id", List.of("test_id"), "test_map_id", Map.of("map_model_id", "test_id"));
        when(getSearchPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of("search_1", configDataMapWithListModelId)));
        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getSearchPipelineResponse);
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "1 search pipelines are still using this model, please delete or update the pipelines first: [search_1]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_UseSettingToSkipBlockedByIngestPipeline() throws IOException {
        Settings settings = Settings.builder().put(ML_COMMONS_SAFE_DELETE_WITH_USAGE_CHECK.getKey(), false).build();
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_SAFE_DELETE_WITH_USAGE_CHECK);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        deleteModelTransportAction = spy(
            new DeleteModelTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                settings,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                agentModelsSearcher,
                mlFeatureEnabledSetting
            )
        );

        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        Map<String, Object> ingestPipelineConfig1 = Map.of("model_id", "test_id");
        Map<String, Object> ingestPipelineConfig2 = Map.of("nothing", "test_id");
        when(getIngestionPipelineResponse.toString())
            .thenReturn(StringUtils.toJson(Map.of("ingest_1", ingestPipelineConfig1, "ingest_2", ingestPipelineConfig2)));
        // when(getIngestionPipelineResponse.pipelines())
        // .thenReturn(List.of(ingestPipelineConfiguration, independentIngestPipelineConfiguration));
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getIngestionPipelineResponse);
            return null;
        }).when(client).execute(eq(GetPipelineAction.INSTANCE), any(), any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    public GetResponse prepareMLModel(MLModelState mlModelState, String modelGroupID, boolean isHidden) throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .modelState(mlModelState)
            .modelGroupId(modelGroupID)
            .isHidden(isHidden)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .build();
        return buildResponse(mlModel);
    }

    public GetResponse prepareModelWithFunction(MLModelState mlModelState, String modelGroupID, boolean isHidden, FunctionName functionName)
        throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .algorithm(functionName)
            .modelState(mlModelState)
            .modelGroupId(modelGroupID)
            .isHidden(isHidden)
            .build();
        return buildResponse(mlModel);
    }

    private GetResponse buildResponse(MLModel mlModel) throws IOException {
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }

    private void prepare() throws IOException {
        emptyBulkByScrollResponse = new BulkByScrollResponse(new ArrayList<>(), null);
        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(searchResponse.getHits()).thenReturn(hits);
        // when(getIngestionPipelineResponse.pipelines()).thenReturn(List.of());
        when(getIngestionPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of()));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(emptyBulkByScrollResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(), any());

        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getIngestionPipelineResponse);
            return null;
        }).when(client).execute(eq(GetPipelineAction.INSTANCE), any(), any());

        // when(getSearchPipelineResponse.pipelines()).thenReturn(List.of());
        when(getSearchPipelineResponse.toString()).thenReturn(StringUtils.toJson(Map.of()));
        doAnswer(invocation -> {
            ActionListener<GetSearchPipelineResponse> listener = invocation.getArgument(2);
            listener.onResponse(getSearchPipelineResponse);
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(), any());
        configDataMap = Map
            .of("single_model_id", "test_id", "list_model_id", List.of("test_id"), "test_map_id", Map.of("model_id", "test_id"));
        doAnswer(invocation -> new SearchRequest()).when(agentModelsSearcher).constructQueryRequestToSearchModelIdInsideAgent(any());

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
    }
}
