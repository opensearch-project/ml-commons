/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class UpdateModelTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    Task task;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<UpdateResponse> actionListener;

    @Mock
    GetResponse getResponse;

    @Mock
    MLUpdateModelInput mockUpdateModelInput;

    @Mock
    MLUpdateModelRequest mockUpdateModelRequest;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    ClusterService clusterService;

    @Mock
    MLModelManager mlModelManager;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ShardId shardId;

    private SearchResponse searchResponse;

    UpdateResponse updateResponse;

    UpdateModelTransportAction transportUpdateModelAction;

    MLUpdateModelRequest updateLocalModelRequest;

    MLUpdateModelInput updateLocalModelInput;

    MLUpdateModelRequest updateRemoteModelRequest;

    MLUpdateModelInput updateRemoteModelInput;

    MLModel mlModelWithNullFunctionName;

    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        updateLocalModelInput = MLUpdateModelInput
            .builder()
            .modelId("test_model_id")
            .name("updated_test_name")
            .description("updated_test_description")
            .modelGroupId("updated_test_model_group_id")
            .build();
        updateLocalModelRequest = MLUpdateModelRequest.builder().updateModelInput(updateLocalModelInput).build();
        updateRemoteModelInput = MLUpdateModelInput
            .builder()
            .modelId("test_model_id")
            .name("updated_test_name")
            .description("updated_test_description")
            .modelGroupId("updated_test_model_group_id")
            .connectorId("updated_test_connector_id")
            .build();
        updateRemoteModelRequest = MLUpdateModelRequest.builder().updateModelInput(updateRemoteModelInput).build();

        mlModelWithNullFunctionName = MLModel
            .builder()
            .name("test_name")
            .modelId("test_model_id")
            .modelGroupId("test_model_group_id")
            .description("test_description")
            .modelState(MLModelState.REGISTERED)
            .build();

        Settings settings = Settings.builder().build();

        transportUpdateModelAction = spy(
            new UpdateModelTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                connectorAccessControlHelper,
                modelAccessControlHelper,
                mlModelManager
            )
        );

        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
        searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(mlModelManager.getAllModelIds()).thenReturn(new String[] {});
        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
    }

    @Test
    public void testUpdateLocalModelSuccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateLocalModelSuccessWithSearchIndexNotFoundError() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("Index not found!"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateLocalModelWithSearchResponseNotEmpty() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(noneEmptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("ML Model test_model_id is deployed, please undeploy the models first!", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateLocalModelWithSearchResponseOtherException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onFailure(
                    new RuntimeException(
                        "Any other Exception occurred during running SearchResponseListener. Please check log for more details."
                    )
                );
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running SearchResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRequestDocIOException() throws IOException {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();
        doThrow(new IOException("Exception occurred during building update request.")).when(mockUpdateModelInput).toXContent(any(), any());
        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IOException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred during building update request.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelWithoutRelinkModelGroupSuccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        updateLocalModelRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithLocalInformationSuccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        GetResponse getResponse = prepareGetResponse(remoteModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationSuccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        GetResponse getResponse = prepareGetResponse(remoteModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testGetUpdateResponseListenerWrongStatus() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        UpdateResponse updateWrongResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateWrongResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateWrongResponse);
    }

    @Test
    public void testGetUpdateResponseListenerOtherException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener
                .onFailure(
                    new RuntimeException(
                        "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details."
                    )
                );
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithNoStandAloneConnectorFound() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel remoteModelWithInternalConnector = prepareUnsupportedMLModel(FunctionName.REMOTE);
        GetResponse getResponse = prepareGetResponse(remoteModelWithInternalConnector);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "This remote does not have a connector_id field, maybe it uses an internal connector.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlNoPermission() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        GetResponse getResponse = prepareGetResponse(remoteModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have permission to update the connector, connector id: updated_test_connector_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlOtherException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        GetResponse getResponse = prepareGetResponse(remoteModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener
                .onFailure(
                    new RuntimeException("Any other connector access control Exception occurred. Please check log for more details.")
                );
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other connector access control Exception occurred. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithModelAccessControlNoPermission() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model, model ID test_model_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithModelAccessControlOtherException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener
                .onFailure(
                    new RuntimeException(
                        "Any other model access control Exception occurred during update the model. Please check log for more details."
                    )
                );
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other model access control Exception occurred during update the model. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithRelinkModelGroupModelAccessControlNoPermission() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), eq("test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        })
            .when(modelAccessControlHelper)
            .validateModelGroupAccess(any(), eq("updated_test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User Doesn't have privilege to re-link this model to the target model group due to no access to the target model group with model group ID updated_test_model_group_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithRelinkModelGroupModelAccessControlOtherException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), eq("test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener
                .onFailure(
                    new RuntimeException(
                        "Any other model access control Exception occurred during re-linking the model group. Please check log for more details."
                    )
                );
            return null;
        })
            .when(modelAccessControlHelper)
            .validateModelGroupAccess(any(), eq("updated_test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other model access control Exception occurred during re-linking the model group. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithModelNotFound() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model to update with the provided model id: test_model_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelWithFunctionNameFieldNotFound() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        GetResponse getResponse = prepareGetResponse(mlModelWithNullFunctionName);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(2)).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testUpdateLocalModelWithRemoteInformation() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        GetResponse getResponse = prepareGetResponse(localModel);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Trying to update the connector or connector_id field on a local model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateLocalModelWithUnsupportedFunction() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        MLModel localModelWithUnsupportedFunction = prepareUnsupportedMLModel(FunctionName.KMEANS);
        GetResponse getResponse = prepareGetResponse(localModelWithUnsupportedFunction);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this function category: KMEANS",
            argumentCaptor.getValue().getMessage()
        );
    }

    private MLModel prepareMLModel(FunctionName functionName) throws IllegalArgumentException {
        MLModel mlModel;
        switch (functionName) {
            case TEXT_EMBEDDING:
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelGroupId("test_model_group_id")
                    .description("test_description")
                    .modelState(MLModelState.REGISTERED)
                    .algorithm(FunctionName.TEXT_EMBEDDING)
                    .build();
                return mlModel;
            case REMOTE:
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelGroupId("test_model_group_id")
                    .description("test_description")
                    .modelState(MLModelState.REGISTERED)
                    .algorithm(FunctionName.REMOTE)
                    .connectorId("test_connector_id")
                    .build();
                return mlModel;
            default:
                throw new IllegalArgumentException("Please choose from FunctionName.TEXT_EMBEDDING and FunctionName.REMOTE");
        }
    }

    private MLModel prepareUnsupportedMLModel(FunctionName unsupportedCase) throws IllegalArgumentException {
        MLModel mlModel;
        switch (unsupportedCase) {
            case REMOTE:
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .description("test_description")
                    .modelState(MLModelState.REGISTERED)
                    .algorithm(FunctionName.REMOTE)
                    .connector(HttpConnector.builder().name("test_connector").protocol("http").build())
                    .build();
                return mlModel;
            case KMEANS:
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelState(MLModelState.REGISTERED)
                    .algorithm(FunctionName.KMEANS)
                    .build();
                return mlModel;
            default:
                throw new IllegalArgumentException("Please choose from FunctionName.REMOTE and FunctionName.KMEANS");
        }
    }

    private GetResponse prepareGetResponse(MLModel mlModel) throws IOException {
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    private SearchResponse noneEmptySearchResponse() throws IOException {
        String modelContent =
            "{\"model_id\":\"test-model_id\",\"description\":\"description\",\"name\":\"name\",\"model_group_id\":\"modelGroupId\",\"model_config\":"
                + "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
                + "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":\"test-connector_id\"}";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { model }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        return searchResponse;
    }
}
