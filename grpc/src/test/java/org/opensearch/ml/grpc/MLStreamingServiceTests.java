/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.grpc.converters.ProtoRequestConverter;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelAccessControlHelper;
import org.opensearch.ml.grpc.interfaces.MLModelManager;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.PredictResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * Unit tests for MLStreamingService.
 */
public class MLStreamingServiceTests {

    private MLModelManager mockModelManager;
    private MLTaskRunner mockPredictTaskRunner;
    private MLTaskRunner mockExecuteTaskRunner;
    private MLFeatureEnabledSetting mockFeatureSettings;
    private MLModelAccessControlHelper mockAccessControlHelper;
    private MLClient mockClient;
    private Object mockSdkClient;
    private MLUserContextProvider mockUserContextProvider;
    private MLStreamingService service;
    private MockStreamObserver responseObserver;

    private MockedStatic<ProtoRequestConverter> protoConverterMock;

    @Before
    public void setUp() {
        mockModelManager = mock(MLModelManager.class);
        mockPredictTaskRunner = mock(MLTaskRunner.class);
        mockExecuteTaskRunner = mock(MLTaskRunner.class);
        mockFeatureSettings = mock(MLFeatureEnabledSetting.class);
        mockAccessControlHelper = mock(MLModelAccessControlHelper.class);
        mockClient = mock(MLClient.class);
        mockSdkClient = new Object();
        mockUserContextProvider = mock(MLUserContextProvider.class);
        responseObserver = new MockStreamObserver();

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(mockClient.getThreadContext()).thenReturn(threadContext);

        service = new MLStreamingService(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings,
            mockAccessControlHelper,
            mockClient,
            mockSdkClient,
            mockUserContextProvider
        );

        protoConverterMock = Mockito.mockStatic(ProtoRequestConverter.class);
    }

    @After
    public void tearDown() {
        if (protoConverterMock != null) {
            protoConverterMock.close();
        }
    }

    @Test
    public void testPredictModelStream_remoteInferenceDisabled() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(false);

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.FAILED_PRECONDITION, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_nonRemoteModelRejected() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.of(FunctionName.KMEANS));

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.INVALID_ARGUMENT, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_modelNotFound() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onFailure(new OpenSearchStatusException("Model not found", RestStatus.NOT_FOUND));
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.NOT_FOUND, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_modelLoadFailure() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Connection refused"));
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.INTERNAL, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_accessDenied() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getModelGroupId()).thenReturn("test-group-id");

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onResponse(mockModel);
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(false);
            return null;
        }).when(mockAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.PERMISSION_DENIED, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_accessGrantedExecutesCB() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getModelGroupId()).thenReturn("test-group-id");

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onResponse(mockModel);
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(mockAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        verify(mockPredictTaskRunner).checkCBAndExecute(eq(FunctionName.REMOTE), any(), any());
    }

    @Test
    public void testPredictModelStream_accessValidationFailure() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getModelGroupId()).thenReturn("test-group-id");

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onResponse(mockModel);
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onFailure(new RuntimeException("Validation error"));
            return null;
        }).when(mockAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.INTERNAL, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_remoteModelAllowed() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.of(FunctionName.REMOTE));

        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getModelGroupId()).thenReturn("test-group-id");

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onResponse(mockModel);
            return null;
        }).when(mockModelManager).getModel(eq("test-model-id"), any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(mockAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        verify(mockPredictTaskRunner).checkCBAndExecute(eq(FunctionName.REMOTE), any(), any());
    }

    @Test
    public void testPredictModelStream_multiTenancyEnabledNoTenantId() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(true);

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.PERMISSION_DENIED, responseObserver.lastError);
    }

    @Test
    public void testPredictModelStream_converterThrowsException() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);

        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any()))
            .thenThrow(new IllegalArgumentException("model_id is required"));

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.INVALID_ARGUMENT, responseObserver.lastError);
    }

    @Test
    public void testExecuteAgentStream_agentFrameworkDisabled() {
        when(mockFeatureSettings.isAgentFrameworkEnabled()).thenReturn(false);

        service.executeAgentStream(mock(MlExecuteAgentStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.FAILED_PRECONDITION, responseObserver.lastError);
    }

    @Test
    public void testExecuteAgentStream_success() {
        when(mockFeatureSettings.isAgentFrameworkEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);

        MLExecuteTaskRequest mockRequest = mock(MLExecuteTaskRequest.class);
        protoConverterMock.when(() -> ProtoRequestConverter.toExecuteRequest(any(), any())).thenReturn(mockRequest);

        service.executeAgentStream(mock(MlExecuteAgentStreamRequest.class), responseObserver);

        verify(mockExecuteTaskRunner).checkCBAndExecute(eq(FunctionName.AGENT), any(), any());
    }

    @Test
    public void testExecuteAgentStream_multiTenancyEnabledNoTenantId() {
        when(mockFeatureSettings.isAgentFrameworkEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(true);

        service.executeAgentStream(mock(MlExecuteAgentStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.PERMISSION_DENIED, responseObserver.lastError);
    }

    @Test
    public void testExecuteAgentStream_converterThrowsException() {
        when(mockFeatureSettings.isAgentFrameworkEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);

        protoConverterMock.when(() -> ProtoRequestConverter.toExecuteRequest(any(), any()))
            .thenThrow(new IllegalArgumentException("agent_id is required"));

        service.executeAgentStream(mock(MlExecuteAgentStreamRequest.class), responseObserver);

        assertTrue("Should send error", responseObserver.errorCalled);
        assertStatusCode(Status.Code.INVALID_ARGUMENT, responseObserver.lastError);
    }

    private MLPredictionTaskRequest createMockPredictRequest(String modelId) {
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(java.util.Map.of("inputs", "test"))
            .build();
        MLInput mlInput = RemoteInferenceMLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();
        return new MLPredictionTaskRequest(modelId, mlInput, null, null);
    }

    private void assertStatusCode(Status.Code expected, Throwable error) {
        assertTrue("Error should be StatusRuntimeException", error instanceof StatusRuntimeException);
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertTrue("Expected " + expected + " but got " + sre.getStatus().getCode(), expected == sre.getStatus().getCode());
    }

    /**
     * Mock StreamObserver for capturing gRPC responses.
     */
    private static class MockStreamObserver implements StreamObserver<PredictResponse> {
        boolean nextCalled = false;
        boolean errorCalled = false;
        boolean completedCalled = false;
        Object lastValue = null;
        Throwable lastError = null;

        @Override
        public void onNext(PredictResponse value) {
            nextCalled = true;
            lastValue = value;
        }

        @Override
        public void onError(Throwable t) {
            errorCalled = true;
            lastError = t;
        }

        @Override
        public void onCompleted() {
            completedCalled = true;
        }
    }
}
