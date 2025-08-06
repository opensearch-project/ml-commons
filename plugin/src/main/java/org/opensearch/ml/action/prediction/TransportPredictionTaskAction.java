/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.breaker.CircuitBreakingException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.algorithms.agent.tracing.MLConnectorTracer;
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
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportPredictionTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mlPredictTaskRunner;
    TransportService transportService;
    MLModelCacheHelper modelCacheHelper;

    Client client;
    SdkClient sdkClient;

    ClusterService clusterService;

    NamedXContentRegistry xContentRegistry;

    MLModelManager mlModelManager;

    ModelAccessControlHelper modelAccessControlHelper;

    private volatile boolean enableAutomaticDeployment;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportPredictionTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelCacheHelper modelCacheHelper,
        MLPredictTaskRunner mlPredictTaskRunner,
        ClusterService clusterService,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Settings settings
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        enableAutomaticDeployment = ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE, it -> enableAutomaticDeployment = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictionTaskRequest.getModelId();
        Span predictSpan = MLConnectorTracer.startModelPredictSpan(modelId, null);
        MLConnectorTracer.serializeInputForTracing(mlPredictionTaskRequest, predictSpan);
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
            ActionListener<MLTaskResponse> spanWrappedListener = MLConnectorTracer.createSpanWrappedListener(predictSpan, listener);
            ActionListener<MLTaskResponse> wrappedListener = ActionListener.runBefore(spanWrappedListener, context::restore);
            MLModel cachedMlModel = modelCacheHelper.getModelInfo(modelId);

            ActionListener<MLModel> modelActionListener = new ActionListener<>() {
                @Override
                public void onResponse(MLModel mlModel) {
                    context.restore();
                    modelCacheHelper.setModelInfo(modelId, mlModel);
                    FunctionName functionName = mlModel.getAlgorithm();
                    if (FunctionName.isDLModel(functionName) && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
                        throw new IllegalStateException(LOCAL_MODEL_DISABLED_ERR_MSG);
                    }
                    mlPredictionTaskRequest.getMlInput().setAlgorithm(functionName);
                    modelAccessControlHelper
                        .validateModelGroupAccess(
                            userInfo,
                            mlFeatureEnabledSetting,
                            tenantId,
                            mlModel.getModelGroupId(),
                            client,
                            sdkClient,
                            ActionListener.wrap(access -> {
                                if (!access) {
                                    wrappedListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "User Doesn't have privilege to perform this operation on this model",
                                                RestStatus.FORBIDDEN
                                            )
                                        );
                                } else {
                                    if (modelCacheHelper.getIsModelEnabled(modelId) != null
                                        && !modelCacheHelper.getIsModelEnabled(modelId)) {
                                        wrappedListener
                                            .onFailure(new OpenSearchStatusException("Model is disabled.", RestStatus.FORBIDDEN));
                                    } else {
                                        if (FunctionName.isDLModel(functionName)) {
                                            if (modelCacheHelper.getRateLimiter(modelId) != null
                                                && !modelCacheHelper.getRateLimiter(modelId).request()) {
                                                MLConnectorTracer
                                                    .handleSpanError(
                                                        predictSpan,
                                                        "Request is throttled at model level.",
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at model level.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at model level.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                            } else if (userInfo != null
                                                && modelCacheHelper.getUserRateLimiter(modelId, userInfo.getName()) != null
                                                && !modelCacheHelper.getUserRateLimiter(modelId, userInfo.getName()).request()) {
                                                MLConnectorTracer
                                                    .handleSpanError(
                                                        predictSpan,
                                                        "Request is throttled at user level.",
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at user level. If you think there's an issue, please contact your cluster admin.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Request is throttled at user level. If you think there's an issue, please contact your cluster admin.",
                                                            RestStatus.TOO_MANY_REQUESTS
                                                        )
                                                    );
                                            } else {
                                                validateInputSchema(modelId, mlPredictionTaskRequest.getMlInput());
                                                executePredict(mlPredictionTaskRequest, wrappedListener, modelId, mlModel.getName());
                                            }
                                        } else {
                                            validateInputSchema(modelId, mlPredictionTaskRequest.getMlInput());
                                            executePredict(mlPredictionTaskRequest, wrappedListener, modelId, mlModel.getName());
                                        }
                                    }
                                }
                            }, e -> {
                                log.error("Failed to Validate Access for ModelId {}", modelId, e);
                                if (e instanceof OpenSearchStatusException) {
                                    wrappedListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                e.getMessage(),
                                                RestStatus.fromCode(((OpenSearchStatusException) e).status().getStatus())
                                            )
                                        );
                                    predictSpan.setError(e);
                                } else if (e instanceof MLResourceNotFoundException) {
                                    wrappedListener.onFailure(new OpenSearchStatusException(e.getMessage(), RestStatus.NOT_FOUND));
                                    predictSpan.setError(e);
                                } else if (e instanceof CircuitBreakingException) {
                                    wrappedListener.onFailure(e);
                                    predictSpan.setError(e);
                                } else {
                                    wrappedListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "Failed to Validate Access for ModelId " + modelId,
                                                RestStatus.FORBIDDEN
                                            )
                                        );
                                    predictSpan.setError(e);
                                }
                                MLConnectorTracer.getInstance().endSpan(predictSpan);
                            })
                        );
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to find model {}", modelId, e);
                    MLConnectorTracer.handleSpanError(predictSpan, "Failed to find model " + modelId, e);
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
            MLConnectorTracer.handleSpanError(predictSpan, "Failed to find model " + modelId, e);
            listener.onFailure(e);
        }
    }

    private void executePredict(
        MLPredictionTaskRequest mlPredictionTaskRequest,
        ActionListener<MLTaskResponse> wrappedListener,
        String modelId,
        String modelName
    ) {
        Span executeSpan = MLConnectorTracer.startModelExecuteSpan(modelId, modelName);
        MLConnectorTracer.serializeInputForTracing(mlPredictionTaskRequest, executeSpan);
        try {
            String requestId = mlPredictionTaskRequest.getRequestID();
            log.debug("receive predict request {} for model {}", requestId, mlPredictionTaskRequest.getModelId());
            long startTime = System.nanoTime();
            // For remote text embedding model, neural search will set mlPredictionTaskRequest.getMlInput().getAlgorithm() as
            // TEXT_EMBEDDING. In ml-commons we should always use the real function name of model: REMOTE. So we try to get
            // from model cache first.
            FunctionName functionName = modelCacheHelper
                .getOptionalFunctionName(modelId)
                .orElse(mlPredictionTaskRequest.getMlInput().getAlgorithm());
            ActionListener<MLTaskResponse> spanWrappedListener = ActionListener.wrap(response -> {
                MLConnectorTracer.getInstance().endSpan(executeSpan);
                wrappedListener.onResponse(response);
            }, exception -> {
                MLConnectorTracer.handleSpanError(executeSpan, "Error in model.execute span", exception);
                wrappedListener.onFailure(exception);
            });

            mlPredictTaskRunner
                .run(
                    // This is by design to NOT use mlPredictionTaskRequest.getMlInput().getAlgorithm() here
                    functionName,
                    mlPredictionTaskRequest,
                    transportService,
                    ActionListener.runAfter(spanWrappedListener, () -> {
                        long endTime = System.nanoTime();
                        double durationInMs = (endTime - startTime) / 1e6;
                        modelCacheHelper.addPredictRequestDuration(modelId, durationInMs);
                        modelCacheHelper.refreshLastAccessTime(modelId);
                        log.debug("completed predict request {} for model {}", requestId, modelId);
                    })
                );
        } catch (Exception e) {
            MLConnectorTracer.handleSpanError(executeSpan, "Error in model.execute span", e);
            wrappedListener.onFailure(e);
            throw e;
        }
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
