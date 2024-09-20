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
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_FIELD;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
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
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.script.ScriptService;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetTaskTransportActionTests extends OpenSearchTestCase {
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
    private EncryptorImpl encryptor;

    @Mock
    ActionListener<MLTaskGetResponse> actionListener;
    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MLTaskManager mlTaskManager;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetTaskTransportAction getTaskTransportAction;
    MLTaskGetRequest mlTaskGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlTaskGetRequest = MLTaskGetRequest.builder().taskId("test_id").build();

        Settings settings = Settings
            .builder()
            .putList(ML_COMMONS_REMOTE_JOB_STATUS_FIELD.getKey(), List.of("status", "TransformJobStatus"))
            .put(ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX.getKey(), "(complete|completed)")
            .put(ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX.getKey(), "(stopped|cancelled)")
            .put(ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX.getKey(), "(stopping|cancelling)")
            .put(ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX.getKey(), "(expired|timeout)")
            .build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        doReturn(clusterState).when(clusterService).state();
        doReturn(metaData).when(clusterState).metadata();

        doReturn(true).when(metaData).hasIndex(anyString());
        when(clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(
                new ClusterSettings(
                    settings,
                    Set
                        .of(
                            ML_COMMONS_REMOTE_JOB_STATUS_FIELD,
                            ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX,
                            ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX,
                            ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX,
                            ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX
                        )
                )
            );

        getTaskTransportAction = spy(
            new GetTaskTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                clusterService,
                scriptService,
                connectorAccessControlHelper,
                encryptor,
                mlTaskManager,
                mlModelManager,
                settings
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

        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

    }

    public void testGetTask_NullResponse() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
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
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
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
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testGetTask_SuccessBatchPredictStatus() throws IOException {
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

        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
        verify(actionListener).onResponse(any(MLTaskGetResponse.class));
    }

    public void test_BatchPredictStatus_NoConnector() throws IOException {
        Map<String, Object> remoteJob = new HashMap<>();
        remoteJob.put("Status", "IN PROGRESS");
        remoteJob.put("TransformJobName", "SM-offline-batch-transform13");

        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(false);

        GetResponse getResponse = prepareMLTask(FunctionName.REMOTE, MLTaskType.BATCH_PREDICTION, remoteJob);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this connector", argumentCaptor.getValue().getMessage());
    }

    public void test_BatchPredictStatus_NoAccessToConnector() throws IOException {
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

        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get connector", argumentCaptor.getValue().getMessage());
    }

    public void test_BatchPredictStatus_NoModel() throws IOException {
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

        getTaskTransportAction.doExecute(null, mlTaskGetRequest, actionListener);
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

    public void test_processTaskResponse_complete() {
        processTaskResponse("TransformJobStatus", "complete", MLTaskState.COMPLETED);
    }

    public void test_processTaskResponse_cancelling() {
        processTaskResponse("status", "cancelling", MLTaskState.CANCELLING);
    }

    public void test_processTaskResponse_cancelled() {
        processTaskResponse("status", "cancelled", MLTaskState.CANCELLED);
    }

    public void test_processTaskResponse_expired() {
        processTaskResponse("status", "expired", MLTaskState.EXPIRED);
    }

    public void test_processTaskResponse_WrongStatusField() {
        processTaskResponse("wrong_status_field", "expired", null);
    }

    public void test_processTaskResponse_UnknownStatusField() {
        processTaskResponse("status", "unkown_status", null);
    }

    private void processTaskResponse(String statusField, String remoteJobResponseStatus, MLTaskState taskState) {
        String taskId = "testTaskId";
        String remoteJobName = randomAlphaOfLength(5);
        Map<String, Object> remoteJob = new HashMap();
        remoteJob.put(statusField, "running");
        remoteJob.put("name", remoteJobName);
        MLTask mlTask = MLTask
            .builder()
            .taskId(taskId)
            .taskType(MLTaskType.BATCH_PREDICTION)
            .inputType(MLInputDataType.REMOTE)
            .state(MLTaskState.RUNNING)
            .remoteJob(remoteJob)
            .build();
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(Map.of(statusField, remoteJobResponseStatus)).build();
        ModelTensorOutput modelTensorOutput = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build()))
            .build();
        MLTaskResponse taskResponse = MLTaskResponse.builder().output(modelTensorOutput).build();
        ActionListener<MLTaskGetResponse> actionListener = mock(ActionListener.class);
        ArgumentCaptor<Map<String, Object>> updatedTaskCaptor = ArgumentCaptor.forClass(Map.class);

        getTaskTransportAction.processTaskResponse(mlTask, taskId, taskResponse, mlTask.getRemoteJob(), actionListener);

        verify(mlTaskManager).updateMLTaskDirectly(any(), updatedTaskCaptor.capture(), any());
        Map<String, Object> updatedTask = updatedTaskCaptor.getValue();
        assertEquals(taskState, updatedTask.get("state"));
        Map<String, Object> updatedRemoteJob = (Map<String, Object>) updatedTask.get("remote_job");
        assertEquals(remoteJobResponseStatus, updatedRemoteJob.get(statusField));
        assertEquals(remoteJobName, updatedRemoteJob.get("name"));
    }
}
