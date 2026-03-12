/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.grpc.adapters.StreamObserverAdapter;
import org.opensearch.ml.grpc.converters.ProtoRequestConverter;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.PredictResponse;
import org.opensearch.protobufs.services.MLServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

/**
 * gRPC service implementation for streaming ML operations.
 *
 * <p>This service provides server-side streaming for:
 * <ul>
 *   <li>Model predictions via PredictModelStream RPC
 *   <li>Agent execution via ExecuteAgentStream RPC
 * </ul>
 *
 * <p>It integrates with OpenSearch's existing ML infrastructure while providing
 * gRPC-based streaming instead of REST SSE.
 */
@Log4j2
public class MLStreamingService extends MLServiceGrpc.MLServiceImplBase {

    // Use Object types to avoid circular dependency with plugin module
    // At runtime, these will be the actual typed objects passed from the plugin
    private final Object client;  // org.opensearch.client.Client
    private final Object modelManager;  // org.opensearch.ml.model.MLModelManager
    private final Object predictTaskRunner;  // org.opensearch.ml.task.MLPredictTaskRunner
    private final Object executeTaskRunner;  // org.opensearch.ml.task.MLExecuteTaskRunner
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Creates a new ML streaming service.
     *
     * @param client OpenSearch client for cluster operations
     * @param modelManager manager for model access and validation
     * @param predictTaskRunner task runner for executing predictions
     * @param executeTaskRunner task runner for executing agent tasks
     * @param mlFeatureEnabledSetting feature flags for ML capabilities
     */
    public MLStreamingService(
        Object client,
        Object modelManager,
        Object predictTaskRunner,
        Object executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.client = client;
        this.modelManager = modelManager;
        this.predictTaskRunner = predictTaskRunner;
        this.executeTaskRunner = executeTaskRunner;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public void predictModelStream(MlPredictModelStreamRequest request, StreamObserver<PredictResponse> responseObserver) {
        try {
            log.error("[GRPC-DEBUG] predictModelStream() called");
            // 1. Validate streaming is enabled
            if (!mlFeatureEnabledSetting.isStreamEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Streaming is disabled").asRuntimeException());
                return;
            }

            // 2. Validate remote inference is enabled
            if (!mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Remote inference is disabled").asRuntimeException());
                return;
            }

            // 3. Convert protobuf request to ML request
            MLPredictionTaskRequest mlRequest = ProtoRequestConverter.toPredictRequest(request);

            // 4. Validate model and execute
            String modelId = mlRequest.getModelId();
            String tenantId = mlRequest.getTenantId();

            // Check if model is in cache
            // Use reflection to avoid compile-time dependency on plugin classes
            java.util.Optional<FunctionName> functionName = getOptionalModelFunctionName(modelId);

            if (functionName.isPresent()) {
                // Model in cache - validate it's a remote model
                if (!FunctionName.REMOTE.equals(functionName.get())) {
                    responseObserver
                        .onError(
                            Status.INVALID_ARGUMENT.withDescription("Streaming is only supported for remote models").asRuntimeException()
                        );
                    return;
                }

                // Execute streaming prediction
                executeStreamingPrediction(mlRequest, responseObserver);
            } else {
                // Model not in cache - load it first
                loadModelAndExecuteStreaming(modelId, tenantId, mlRequest, responseObserver);
            }

        } catch (Exception e) {
            log.error("Error in predictModelStream", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
        }
    }

    @Override
    public void executeAgentStream(MlExecuteAgentStreamRequest request, StreamObserver<PredictResponse> responseObserver) {
        try {
            // 1. Validate streaming is enabled
            if (!mlFeatureEnabledSetting.isStreamEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Streaming is disabled").asRuntimeException());
                return;
            }

            // 2. Validate agent execution is enabled
            if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Agent framework is disabled").asRuntimeException());
                return;
            }

            // 3. Convert protobuf request to ML execute request
            // TODO: Implement ProtoRequestConverter.toExecuteRequest()
            throw new UnsupportedOperationException(
                "Agent execution streaming not yet implemented. " + "Requires ProtoRequestConverter.toExecuteRequest() implementation."
            );

        } catch (Exception e) {
            log.error("Error in executeAgentStream", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
        }
    }

    /**
     * Loads a model from storage and then executes streaming prediction.
     */
    private void loadModelAndExecuteStreaming(
        String modelId,
        String tenantId,
        MLPredictionTaskRequest mlRequest,
        StreamObserver<PredictResponse> responseObserver
    ) {
        ActionListener<MLModel> listener = ActionListener.wrap(mlModel -> {
            try {
                // Validate it's a remote model
                if (!FunctionName.REMOTE.equals(mlModel.getAlgorithm())) {
                    responseObserver
                        .onError(
                            Status.INVALID_ARGUMENT.withDescription("Streaming is only supported for remote models").asRuntimeException()
                        );
                    return;
                }

                // Execute streaming prediction
                executeStreamingPrediction(mlRequest, responseObserver);

            } catch (Exception e) {
                log.error("Error loading model for streaming", e);
                Status status = GrpcStatusMapper.toGrpcStatus(e);
                responseObserver.onError(status.asRuntimeException());
            }
        }, e -> {
            log.error("Failed to load model {}", modelId, e);
            if (e instanceof OpenSearchStatusException && ((OpenSearchStatusException) e).status() == RestStatus.NOT_FOUND) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Model not found: " + modelId).withCause(e).asRuntimeException());
            } else {
                Status status = GrpcStatusMapper.toGrpcStatus(e);
                responseObserver.onError(status.asRuntimeException());
            }
        });

        // Load model with tenant context
        // Use reflection: modelManager.getModel(modelId, tenantId, listener)
        try {
            String resolvedTenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), tenantId);
            java.lang.reflect.Method getModelMethod = modelManager
                .getClass()
                .getMethod("getModel", String.class, String.class, ActionListener.class);
            getModelMethod.invoke(modelManager, modelId, resolvedTenantId, listener);
        } catch (Exception e) {
            log.error("Failed to invoke getModel via reflection", e);
            throw new RuntimeException("Failed to load model", e);
        }
    }

    /**
     * Executes streaming prediction using the task runner.
     */
    private void executeStreamingPrediction(MLPredictionTaskRequest mlRequest, StreamObserver<PredictResponse> responseObserver) {
        try {
            log.debug("[GRPC-DEBUG] executeStreamingPrediction() called");
            // Create adapter that bridges ML streaming to gRPC streaming
            StreamObserverAdapter<PredictResponse> adapter = new StreamObserverAdapter<>(responseObserver, false);
            log.debug("[GRPC-DEBUG] Created StreamObserverAdapter");

            // Set the request to enable streaming mode and use our channel
            mlRequest.setDispatchTask(false); // Force local execution to use our adapter
            mlRequest.setStreamingChannel(adapter.getChannel()); // Set our gRPC channel

            // Execute prediction via task runner using checkCBAndExecute
            // This will call executeTask which will use our streaming channel
            try {
                java.lang.reflect.Method checkCBMethod = predictTaskRunner
                    .getClass()
                    .getSuperclass()
                    .getDeclaredMethod(
                        "checkCBAndExecute",
                        FunctionName.class,
                        org.opensearch.ml.common.transport.MLTaskRequest.class,
                        ActionListener.class
                    );
                checkCBMethod.setAccessible(true);
                log.debug("[GRPC-DEBUG] About to invoke checkCBAndExecute with streaming channel set");
                checkCBMethod.invoke(predictTaskRunner, FunctionName.REMOTE, mlRequest, adapter);
                log.debug("[GRPC-DEBUG] Invoked checkCBAndExecute successfully");
            } catch (java.lang.reflect.InvocationTargetException e) {
                log.error("[GRPC-DEBUG] InvocationTargetException during checkCBAndExecute", e.getCause());
                throw e.getCause() != null ? (Exception) e.getCause() : e;
            } catch (Exception e) {
                log.error("[GRPC-DEBUG] Exception during checkCBAndExecute reflection", e);
                throw e;
            }

        } catch (Exception e) {
            log.error("Error executing streaming prediction", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
        }
    }

    /**
     * Resolves the tenant ID based on multi-tenancy settings.
     */
    private String getTenantID(boolean isMultiTenancyEnabled, String requestTenantId) {
        if (isMultiTenancyEnabled) {
            return requestTenantId;
        }
        return null;
    }

    /**
     * Gets optional model function name from model manager using reflection.
     */
    @SuppressWarnings("unchecked")
    private java.util.Optional<FunctionName> getOptionalModelFunctionName(String modelId) {
        try {
            java.lang.reflect.Method method = modelManager.getClass().getMethod("getOptionalModelFunctionName", String.class);
            return (java.util.Optional<FunctionName>) method.invoke(modelManager, modelId);
        } catch (Exception e) {
            log.error("Failed to invoke getOptionalModelFunctionName via reflection", e);
            return java.util.Optional.empty();
        }
    }
}
