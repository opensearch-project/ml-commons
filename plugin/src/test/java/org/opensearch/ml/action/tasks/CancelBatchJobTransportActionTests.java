/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobRequest;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobResponse;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.script.ScriptService;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class CancelBatchJobTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    private ClusterService clusterService;
    @Mock
    private ScriptService scriptService;
    @Mock
    ClusterState clusterState;

    @Mock
    private Metadata metaData;

    @Mock
    ActionFilters actionFilters;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private EncryptorImpl encryptor;

    @Mock
    ActionListener<MLCancelBatchJobResponse> actionListener;
    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLResourceSharingExtension mlResourceSharingExtension;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    CancelBatchJobTransportAction cancelBatchJobTransportAction;
    MLCancelBatchJobRequest mlCancelBatchJobRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlCancelBatchJobRequest = MLCancelBatchJobRequest.builder().taskId("test_id").build();

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        doReturn(clusterState).when(clusterService).state();
        doReturn(metaData).when(clusterState).metadata();

        doReturn(true).when(metaData).hasIndex(anyString());

        cancelBatchJobTransportAction = spy(
            new CancelBatchJobTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                clusterService,
                scriptService,
                connectorAccessControlHelper,
                modelAccessControlHelper,
                encryptor,
                mlTaskManager,
                mlModelManager,
                mlFeatureEnabledSetting
            )
        );

        MLModel mlModel = mock(MLModel.class);

        Connector connector = HttpConnector
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
                            .actionType(ConnectorAction.ActionType.BATCH_PREDICT_STATUS)
                            .method("POST")
                            .url("https://api.sagemaker.us-east-1.amazonaws.com/DescribeTransformJob")
                            .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                            .requestBody("{ \"TransformJobName\" : \"${parameters.TransformJobName}\"}")
                            .build()
                    )
            )
            .build();

        when(mlModel.getConnectorId()).thenReturn("testConnectorID");

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("testModelID"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());
        when(mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()).thenReturn(true);
    }

    public void testGetTask_NullResponse() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).get(any(), any());
        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_IndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index Not Found"));
            return null;
        }).when(client).get(any(), any());
        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_FeatureFlagDisabled() throws IOException {
        Map<String, Object> remoteJob = new HashMap<>();
        remoteJob.put("Status", "IN PROGRESS");
        remoteJob.put("TransformJobName", "SM-offline-batch-transform13");

        GetResponse getResponse = prepareMLTask(FunctionName.REMOTE, MLTaskType.BATCH_PREDICTION, remoteJob);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        when(mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()).thenReturn(false);
        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<IllegalStateException> argumentCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Offline Batch Inference is currently disabled. To enable it, update the setting \"plugins.ml_commons.offline_batch_inference_enabled\" to true.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Ignore
    public void testGetTask_SuccessBatchPredictCancel() throws IOException {
        Map<String, Object> remoteJob = new HashMap<>();
        remoteJob.put("Status", "IN PROGRESS");
        remoteJob.put("TransformJobName", "SM-offline-batch-transform13");

        GetResponse getResponse = prepareMLTask(FunctionName.REMOTE, MLTaskType.BATCH_PREDICTION, remoteJob);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(Map.of("TransformJobStatus", "COMPLETED")).build();
        ModelTensorOutput modelTensorOutput = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build()))
            .build();

        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        verify(actionListener).onResponse(any(MLCancelBatchJobResponse.class));
    }

    public void test_BatchPredictCancel_NoModelGroupAccess() throws IOException {
        Map<String, Object> remoteJob = new HashMap<>();
        remoteJob.put("Status", "IN PROGRESS");
        remoteJob.put("TransformJobName", "SM-offline-batch-transform13");

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any());

        GetResponse getResponse = prepareMLTask(FunctionName.REMOTE, MLTaskType.BATCH_PREDICTION, remoteJob);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to cancel this batch job", argumentCaptor.getValue().getMessage());
    }

    public void test_BatchPredictStatus_NoConnectorFound() throws IOException {
        Map<String, Object> remoteJob = new HashMap<>();
        remoteJob.put("Status", "IN PROGRESS");
        remoteJob.put("TransformJobName", "SM-offline-batch-transform13");

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onFailure(new ResourceNotFoundException("Failed to get connector"));
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        GetResponse getResponse = prepareMLTask(FunctionName.REMOTE, MLTaskType.BATCH_PREDICTION, remoteJob);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        cancelBatchJobTransportAction.doExecute(null, mlCancelBatchJobRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get connector", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLTask(FunctionName functionName, MLTaskType mlTaskType, Map<String, Object> remoteJob) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId("taskID")
            .modelId("testModelID")
            .functionName(functionName)
            .taskType(mlTaskType)
            .remoteJob(remoteJob)
            .build();
        XContentBuilder content = mlTask.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
