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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
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
    MLUpdateModelInput mockUpdateModelInput;

    @Mock
    MLUpdateModelRequest mockUpdateModelRequest;

    @Mock
    MLModel mockModel;

    @Mock
    MLModelGroup mockModelGroup;

    @Mock
    MLModelManager mlModelManager;

    @Mock
    MLModelGroupManager mlModelGroupManager;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ShardId shardId;

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
            .modelId("test_model_id")
            .name("test_name")
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
                connectorAccessControlHelper,
                modelAccessControlHelper,
                mlModelManager,
                mlModelGroupManager
            )
        );

        MLModel localModel = prepareMLModel(FunctionName.TEXT_EMBEDDING);
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), eq("test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        })
            .when(modelAccessControlHelper)
            .validateModelGroupAccess(any(), eq("updated_test_model_group_id"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        })
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(any(Client.class), eq("updated_test_connector_id"), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(localModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelGroup> listener = invocation.getArgument(1);
            listener.onResponse(mockModelGroup);
            return null;
        }).when(mlModelGroupManager).getModelGroup(eq("updated_test_model_group_id"), isA(ActionListener.class));
    }

    @Test
    public void testUpdateLocalModelSuccess() {
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelStateLoadedException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.LOADED).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStateLoadingException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.LOADING).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStatePartiallyLoadedException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.PARTIALLY_LOADED).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStateDeployedException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.DEPLOYED).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStateDeployingException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.DEPLOYING).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStatePartiallyDeployedException() {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.PARTIALLY_DEPLOYED).when(mockModel).getModelState();

        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLValidationException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithoutRegisterToNewModelGroupSuccess() {
        updateLocalModelRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithLocalInformationSuccess() {
        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationSuccess() {
        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithNoStandAloneConnectorFound() {
        MLModel remoteModelWithInternalConnector = prepareUnsupportedMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModelWithInternalConnector);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "This remote does not have a connector_id field, maybe it uses an internal connector.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlNoPermission() {
        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

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
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlOtherException() {
        MLModel remoteModel = prepareMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

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
    public void testUpdateModelWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model, model ID test_model_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithModelAccessControlOtherException() {
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

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other model access control Exception occurred during update the model. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithRegisterToNewModelGroupModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        })
            .when(modelAccessControlHelper)
            .validateModelGroupAccess(any(), eq("updated_test_model_group_id"), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User Doesn't have privilege to re-link this model to the target model group due to no access to the target model group with model group ID updated_test_model_group_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithRegisterToNewModelGroupModelAccessControlOtherException() {
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

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other model access control Exception occurred during re-linking the model group. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithRegisterToNewModelGroupNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModelGroup> listener = invocation.getArgument(1);
            listener.onFailure(new MLResourceNotFoundException("Model group not found with MODEL_GROUP_ID: updated_test_model_group_id"));
            return null;
        }).when(mlModelGroupManager).getModelGroup(eq("updated_test_model_group_id"), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find the model group with the provided model group id in the update model input, MODEL_GROUP_ID: updated_test_model_group_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelWithModelNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model to update with the provided model id: test_model_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelWithFunctionNameFieldNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModelWithNullFunctionName);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testUpdateLocalModelWithRemoteInformation() {
        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Trying to update the connector or connector_id field on a local model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateLocalModelWithUnsupportedFunction() {
        MLModel localModelWithUnsupportedFunction = prepareUnsupportedMLModel(FunctionName.KMEANS);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(localModelWithUnsupportedFunction);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateRemoteModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this function category: KMEANS",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRequestDocIOException() throws IOException {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.REGISTERED).when(mockModel).getModelState();

        doThrow(new IOException("Exception occurred during building update request.")).when(mockUpdateModelInput).toXContent(any(), any());
        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IOException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred during building update request.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateRequestDocInRegisterToNewModelGroupIOException() throws IOException {
        doReturn(mockUpdateModelInput).when(mockUpdateModelRequest).getUpdateModelInput();
        doReturn("mockId").when(mockUpdateModelInput).getModelId();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mockModel);
            return null;
        }).when(mlModelManager).getModel(eq("mockId"), any(), any(), isA(ActionListener.class));

        doReturn("test_model_group_id").when(mockModel).getModelGroupId();
        doReturn(FunctionName.TEXT_EMBEDDING).when(mockModel).getAlgorithm();
        doReturn(MLModelState.REGISTERED).when(mockModel).getModelState();

        doReturn("mockUpdateModelGroupId").when(mockUpdateModelInput).getModelGroupId();

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), eq("mockUpdateModelGroupId"), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelGroup> listener = invocation.getArgument(1);
            listener.onResponse(mockModelGroup);
            return null;
        }).when(mlModelGroupManager).getModelGroup(eq("mockUpdateModelGroupId"), isA(ActionListener.class));

        doThrow(new IOException("Exception occurred during building update request.")).when(mockUpdateModelInput).toXContent(any(), any());
        transportUpdateModelAction.doExecute(task, mockUpdateModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IOException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred during building update request.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetUpdateResponseListenerWithVersionBumpWrongStatus() {
        UpdateResponse updateWrongResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateWrongResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateWrongResponse);
    }

    @Test
    public void testGetUpdateResponseListenerWithVersionBumpOtherException() {
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

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testGetUpdateResponseListenerWrongStatus() {
        UpdateResponse updateWrongResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateWrongResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));
        updateLocalModelRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateWrongResponse);
    }

    @Test
    public void testGetUpdateResponseListenerOtherException() {
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
        updateLocalModelRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
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
                    .modelGroupId("test_model_group_id")
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
                    .modelGroupId("test_model_group_id")
                    .modelState(MLModelState.REGISTERED)
                    .algorithm(FunctionName.KMEANS)
                    .build();
                return mlModel;
            default:
                throw new IllegalArgumentException("Please choose from FunctionName.REMOTE and FunctionName.KMEANS");
        }
    }
}
