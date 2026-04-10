/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.util.Optional;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.grpc.adapters.StreamObserverAdapter;
import org.opensearch.ml.grpc.converters.ProtoRequestConverter;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelAccessControlHelper;
import org.opensearch.ml.grpc.interfaces.MLModelManager;
import org.opensearch.ml.grpc.interfaces.MLSdkClient;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.PredictResponse;
import org.opensearch.protobufs.services.MLServiceGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

/**
 * gRPC service implementation for streaming ML operations.
 * This service provides server-side streaming for:
 * - Model predictions via PredictModelStream RPC
 * - Agent execution via ExecuteAgentStream RPC
 */
@Log4j2
public class MLStreamingService extends MLServiceGrpc.MLServiceImplBase {

    // Use interfaces to avoid circular dependency with plugin module
    private final MLModelManager modelManager;
    private final MLTaskRunner predictTaskRunner;
    private final MLTaskRunner executeTaskRunner;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLModelAccessControlHelper modelAccessControlHelper;
    private final MLClient client;
    private final MLSdkClient sdkClient;
    private final MLUserContextProvider userContextProvider;

    /**
     * Creates a new ML streaming service.
     *
     * @param modelManager manager for model access and validation
     * @param predictTaskRunner task runner for executing predictions
     * @param executeTaskRunner task runner for executing agent tasks
     * @param mlFeatureEnabledSetting feature flags for ML capabilities
     * @param modelAccessControlHelper helper for validating model access control
     * @param client OpenSearch client for validation
     * @param sdkClient SDK client for multi-tenant operations
     * @param userContextProvider provider for extracting user from security context
     */
    public MLStreamingService(
        MLModelManager modelManager,
        MLTaskRunner predictTaskRunner,
        MLTaskRunner executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelAccessControlHelper modelAccessControlHelper,
        MLClient client,
        MLSdkClient sdkClient,
        MLUserContextProvider userContextProvider
    ) {
        this.modelManager = modelManager;
        this.predictTaskRunner = predictTaskRunner;
        this.executeTaskRunner = executeTaskRunner;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.client = client;
        this.sdkClient = sdkClient;
        this.userContextProvider = userContextProvider;
    }

    @Override
    public void predictModelStream(MlPredictModelStreamRequest request, StreamObserver<PredictResponse> responseObserver) {
        try {
            // Validate remote inference is enabled
            if (!mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Remote inference is disabled").asRuntimeException());
                return;
            }

            // Extract tenant ID from gRPC metadata
            String tenantId = extractTenantId();

            // Convert protobuf request to ML request with tenant ID
            MLPredictionTaskRequest mlRequest = ProtoRequestConverter.toPredictRequest(request, tenantId);

            // Validate model and execute
            String modelId = mlRequest.getModelId();
            Optional<FunctionName> functionName = getOptionalModelFunctionName(modelId);
            if (functionName.isPresent() && !FunctionName.REMOTE.equals(functionName.get())) {
                responseObserver
                    .onError(Status.INVALID_ARGUMENT.withDescription("Streaming is only supported for remote models").asRuntimeException());
                return;
            }

            // Extract user from context
            User user = getUserFromContext();
            mlRequest.setUser(user);

            loadModelValidateAndExecuteStreaming(modelId, tenantId, mlRequest, user, responseObserver);
        } catch (Exception e) {
            handleError(responseObserver, "predictModelStream", e);
        }
    }

    /**
     * Loads a model, validates access, and then executes streaming prediction.
     */
    private void loadModelValidateAndExecuteStreaming(
        String modelId,
        String tenantId,
        MLPredictionTaskRequest mlRequest,
        User user,
        StreamObserver<PredictResponse> responseObserver
    ) {
        try {
            ThreadContext.StoredContext context = client.getThreadContext().stashContext();
            ActionListener<MLModel> modelListener = ActionListener.wrap(mlModel -> {
                try {
                    validateModelGroupAccessAndExecute(mlModel, mlRequest, user, tenantId, responseObserver);
                } catch (Exception e) {
                    handleError(responseObserver, "validating model access", e);
                } finally {
                    closeContextSafely(context);
                }
            }, e -> {
                try {
                    handleModelLoadError(responseObserver, modelId, e);
                } finally {
                    closeContextSafely(context);
                }
            });

            String resolvedTenantId = mlFeatureEnabledSetting.isMultiTenancyEnabled() ? tenantId : null;
            modelManager.getModel(modelId, resolvedTenantId, modelListener);
        } catch (Exception e) {
            handleError(responseObserver, "loading model", e);
        }
    }

    /**
     * Safely closes the stored context, logging any errors.
     *
     * @param context the stored context to close
     */
    private void closeContextSafely(ThreadContext.StoredContext context) {
        try {
            context.close();
        } catch (Exception e) {
            log.error("Failed to close thread context", e);
        }
    }

    /**
     * Handles errors during model loading with special handling for NOT_FOUND status.
     *
     * @param responseObserver the gRPC response observer
     * @param modelId the model ID being loaded
     * @param e the exception that occurred
     */
    private void handleModelLoadError(StreamObserver<PredictResponse> responseObserver, String modelId, Exception e) {
        log.error("Failed to load model {}", modelId, e);
        if (e instanceof OpenSearchStatusException && ((OpenSearchStatusException) e).status() == RestStatus.NOT_FOUND) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Model not found: " + modelId).withCause(e).asRuntimeException());
        } else {
            handleError(responseObserver, "loading model", e);
        }
    }

    /**
     * Validates model group access and executes streaming prediction if authorized.
     */
    private void validateModelGroupAccessAndExecute(
        MLModel mlModel,
        MLPredictionTaskRequest mlRequest,
        User user,
        String tenantId,
        StreamObserver<PredictResponse> responseObserver
    ) {
        try {
            String modelGroupId = mlModel.getModelGroupId();
            ActionListener<Boolean> accessListener = ActionListener.wrap(hasAccess -> {
                if (!hasAccess) {
                    responseObserver
                        .onError(
                            Status.PERMISSION_DENIED
                                .withDescription("User doesn't have privilege to perform this operation on this model")
                                .asRuntimeException()
                        );
                } else {
                    // Access granted - execute streaming prediction
                    executeStreamingPrediction(mlRequest, responseObserver);
                }
            }, e -> handleError(responseObserver, "validating model group access", e));

            modelAccessControlHelper
                .validateModelGroupAccess(
                    user,
                    mlFeatureEnabledSetting,
                    tenantId,
                    modelGroupId,
                    MLPredictionStreamTaskAction.NAME,
                    client,
                    sdkClient,
                    accessListener
                );
        } catch (Exception e) {
            handleError(responseObserver, "validating model group access", e);
        }
    }

    /**
     * Executes streaming prediction using the task runner.
     */
    private void executeStreamingPrediction(MLPredictionTaskRequest mlRequest, StreamObserver<PredictResponse> responseObserver) {
        try {
            // Create adapter that bridges ML streaming to gRPC streaming
            StreamObserverAdapter<PredictResponse> adapter = new StreamObserverAdapter<>(responseObserver);

            // Set the request to use gRPC channel
            mlRequest.setStreamingChannel(adapter.getChannel());

            // Execute prediction via task runner
            predictTaskRunner.checkCBAndExecute(FunctionName.REMOTE, mlRequest, adapter);
        } catch (Exception e) {
            handleError(responseObserver, "executing streaming prediction", e);
        }
    }

    @Override
    public void executeAgentStream(MlExecuteAgentStreamRequest request, StreamObserver<PredictResponse> responseObserver) {
        try {
            // Validate agent execution is enabled
            if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Agent framework is disabled").asRuntimeException());
                return;
            }

            // Extract tenant ID from gRPC metadata
            String tenantId = extractTenantId();

            // Convert protobuf request to ML execute request with tenant ID
            MLExecuteTaskRequest mlRequest = ProtoRequestConverter.toExecuteRequest(request, tenantId);

            // Execute agent streaming
            executeAgentStreamingTask(mlRequest, responseObserver);
        } catch (Exception e) {
            handleError(responseObserver, "executeAgentStream", e);
        }
    }

    /**
     * Executes agent streaming using the execute task runner.
     */
    private void executeAgentStreamingTask(MLExecuteTaskRequest mlRequest, StreamObserver<PredictResponse> responseObserver) {
        try {
            // Create adapter that bridges ML streaming to gRPC streaming
            StreamObserverAdapter<PredictResponse> adapter = new StreamObserverAdapter<>(responseObserver);

            // Set the request to enable streaming mode and use gRPC channel
            mlRequest.setDispatchTask(false);
            mlRequest.setStreamingChannel(adapter.getChannel());

            // Execute agent task via task runner
            executeTaskRunner.checkCBAndExecute(FunctionName.AGENT, mlRequest, adapter);
        } catch (Exception e) {
            handleError(responseObserver, "executing agent streaming task", e);
        }
    }

    /**
     * Handles errors by logging and sending error response to the client.
     * This centralizes error handling logic to reduce code duplication.
     *
     * @param responseObserver the gRPC response observer to send error to
     * @param operation the operation being performed (for logging context)
     * @param e the exception that occurred
     */
    private void handleError(StreamObserver<PredictResponse> responseObserver, String operation, Exception e) {
        log.error("Error in {}", operation, e);
        Status status = GrpcStatusMapper.toGrpcStatus(e);
        responseObserver.onError(status.asRuntimeException());
    }

    /**
     * Extracts tenant ID from gRPC context.
     * The TenantIdInterceptor extracts the "x-tenant-id" header from request metadata
     * and attaches it to the context for access here.
     *
     * Returns null if multi-tenancy is disabled.
     * Throws exception if multi-tenancy is enabled but tenant ID header is missing.
     */
    private String extractTenantId() {
        if (!mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
            return null;
        }

        // Extract tenant ID from context
        String tenantId = TenantIdInterceptor.TENANT_ID_CONTEXT_KEY.get();

        // Validate tenant ID is present when multi-tenancy is enabled
        if (tenantId == null) {
            throw new OpenSearchStatusException("Tenant ID header is missing or has no value", RestStatus.FORBIDDEN);
        }
        return tenantId;
    }

    /**
     * Gets optional model function name from model manager.
     */
    private Optional<FunctionName> getOptionalModelFunctionName(String modelId) {
        return modelManager.getOptionalModelFunctionName(modelId);
    }

    /**
     * Extracts User from OpenSearch security context.
     */
    private User getUserFromContext() {
        return userContextProvider.getUserContext();
    }
}
