/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import static org.junit.Assert.assertFalse;
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
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.grpc.converters.ProtoRequestConverter;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelManager;
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
    private MLFeatureEnabledSetting mockFeatureSettings;
    private MLClient mockClient;
    private MLUserContextProvider mockUserContextProvider;
    private MLStreamingService service;
    private MockStreamObserver responseObserver;

    private MockedStatic<ProtoRequestConverter> protoConverterMock;

    @Before
    public void setUp() {
        mockModelManager = mock(MLModelManager.class);
        mockFeatureSettings = mock(MLFeatureEnabledSetting.class);
        mockClient = mock(MLClient.class);
        mockUserContextProvider = mock(MLUserContextProvider.class);
        responseObserver = new MockStreamObserver();

        service = new MLStreamingService(mockModelManager, mockFeatureSettings, mockClient, mockUserContextProvider);

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

        // client.execute() calls back with NOT_FOUND error
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onFailure(new OpenSearchStatusException("Model not found", RestStatus.NOT_FOUND));
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

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

        // client.execute() calls back with generic error
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Connection refused"));
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

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

        // client.execute() calls back with FORBIDDEN (Security Plugin rejection)
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onFailure(new OpenSearchStatusException(
                "User doesn't have privilege to perform this operation on this model", RestStatus.FORBIDDEN));
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

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

        // client.execute() succeeds (no error callback)
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        // Verify client.execute() was called with the correct action
        verify(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());
        assertFalse("Should not send error", responseObserver.errorCalled);
    }

    @Test
    public void testPredictModelStream_accessValidationFailure() {
        when(mockFeatureSettings.isRemoteInferenceEnabled()).thenReturn(true);
        when(mockFeatureSettings.isMultiTenancyEnabled()).thenReturn(false);
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLPredictionTaskRequest mockRequest = createMockPredictRequest("test-model-id");
        protoConverterMock.when(() -> ProtoRequestConverter.toPredictRequest(any(), any())).thenReturn(mockRequest);
        when(mockModelManager.getOptionalModelFunctionName("test-model-id")).thenReturn(Optional.empty());

        // client.execute() calls back with a generic validation error
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Validation error"));
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

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

        // client.execute() succeeds
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());

        service.predictModelStream(mock(MlPredictModelStreamRequest.class), responseObserver);

        verify(mockClient).execute(eq(MLPredictionStreamTaskAction.INSTANCE), any(), any());
        assertFalse("Should not send error", responseObserver.errorCalled);
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
        when(mockUserContextProvider.getUserContext()).thenReturn(null);

        MLExecuteTaskRequest mockRequest = mock(MLExecuteTaskRequest.class);
        protoConverterMock.when(() -> ProtoRequestConverter.toExecuteRequest(any(), any())).thenReturn(mockRequest);

        // client.execute() succeeds
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(mockClient).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());

        service.executeAgentStream(mock(MlExecuteAgentStreamRequest.class), responseObserver);

        verify(mockClient).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        assertFalse("Should not send error", responseObserver.errorCalled);
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
