/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.common.transport.update_cache.MLUpdateModelCacheNodesResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

public class UpdateModelTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;
    private SdkClient sdkClient;

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
    MLModelManager mlModelManager;

    @Mock
    MLModelGroupManager mlModelGroupManager;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    MLUpdateModelCacheNodesResponse updateModelCacheNodesResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ShardId shardId;

    private Settings settings;

    Connector testConnector;

    UpdateResponse updateResponse;

    UpdateModelTransportAction transportUpdateModelAction;

    MLUpdateModelRequest updateLocalModelRequest;

    MLUpdateModelInput updateLocalModelInput;

    MLModel mlModelWithNullFunctionName;

    MLModel localModel;

    ThreadContext threadContext;

    @Mock
    ClusterState clusterState;

    @Mock
    ClusterService clusterService;

    @Mock
    MLEngine mlEngine;

    @Mock
    NamedXContentRegistry xContentRegistry;

    private static final List<String> TRUSTED_CONNECTOR_ENDPOINTS_REGEXES = ImmutableList.of("^https://api\\.test\\.com/.*$");

    private static TestThreadPool testThreadPool = new TestThreadPool(
        UpdateModelTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        updateLocalModelInput = MLUpdateModelInput
            .builder()
            .modelId("test_model_id")
            .name("updated_test_name")
            .description("updated_test_description")
            .modelGroupId("updated_test_model_group_id")
            .build();
        updateLocalModelRequest = MLUpdateModelRequest.builder().updateModelInput(updateLocalModelInput).build();

        mlModelWithNullFunctionName = MLModel
            .builder()
            .modelId("test_model_id")
            .name("test_name")
            .modelGroupId("test_model_group_id")
            .description("test_description")
            .modelState(MLModelState.REGISTERED)
            .build();

        settings = Settings
            .builder()
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_CONNECTOR_ENDPOINTS_REGEXES)
            .build();

        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX);

        InetAddress inetAddress1 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 1 });
        InetAddress inetAddress2 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 2 });

        DiscoveryNode node1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(inetAddress1, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNode node2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(inetAddress2, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node1).add(node2).build();
        String[] targetNodeIds = new String[] { node1.getId(), node2.getId() };

        localModel = prepareMLModel("TEXT_EMBEDDING");
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterState.nodes()).thenReturn(nodes);
        when(mlModelManager.getWorkerNodes("test_model_id", FunctionName.REMOTE)).thenReturn(targetNodeIds);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);

        transportUpdateModelAction = spy(
            new UpdateModelTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                connectorAccessControlHelper,
                modelAccessControlHelper,
                mlModelManager,
                mlModelGroupManager,
                settings,
                clusterService,
                mlEngine,
                mlFeatureEnabledSetting
            )
        );

        testConnector = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.test.com/v1/test")
                            .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                            .requestBody("{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                            .build()
                    )
            )
            .build();

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
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), isA(ActionListener.class));

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

        MLModelGroup modelGroup = MLModelGroup
            .builder()
            .modelGroupId("updated_test_model_group_id")
            .name("test")
            .description("this is test group")
            .latestVersion(1)
            .backendRoles(Arrays.asList("role1", "role2"))
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();

        GetResponse getResponse = prepareGetResponse(modelGroup);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(mlModelGroupManager).getModelGroupResponse(eq("updated_test_model_group_id"), isA(ActionListener.class));
    }

    @Test
    public void testUpdateLocalModelSuccess() {
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelWithoutRegisterToNewModelGroupSuccess() {
        updateLocalModelRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelWithRegisterToSameModelGroupSuccess() {
        updateLocalModelRequest.getUpdateModelInput().setModelGroupId("test_model_group_id");
        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateRemoteModelWithLocalInformationSuccess() {
        MLModel remoteModel = prepareMLModel("REMOTE_EXTERNAL");
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateExternalRemoteModelWithExternalRemoteInformationSuccess() {
        MLModel remoteModel = prepareMLModel("REMOTE_EXTERNAL");
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateInternalRemoteModelWithInternalRemoteInformationSuccess() {
        MLModel remoteModel = prepareMLModel("REMOTE_INTERNAL");
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_INTERNAL"), actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateHiddenRemoteModelWithRemoteInformationSuccess() {
        MLModel remoteModel = prepareMLModel("REMOTE_INTERNAL", true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));
        doReturn(true).when(transportUpdateModelAction).isSuperAdminUserWrapper(clusterService, client);
        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_INTERNAL"), actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateHiddenRemoteModelPermissionError() {
        MLModel remoteModel = prepareMLModel("REMOTE_INTERNAL", true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));
        doReturn(false).when(transportUpdateModelAction).isSuperAdminUserWrapper(clusterService, client);
        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_INTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateRemoteModelWithNoExternalConnectorFound() {
        MLModel remoteModelWithInternalConnector = prepareUnsupportedMLModel(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModelWithInternalConnector);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "This remote does not have a connector_id field, maybe it uses an internal connector.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlNoPermission() {
        MLModel remoteModel = prepareMLModel("REMOTE_EXTERNAL");
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have permission to update the connector, connector id: updated_test_connector_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateRemoteModelWithRemoteInformationWithConnectorAccessControlOtherException() {
        MLModel remoteModel = prepareMLModel("REMOTE_EXTERNAL");
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(remoteModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(5);
            listener
                .onFailure(
                    new RuntimeException("Any other connector access control Exception occurred. Please check log for more details.")
                );
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any(), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
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
        }).when(mlModelGroupManager).getModelGroupResponse(eq("updated_test_model_group_id"), isA(ActionListener.class));

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
    public void testUpdateLocalModelWithExternalRemoteInformation() {
        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Trying to update the connector or connector_id field on a local model.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateLocalModelWithInternalRemoteInformation() {
        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_INTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Trying to update the connector or connector_id field on a local model.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateLocalModelWithUnsupportedFunction() {
        MLModel localModelWithUnsupportedFunction = prepareUnsupportedMLModel(FunctionName.KMEANS);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(localModelWithUnsupportedFunction);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, prepareRemoteRequest("REMOTE_EXTERNAL"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("The function category KMEANS is not supported at this time.", argumentCaptor.getValue().getMessage());
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

        MLModelGroup modelGroup = MLModelGroup
            .builder()
            .modelGroupId("updated_test_model_group_id")
            .name("test")
            .description("this is test group")
            .latestVersion(1)
            .backendRoles(Arrays.asList("role1", "role2"))
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();

        GetResponse getResponse = prepareGetResponse(modelGroup);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(mlModelGroupManager).getModelGroupResponse(eq("mockUpdateModelGroupId"), isA(ActionListener.class));

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
    public void testGetUpdateResponseListenerWithNullUpdateResponse() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update ML model: test_model_id", argumentCaptor.getValue().getMessage());
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

    @Test
    public void testUpdateModelStateDeployingException() {
        MLModel testDeployingModel = prepareMLModel("TEXT_EMBEDDING", MLModelState.DEPLOYING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testDeployingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model is deploying. Please wait for the model to complete deployment. model ID test_model_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelStateLoadingException() {
        MLModel testDeployingModel = prepareMLModel("TEXT_EMBEDDING", MLModelState.LOADING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testDeployingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, updateLocalModelRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model is deploying. Please wait for the model to complete deployment. model ID test_model_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelCacheModelStateDeployedSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelWithIsModelEnabledSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setConnector(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setIsEnabled(true);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelWithoutUpdateConnectorWithRateLimiterSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();
        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setRateLimiter(rateLimiter);
        testUpdateModelCacheRequest.getUpdateModelInput().setConnector(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelWithRateLimiterSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();
        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setRateLimiter(rateLimiter);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelWithPartialRateLimiterSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").build();
        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setRateLimiter(rateLimiter);
        testUpdateModelCacheRequest.getUpdateModelInput().setConnector(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelWithPartialRateLimiterSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").build();
        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        testUpdateModelCacheRequest.getUpdateModelInput().setRateLimiter(rateLimiter);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheUpdateResponseListenerWithNullUpdateResponse() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        when(updateModelCacheNodesResponse.failures()).thenReturn(null);

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update ML model: test_model_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelCacheModelWithUndeploySuccessEmptyFailures() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        when(updateModelCacheNodesResponse.failures()).thenReturn(new ArrayList<>());

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithUndeploySuccessPartiallyFailures() {
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));

        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        when(updateModelCacheNodesResponse.failures()).thenReturn(failures);

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update ML model index with model ID test_model_id but update model cache was failed on following nodes [foo1], please retry or redeploy model manually.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUndeployNullResponse() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update ML model index with model ID test_model_id but update model cache was failed on following nodes [foo1, foo2], please retry or redeploy model manually.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUndeployOtherException() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelCacheModelStateDeployedWrongStatus() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        UpdateResponse updateWrongResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateWrongResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateWrongResponse);
    }

    @Test
    public void testUpdateModelCacheModelStateDeployedUpdateModelCacheException() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener
                .onFailure(
                    new RuntimeException(
                        "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details."
                    )
                );
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelCacheModelStateDeployedUpdateException() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
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

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelCacheModelRegisterToNewModelGroupSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelRegisterToNewModelGroupWrongStatus() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        UpdateResponse updateWrongResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateWrongResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateWrongResponse);
    }

    @Test
    public void testUpdateModelCacheModelRegisterToNewModelGroupUpdateModelCacheException() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener
                .onFailure(
                    new RuntimeException(
                        "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details."
                    )
                );
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelCacheModelRegisterToNewModelGroupUpdateException() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.DEPLOYED);
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

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during running getUpdateResponseListener. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelCacheModelStateLoadedSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.LOADED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelStatePartiallyDeployedSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.PARTIALLY_DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelCacheModelStatePartiallyLoadedSuccess() {
        MLModel testUpdateModelCacheModel = prepareMLModel("REMOTE_INTERNAL", MLModelState.PARTIALLY_LOADED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(testUpdateModelCacheModel);
            return null;
        }).when(mlModelManager).getModel(eq("test_model_id"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLUpdateModelCacheNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateModelCacheNodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        MLUpdateModelRequest testUpdateModelCacheRequest = prepareRemoteRequest("REMOTE_INTERNAL");
        testUpdateModelCacheRequest.getUpdateModelInput().setModelGroupId(null);
        transportUpdateModelAction.doExecute(task, testUpdateModelCacheRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    // TODO: Add UT to make sure that version incremented successfully.
    private MLModel prepareMLModel(String functionName, MLModelState modelState, boolean isHidden) throws IllegalArgumentException {
        MLModel mlModel;
        switch (functionName) {
            case "TEXT_EMBEDDING":
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelGroupId("test_model_group_id")
                    .description("test_description")
                    .modelState(modelState)
                    .algorithm(FunctionName.TEXT_EMBEDDING)
                    .isHidden(isHidden)
                    .build();
                return mlModel;
            case "REMOTE_EXTERNAL":
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelGroupId("test_model_group_id")
                    .description("test_description")
                    .modelState(modelState)
                    .algorithm(FunctionName.REMOTE)
                    .connectorId("test_connector_id")
                    .isHidden(isHidden)
                    .build();
                return mlModel;
            case "REMOTE_INTERNAL":
                mlModel = MLModel
                    .builder()
                    .name("test_name")
                    .modelId("test_model_id")
                    .modelGroupId("test_model_group_id")
                    .description("test_description")
                    .modelState(modelState)
                    .algorithm(FunctionName.REMOTE)
                    .connector(testConnector)
                    .isHidden(isHidden)
                    .build();
                return mlModel;
            default:
                throw new IllegalArgumentException("Please choose from TEXT_EMBEDDING, REMOTE_EXTERNAL, or REMOTE_INTERNAL");
        }
    }

    private MLModel prepareMLModel(String functionName, MLModelState modelState) throws IllegalArgumentException {
        return prepareMLModel(functionName, modelState, false);
    }

    private MLModel prepareMLModel(String functionName, boolean isHidden) throws IllegalArgumentException {
        return prepareMLModel(functionName, MLModelState.REGISTERED, isHidden);
    }

    private MLModel prepareMLModel(String functionName) throws IllegalArgumentException {
        return prepareMLModel(functionName, MLModelState.REGISTERED, false);
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

    private MLUpdateModelRequest prepareRemoteRequest(String remoteRequestType) throws IllegalArgumentException {
        MLUpdateModelInput updateRemoteModelInput;
        switch (remoteRequestType) {
            case "REMOTE_EXTERNAL":
                updateRemoteModelInput = MLUpdateModelInput
                    .builder()
                    .modelId("test_model_id")
                    .name("updated_test_name")
                    .description("updated_test_description")
                    .modelGroupId("updated_test_model_group_id")
                    .connectorId("updated_test_connector_id")
                    .build();
                return MLUpdateModelRequest.builder().updateModelInput(updateRemoteModelInput).build();
            case "REMOTE_INTERNAL":
                MLCreateConnectorInput updateContent = MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .version("1")
                    .description("updated description")
                    .build();
                updateRemoteModelInput = MLUpdateModelInput
                    .builder()
                    .modelId("test_model_id")
                    .name("updated_test_name")
                    .description("updated_test_description")
                    .modelGroupId("updated_test_model_group_id")
                    .connector(updateContent)
                    .build();
                return MLUpdateModelRequest.builder().updateModelInput(updateRemoteModelInput).build();
            default:
                throw new IllegalArgumentException("Please choose from REMOTE_EXTERNAL or REMOTE_INTERNAL");
        }
    }

    private GetResponse prepareGetResponse(MLModelGroup mlModelGroup) throws IOException {
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    @Ignore
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
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Ignore
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
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "ML Model mockId is in deploying or deployed state, please undeploy the models first!",
            argumentCaptor.getValue().getMessage()
        );
    }
}
