/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.action.models.DeleteModelTransportAction.*;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.ScrollableHitSource;
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

    DeleteModelTransportAction deleteModelTransportAction;
    MLModelDeleteRequest mlModelDeleteRequest;
    ThreadContext threadContext;
    MLModel model;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId("test_id").build();

        Settings settings = Settings.builder().build();
        deleteModelTransportAction = spy(
            new DeleteModelTransportAction(transportService, actionFilters, client, xContentRegistry, clusterService, modelAccessControlHelper)
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Ignore
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

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Ignore
    public void testDeleteModel_CheckModelState() throws IOException {
        GetResponse getResponse = prepareMLModel(MLModelState.DEPLOYING);
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

    @Ignore
    public void testDeleteModel_ModelNotFoundException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception());
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find model", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testDeleteModel_ResourceNotFoundException() throws IOException {
        doAnswer(invocation -> {
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

        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<ResourceNotFoundException> argumentCaptor = ArgumentCaptor.forClass(ResourceNotFoundException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testDeleteModelChunks_Success() {
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        deleteModelTransportAction.deleteModelChunks("test_id", deleteResponse, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Ignore
    public void testDeleteModel_RuntimeException() throws IOException {
        GetResponse getResponse = prepareMLModel(MLModelState.REGISTERED);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testDeleteModel_ThreadContextError() {
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("thread context error"));
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("thread context error", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void test_FailToDeleteModel() {
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).execute(any(), any(), any());

        deleteModelTransportAction.deleteModelChunks("test_id", deleteResponse, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void test_FailToDeleteAllModelChunks() {
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure(ML_MODEL_INDEX, "test_id", new RuntimeException("Error!"));
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(Arrays.asList(failure));
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        deleteModelTransportAction.deleteModelChunks("test_id", deleteResponse, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + BULK_FAILURE_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void test_FailToDeleteAllModelChunks_TimeOut() {
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure(ML_MODEL_INDEX, "test_id", new RuntimeException("Error!"));
        when(bulkByScrollResponse.getBulkFailures()).thenReturn(Arrays.asList(failure));
        when(bulkByScrollResponse.isTimedOut()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        deleteModelTransportAction.deleteModelChunks("test_id", deleteResponse, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + TIMEOUT_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    @Ignore
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

        deleteModelTransportAction.deleteModelChunks("test_id", deleteResponse, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OS_STATUS_EXCEPTION_MESSAGE + ", " + SEARCH_FAILURE_MSG + "test_id", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLModel(MLModelState mlModelState) throws IOException {
        MLModel mlModel = MLModel.builder().modelId("test_id").modelState(mlModelState).build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
