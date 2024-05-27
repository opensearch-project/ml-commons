/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.ScrollableHitSource;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteModelTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    BulkByScrollResponse bulkByScrollResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLModelManager mlModelManager;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    ClusterService clusterService;

    @Mock
    ExecutorService executorService;

    DeleteModelTransportAction deleteModelTransportAction;
    MLModelDeleteRequest mlModelDeleteRequest;
    ThreadContext threadContext;
    MLModel model;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        mlModelDeleteRequest = MLModelDeleteRequest
            .builder()
            .modelId("test_id")
            .retry(false)
            .maxRetry(1)
            .retryDelay(TimeValue.timeValueMillis(100))
            .retryTimeout(TimeValue.timeValueSeconds(30))
            .build();

        Settings settings = Settings.builder().build();
        deleteModelTransportAction = spy(
            new DeleteModelTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlModelManager
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(any())).thenReturn(executorService);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(), any(), any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any());
    }

    public void testDeleteModel_Success() throws IOException {
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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteRemoteModel_Success() throws IOException {
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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteRemoteModel_deleteModelController_failed() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteLocalModel_deleteModelController_failed() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteRemoteModel_deleteModelChunks_failed() throws IOException {
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

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model is not all cleaned up, please try again. Model ID: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteHiddenModel_Success() throws IOException {
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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        doReturn(true).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteHiddenModel_NoSuperAdminPermission() throws IOException {
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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        doReturn(false).when(deleteModelTransportAction).isSuperAdminUserWrapper(clusterService, client);
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_Success_AlgorithmNotNull() throws IOException {
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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void test_UserHasNoAccessException() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, "modelGroupID", false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_CheckModelStateDeploying() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.DEPLOYING, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.DEPLOYING);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStateLoading() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.LOADING, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.LOADING);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStateDeployed() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.DEPLOYED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.DEPLOYED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStateLoaded() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.LOADED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.LOADED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStatePartiallyDeployed() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.PARTIALLY_DEPLOYED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.PARTIALLY_DEPLOYED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStatePartiallyLoaded() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.PARTIALLY_LOADED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.PARTIALLY_LOADED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_CheckModelStateNotFound() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void testDeleteModel_CheckModelStateOtherException() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Some Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Some Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_ModelNotFoundException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Fail to find model"));
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_deleteModelController_ResourceNotFoundException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener, times(1)).onResponse(argumentCaptor.capture());
    }

    public void test_ValidationFailedException() throws IOException {
        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteRemoteModel_modelNotFound_ResourceNotFoundException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("resource not found"));
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue().getMessage().equals("Failed to find model");
    }

    public void testDeleteRemoteModel_modelNotFound_RuntimeException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse response = new BulkByScrollResponse(new ArrayList<>(), null);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareModelWithFunction(MLModelState.REGISTERED, null, false, FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue().getMessage().equals("runtime exception");
    }

    public void testModelNotFound_modelChunks_modelController_delete_success() throws IOException {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

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
        }).when(client).execute(any(), any(), any());
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

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

    public void testDeleteModel_ThreadContextError() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("thread context error");
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("thread context error"));
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
    }

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

    public void testDeleteModel_FailedWithMaxRetry() throws IOException {
        mlModelDeleteRequest = MLModelDeleteRequest
            .builder()
            .modelId("test_id")
            .retry(true)
            .maxRetry(1)
            .retryDelay(TimeValue.timeValueMillis(100))
            .retryTimeout(TimeValue.timeValueSeconds(30))
            .build();

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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.DEPLOYED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(mlModelManager, times(2)).getModelState(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDeleteModel_SuccessWithMaxRetry() throws IOException {
        mlModelDeleteRequest = MLModelDeleteRequest
            .builder()
            .modelId("test_id")
            .retry(true)
            .maxRetry(1)
            .retryDelay(TimeValue.timeValueMillis(100))
            .retryTimeout(TimeValue.timeValueSeconds(30))
            .build();

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
        }).when(client).execute(any(), any(), any());

        MLModel mlModel = prepareMLModel(MLModelState.REGISTERED, null, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.DEPLOYED);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModelState> listener = invocation.getArgument(1);
            listener.onResponse(MLModelState.REGISTERED);
            return null;
        }).when(mlModelManager).getModelState(eq("test_id"), isA(ActionListener.class));

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(mlModelManager, times(2)).getModelState(any(), any());
        verify(actionListener).onResponse(deleteResponse);
    }

    private MLModel prepareMLModel(MLModelState mlModelState, String modelGroupID, boolean isHidden) {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .modelState(mlModelState)
            .modelGroupId(modelGroupID)
            .isHidden(isHidden)
            .build();
        return mlModel;
    }

    private MLModel prepareModelWithFunction(MLModelState mlModelState, String modelGroupID, boolean isHidden, FunctionName functionName) {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .algorithm(functionName)
            .modelState(mlModelState)
            .modelGroupId(modelGroupID)
            .isHidden(isHidden)
            .build();
        return mlModel;
    }

    private GetResponse buildResponse(MLModel mlModel) throws IOException {
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
