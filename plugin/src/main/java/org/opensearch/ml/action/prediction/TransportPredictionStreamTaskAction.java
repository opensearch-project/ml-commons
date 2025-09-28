/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_PREDICT_THREAD_POOL;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Nullable;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.algorithms.remote.StreamPredictActionListener;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportPredictionStreamTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mlPredictTaskRunner;

    TransportService transportService;

    MLModelCacheHelper modelCacheHelper;

    Client client;

    SdkClient sdkClient;

    ClusterService clusterService;

    MLModelManager mlModelManager;

    ModelAccessControlHelper modelAccessControlHelper;

    private volatile boolean enableAutomaticDeployment;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public static StreamTransportService streamTransportService;
    private static StreamTransportService streamTransportServiceInstance;

    @Inject
    public TransportPredictionStreamTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelCacheHelper modelCacheHelper,
        MLPredictTaskRunner mlPredictTaskRunner,
        ClusterService clusterService,
        Client client,
        SdkClient sdkClient,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Settings settings,
        @Nullable StreamTransportService streamTransportService
    ) {
        super(MLPredictionStreamTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        if (streamTransportServiceInstance == null) {
            streamTransportServiceInstance = streamTransportService;
        }
        this.streamTransportService = streamTransportServiceInstance;
        enableAutomaticDeployment = ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE, it -> enableAutomaticDeployment = it);

        if (streamTransportService != null) {
            streamTransportService
                .registerRequestHandler(
                    MLPredictionStreamTaskAction.NAME,
                    STREAM_PREDICT_THREAD_POOL,
                    MLPredictionTaskRequest::new,
                    this::messageReceived
                );
        } else {
            log.warn("StreamTransportService is not available.");
        }
    }

    public static StreamTransportService getStreamTransportService() {
        return streamTransportService;
    }

    public void messageReceived(MLPredictionTaskRequest request, TransportChannel channel, Task task) {
        StreamPredictActionListener<MLTaskResponse, MLPredictionTaskRequest> streamListener = new StreamPredictActionListener<>(channel);
        doExecute(task, request, streamListener, channel);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        // This should never be called for streaming action
        listener.onFailure(new UnsupportedOperationException("Use doExecute with TransportChannel for streaming requests"));
    }

    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        mlPredictionTaskRequest.setStreamingChannel(channel);

        String modelId = mlPredictionTaskRequest.getModelId();
        String tenantId = mlPredictionTaskRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }
        User user = mlPredictionTaskRequest.getUser();
        if (user == null) {
            user = RestActionUtils.getUserContext(client);
            mlPredictionTaskRequest.setUser(user);
        }
        final User userInfo = user;

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLTaskResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
            MLModel cachedMlModel = modelCacheHelper.getModelInfo(modelId);
            ActionListener<MLModel> modelActionListener = new ActionListener<>() {
                @Override
                public void onResponse(MLModel mlModel) {
                    context.restore();
                    modelCacheHelper.setModelInfo(modelId, mlModel);
                    FunctionName functionName = mlModel.getAlgorithm();
                    if (FunctionName.isDLModel(functionName) && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
                        throw new UnsupportedOperationException("Streaming is not supported for local model.");
                    }
                    mlPredictionTaskRequest.getMlInput().setAlgorithm(functionName);
                    // Validate user access to model group
                    modelAccessControlHelper
                        .validateModelGroupAccess(
                            userInfo,
                            mlFeatureEnabledSetting,
                            tenantId,
                            mlModel.getModelGroupId(),
                            client,
                            sdkClient,
                            ActionListener.wrap(access -> {
                                // Check if user has access
                                if (!access) {
                                    wrappedListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "User Doesn't have privilege to perform this operation on this model",
                                                RestStatus.FORBIDDEN
                                            )
                                        );
                                } else {
                                    // Check if model is enabled
                                    if (modelCacheHelper.getIsModelEnabled(modelId) != null
                                        && !modelCacheHelper.getIsModelEnabled(modelId)) {
                                        wrappedListener
                                            .onFailure(new OpenSearchStatusException("Model is disabled.", RestStatus.FORBIDDEN));
                                    } else {
                                        if (FunctionName.isDLModel(functionName)) {
                                            // Check model-level rate limit
                                            if (modelCacheHelper.getRateLimiter(modelId) != null
                                                && !modelCacheHelper.getRateLimiter(modelId).request()) {
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at model level.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                                // Check user-level rate limit
                                            } else if (userInfo != null
                                                && modelCacheHelper.getUserRateLimiter(modelId, userInfo.getName()) != null
                                                && !modelCacheHelper.getUserRateLimiter(modelId, userInfo.getName()).request()) {
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at user level. If you think there's an issue, please contact your cluster admin.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                            } else {
                                                // DL models don't support streaming
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Non-streaming requests are not supported by the streaming transport action",
                                                            RestStatus.BAD_REQUEST
                                                        )
                                                    );
                                            }
                                        } else {
                                            // Execute predict stream for non-DL models
                                            validateInputSchema(modelId, mlPredictionTaskRequest.getMlInput());
                                            executePredictStream(mlPredictionTaskRequest, wrappedListener, modelId);
                                        }
                                    }
                                }
                            }, wrappedListener::onFailure)
                        );
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to find model {}", modelId, e);
                    wrappedListener.onFailure(e);
                }
            };

            if (cachedMlModel != null) {
                modelActionListener.onResponse(cachedMlModel);
            } else {
                // For multi-node cluster, the function name is null in cache, so should always get model first.
                mlModelManager.getModel(modelId, tenantId, modelActionListener);
            }
        } catch (Exception e) {
            log.error("Failed to predict " + mlPredictionTaskRequest.toString(), e);
            listener.onFailure(e);
        }
    }

    private void executePredictStream(
        MLPredictionTaskRequest mlPredictionTaskRequest,
        ActionListener<MLTaskResponse> wrappedListener,
        String modelId
    ) {
        String requestId = mlPredictionTaskRequest.getRequestID();
        long startTime = System.nanoTime();

        FunctionName functionName = modelCacheHelper
            .getOptionalFunctionName(modelId)
            .orElse(mlPredictionTaskRequest.getMlInput().getAlgorithm());

        mlPredictTaskRunner
            .run(functionName, mlPredictionTaskRequest, streamTransportService, ActionListener.runAfter(wrappedListener, () -> {
                long endTime = System.nanoTime();
                double durationInMs = (endTime - startTime) / 1e6;
                modelCacheHelper.addPredictRequestDuration(modelId, durationInMs);
                modelCacheHelper.refreshLastAccessTime(modelId);
                log.debug("completed predict request {} for model {}", requestId, modelId);
            }));
    }

    public void validateInputSchema(String modelId, MLInput mlInput) {
        if (modelCacheHelper.getModelInterface(modelId) != null && modelCacheHelper.getModelInterface(modelId).get("input") != null) {
            String inputSchemaString = modelCacheHelper.getModelInterface(modelId).get("input");
            try {
                String InputString = mlInput.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS).toString();
                // Process the parameters field in the input dataset to convert it back to its original datatype, instead of a string
                String processedInputString = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(InputString, inputSchemaString);
                MLNodeUtils.validateSchema(inputSchemaString, processedInputString);
            } catch (Exception e) {
                throw new OpenSearchStatusException(
                    "Error validating input schema, if you think this is expected, please update your 'input' field in the 'interface' field for this model: "
                        + e.getMessage(),
                    RestStatus.BAD_REQUEST
                );
            }
        }
    }
}
