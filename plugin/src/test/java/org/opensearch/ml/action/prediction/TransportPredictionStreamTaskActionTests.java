/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.breaker.CircuitBreaker;
import org.opensearch.core.common.breaker.CircuitBreakingException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportPredictionStreamTaskActionTests extends OpenSearchTestCase {
    @Mock
    private MLPredictTaskRunner mlPredictTaskRunner;

    @Mock
    private TransportService transportService;

    @Mock
    private MLModelCacheHelper modelCacheHelper;

    @Mock
    private Client client;
    SdkClient sdkClient;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private ActionListener<MLTaskResponse> actionListener;

    @Mock
    private MLModel model;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    ActionFilters actionFilters;

    MLPredictionTaskRequest mlPredictionTaskRequest;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private TransportPredictionStreamTaskAction transportPredictionStreamTaskAction;

    ThreadContext threadContext;

    @Mock
    ThreadPool threadPool;

    private MLInput mlInput;

    @Mock
    private StreamTransportService streamTransportService;

    @Mock
    private TransportChannel transportChannel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        User user = User.parse("admin|role-1|all_access");

        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(KMeansParams.builder().centroids(1).build())
            .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
            .build();

        mlPredictionTaskRequest = MLPredictionTaskRequest.builder().modelId("test_id").mlInput(mlInput).user(user).build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        Settings settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE.getKey(), true).build();
        ClusterSettings clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE)));

        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(true);

        transportPredictionStreamTaskAction = spy(
            new TransportPredictionStreamTaskAction(
                transportService,
                actionFilters,
                modelCacheHelper,
                mlPredictTaskRunner,
                clusterService,
                client,
                sdkClient,
                mlModelManager,
                modelAccessControlHelper,
                mlFeatureEnabledSetting,
                settings,
                streamTransportService
            )
        );
    }

    @Test
    public void testGetStreamTransportService() {
        StreamTransportService result = TransportPredictionStreamTaskAction.getStreamTransportService();
        assertNotNull(result);
    }

    @Test
    public void testMessageReceived() {
        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.messageReceived(mlPredictionTaskRequest, transportChannel, task);

        verify(transportPredictionStreamTaskAction).doExecute(eq(task), eq(mlPredictionTaskRequest), any(), eq(transportChannel));
    }

    @Test
    public void testDoExecuteWithoutChannel() {
        transportPredictionStreamTaskAction.doExecute(null, mlPredictionTaskRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof UnsupportedOperationException);
        assertEquals("Use doExecute with TransportChannel for streaming requests", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteWithAccessDenied() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.REMOTE);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
    }

    @Test
    public void testDoExecuteLocalModelNotSupported() {
        when(modelCacheHelper.getModelInfo(anyString())).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        when(model.getModelGroupId()).thenReturn("test_group_id");
        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(false);

        transportPredictionStreamTaskAction.doExecute(null, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof UnsupportedOperationException);
        assertEquals("Streaming is not supported for local model.", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteOpenSearchStatusException() {
        when(modelCacheHelper.getModelInfo(anyString())).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.KMEANS);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onFailure(new OpenSearchStatusException("Testing OpenSearchStatusException", RestStatus.BAD_REQUEST));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ((ActionListener<MLTaskResponse>) invocation.getArguments()[3]).onResponse(null);
            return null;
        }).when(mlPredictTaskRunner).run(any(), any(), any(), any());

        transportPredictionStreamTaskAction.doExecute(null, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Testing OpenSearchStatusException", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecuteMLResourceNotFoundException() {
        when(modelCacheHelper.getModelInfo(anyString())).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.KMEANS);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onFailure(new MLResourceNotFoundException("Testing MLResourceNotFoundException"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ((ActionListener<MLTaskResponse>) invocation.getArguments()[3]).onResponse(null);
            return null;
        }).when(mlPredictTaskRunner).run(any(), any(), any(), any());

        transportPredictionStreamTaskAction.doExecute(null, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(MLResourceNotFoundException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Testing MLResourceNotFoundException", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteMLLimitExceededException() {
        when(modelCacheHelper.getModelInfo(anyString())).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onFailure(new CircuitBreakingException("Memory Circuit Breaker is open, please check your resources!", CircuitBreaker.Durability.TRANSIENT));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ((ActionListener<MLTaskResponse>) invocation.getArguments()[3]).onResponse(null);
            return null;
        }).when(mlPredictTaskRunner).run(any(), any(), any(), any());

        transportPredictionStreamTaskAction.doExecute(null, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(CircuitBreakingException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Memory Circuit Breaker is open, please check your resources!", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteModelDisabled() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(modelCacheHelper.getIsModelEnabled("test_id")).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals("Model is disabled.", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteModelRateLimited() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        when(modelCacheHelper.getIsModelEnabled("test_id")).thenReturn(true);
        when(modelCacheHelper.getRateLimiter("test_id")).thenReturn(mock(org.opensearch.common.util.TokenBucket.class));
        when(modelCacheHelper.getRateLimiter("test_id").request()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals("Request is throttled at model level.", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteUserRateLimited() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        when(modelCacheHelper.getIsModelEnabled("test_id")).thenReturn(true);
        when(modelCacheHelper.getRateLimiter("test_id")).thenReturn(mock(org.opensearch.common.util.TokenBucket.class));
        when(modelCacheHelper.getRateLimiter("test_id").request()).thenReturn(true);
        when(modelCacheHelper.getUserRateLimiter("test_id", "admin")).thenReturn(mock(org.opensearch.common.util.TokenBucket.class));
        when(modelCacheHelper.getUserRateLimiter("test_id", "admin").request()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals("Request is throttled at user level. If you think there's an issue, please contact your cluster admin.", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteDLModelNotSupported() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        when(modelCacheHelper.getIsModelEnabled("test_id")).thenReturn(true);
        when(modelCacheHelper.getRateLimiter("test_id")).thenReturn(mock(org.opensearch.common.util.TokenBucket.class));
        when(modelCacheHelper.getRateLimiter("test_id").request()).thenReturn(true);
        when(modelCacheHelper.getUserRateLimiter("test_id", "admin")).thenReturn(mock(org.opensearch.common.util.TokenBucket.class));
        when(modelCacheHelper.getUserRateLimiter("test_id", "admin").request()).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals("Non-streaming requests are not supported by the streaming transport action", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteStreamExecution() {
        when(modelCacheHelper.getModelInfo("test_id")).thenReturn(model);
        when(model.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(modelCacheHelper.getIsModelEnabled("test_id")).thenReturn(true);
        when(modelCacheHelper.getOptionalFunctionName("test_id")).thenReturn(java.util.Optional.of(FunctionName.REMOTE));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(3);
            listener.onResponse(mock(MLTaskResponse.class));
            return null;
        }).when(mlPredictTaskRunner).run(any(), any(), any(), any());

        Task task = mock(Task.class);
        transportPredictionStreamTaskAction.doExecute(task, mlPredictionTaskRequest, actionListener, transportChannel);

        verify(mlPredictTaskRunner).run(any(), any(), any(), any());
        verify(modelCacheHelper).addPredictRequestDuration(eq("test_id"), any(Double.class));
        verify(modelCacheHelper).refreshLastAccessTime("test_id");
    }

    @Test
    public void testValidateInputSchemaSuccess() {
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(
                Map
                    .of(
                        "messages",
                        "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"You are a helpful assistant.\\\"},"
                            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hello!\\\"}]"
                    )
            )
            .build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
        Map<String, String> modelInterface = Map
            .of(
                "input",
                "{\"properties\":{\"parameters\":{\"properties\":{\"messages\":{"
                    + "\"description\":\"This is a test description field\",\"type\":\"string\"}}}}}"
            );
        when(modelCacheHelper.getModelInterface(any())).thenReturn(modelInterface);
        transportPredictionStreamTaskAction.validateInputSchema("testId", mlInput);
    }

    @Test
    public void testValidateInputSchemaFailed() {
        exceptionRule.expect(OpenSearchStatusException.class);
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(
                Map
                    .of(
                        "messages",
                        "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"You are a helpful assistant.\\\"},"
                            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hello!\\\"}]"
                    )
            )
            .build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
        Map<String, String> modelInterface = Map
            .of(
                "input",
                "{\"properties\":{\"parameters\":{\"properties\":{\"messages\":{"
                    + "\"description\":\"This is a test description field\",\"type\":\"integer\"}}}}}"
            );
        when(modelCacheHelper.getModelInterface(any())).thenReturn(modelInterface);
        transportPredictionStreamTaskAction.validateInputSchema("testId", mlInput);
    }
}
