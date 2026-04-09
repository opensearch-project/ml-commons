/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.opensearch.OpenSearchStatusException;
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

    // Use Object types to avoid circular dependency with plugin module
    private final Object modelManager;
    private final Object predictTaskRunner;
    private final Object executeTaskRunner;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final Object modelAccessControlHelper;
    private final Object client;
    private final Object sdkClient;

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
     */
    public MLStreamingService(
        Object modelManager,
        Object predictTaskRunner,
        Object executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Object modelAccessControlHelper,
        Object client,
        Object sdkClient
    ) {
        this.modelManager = modelManager;
        this.predictTaskRunner = predictTaskRunner;
        this.executeTaskRunner = executeTaskRunner;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.client = client;
        this.sdkClient = sdkClient;
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
            log.error("Error in predictModelStream", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
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
            // Get ThreadContext from client via reflection
            Method getThreadPoolMethod = client.getClass().getMethod("threadPool");
            Object threadPool = getThreadPoolMethod.invoke(client);
            Method getThreadContextMethod = threadPool.getClass().getMethod("getThreadContext");
            Object threadContext = getThreadContextMethod.invoke(threadPool);
            Method stashContextMethod = threadContext.getClass().getMethod("stashContext");

            AutoCloseable context = (AutoCloseable) stashContextMethod.invoke(threadContext);
            ActionListener<MLModel> modelListener = ActionListener.wrap(mlModel -> {
                try {
                    // Validate model group access
                    validateModelGroupAccessAndExecute(mlModel, mlRequest, user, tenantId, responseObserver);
                } catch (Exception e) {
                    log.error("Error validating model access", e);
                    Status status = GrpcStatusMapper.toGrpcStatus(e);
                    responseObserver.onError(status.asRuntimeException());
                } finally {
                    try {
                        context.close();
                    } catch (Exception ex) {
                        log.error("Failed to close context", ex);
                    }
                }
            }, e -> {
                log.error("Failed to load model {}", modelId, e);
                try {
                    context.close();
                } catch (Exception ex) {
                    log.error("Failed to close context", ex);
                }
                if (e instanceof OpenSearchStatusException && ((OpenSearchStatusException) e).status() == RestStatus.NOT_FOUND) {
                    responseObserver
                        .onError(Status.NOT_FOUND.withDescription("Model not found: " + modelId).withCause(e).asRuntimeException());
                } else {
                    Status status = GrpcStatusMapper.toGrpcStatus(e);
                    responseObserver.onError(status.asRuntimeException());
                }
            });

            String resolvedTenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), tenantId);
            Method getModelMethod = modelManager.getClass().getMethod("getModel", String.class, String.class, ActionListener.class);
            getModelMethod.invoke(modelManager, modelId, resolvedTenantId, modelListener);
        } catch (Exception e) {
            log.error("Failed to invoke getModel via reflection", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
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

            // Find validateModelGroupAccess method by iterating through methods
            // We can't use getMethod() directly because some classes aren't available at compile time
            Method validateMethod = null;
            for (Method method : modelAccessControlHelper.getClass().getMethods()) {
                if (method.getName().equals("validateModelGroupAccess") && method.getParameterCount() == 8) {
                    validateMethod = method;
                    break;
                }
            }

            if (validateMethod == null) {
                throw new RuntimeException("validateModelGroupAccess method not found");
            }

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
            }, e -> {
                log.error("Error validating model group access", e);
                Status status = GrpcStatusMapper.toGrpcStatus(e);
                responseObserver.onError(status.asRuntimeException());
            });

            validateMethod
                .invoke(
                    modelAccessControlHelper,
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
            log.error("Failed to invoke validateModelGroupAccess via reflection", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
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

            // Execute prediction via task runner using checkCBAndExecute
            try {
                Method checkCBMethod = predictTaskRunner
                    .getClass()
                    .getSuperclass()
                    .getDeclaredMethod(
                        "checkCBAndExecute",
                        FunctionName.class,
                        org.opensearch.ml.common.transport.MLTaskRequest.class,
                        ActionListener.class
                    );
                checkCBMethod.setAccessible(true);
                checkCBMethod.invoke(predictTaskRunner, FunctionName.REMOTE, mlRequest, adapter);
            } catch (InvocationTargetException e) {
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
            log.error("Error in executeAgentStream", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
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

            // Execute agent task via task runner using checkCBAndExecute
            try {
                Method checkCBMethod = executeTaskRunner
                    .getClass()
                    .getSuperclass()
                    .getDeclaredMethod(
                        "checkCBAndExecute",
                        FunctionName.class,
                        org.opensearch.ml.common.transport.MLTaskRequest.class,
                        org.opensearch.core.action.ActionListener.class
                    );
                checkCBMethod.setAccessible(true);
                checkCBMethod.invoke(executeTaskRunner, FunctionName.AGENT, mlRequest, adapter);
            } catch (InvocationTargetException e) {
                log.error("InvocationTargetException during agent execute", e.getCause());
                throw e.getCause() != null ? (Exception) e.getCause() : e;
            } catch (Exception e) {
                log.error("Exception during agent execute reflection", e);
                throw e;
            }
        } catch (Exception e) {
            log.error("Error executing agent streaming task", e);
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
        }
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
    private Optional<FunctionName> getOptionalModelFunctionName(String modelId) {
        try {
            Method method = modelManager.getClass().getMethod("getOptionalModelFunctionName", String.class);
            return (Optional<FunctionName>) method.invoke(modelManager, modelId);
        } catch (Exception e) {
            log.error("Failed to invoke getOptionalModelFunctionName via reflection", e);
            return Optional.empty();
        }
    }

    /**
     * Extracts User from OpenSearch security context via client ThreadContext.
     */
    private User getUserFromContext() {
        try {
            Class<?> restActionUtilsClass = Class.forName("org.opensearch.ml.utils.RestActionUtils");
            Method getUserContextMethod = restActionUtilsClass.getMethod("getUserContext", org.opensearch.transport.client.Client.class);
            return (User) getUserContextMethod.invoke(null, client);
        } catch (Exception e) {
            log.error("Failed to get user context via reflection", e);
            return null;
        }
    }
}
