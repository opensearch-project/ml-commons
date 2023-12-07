/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportPredictionTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mlPredictTaskRunner;
    TransportService transportService;
    MLModelCacheHelper modelCacheHelper;

    Client client;

    ClusterService clusterService;

    NamedXContentRegistry xContentRegistry;

    MLModelManager mlModelManager;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportPredictionTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelCacheHelper modelCacheHelper,
        MLPredictTaskRunner mlPredictTaskRunner,
        ClusterService clusterService,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictionTaskRequest.getModelId();

        User user = mlPredictionTaskRequest.getUser();
        if (user == null) {
            user = RestActionUtils.getUserContext(client);
            mlPredictionTaskRequest.setUser(user);
        }
        final User userInfo = user;

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLTaskResponse> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());
            MLModel cachedMlModel = modelCacheHelper.getModelInfo(modelId);
            ActionListener<MLModel> modelActionListener = new ActionListener<>() {
                @Override
                public void onResponse(MLModel mlModel) {
                    context.restore();
                    modelCacheHelper.setModelInfo(modelId, mlModel);
                    FunctionName functionName = mlModel.getAlgorithm();
                    mlPredictionTaskRequest.getMlInput().setAlgorithm(functionName);
                    modelAccessControlHelper
                        .validateModelGroupAccess(userInfo, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                            if (!access) {
                                wrappedListener
                                    .onFailure(
                                        new MLValidationException("User Doesn't have privilege to perform this operation on this model")
                                    );
                            } else {
                                if (modelCacheHelper.getIsRequestAccepted(modelId) != null
                                    && !modelCacheHelper.getIsRequestAccepted(modelId)) {
                                    wrappedListener
                                        .onFailure(new OpenSearchStatusException("Quota is depleted.", RestStatus.TOO_MANY_REQUESTS));
                                } else {
                                    if (FunctionName.isDLModel(functionName)) {
                                        if (modelCacheHelper.getRateLimiter(modelId) != null
                                            && !modelCacheHelper.getRateLimiter(modelId).request()) {
                                            wrappedListener
                                                .onFailure(
                                                    new OpenSearchStatusException("Request is throttled.", RestStatus.TOO_MANY_REQUESTS)
                                                );
                                        } else {
                                            executePredict(mlPredictionTaskRequest, wrappedListener, modelId);
                                        }
                                    } else {
                                        executePredict(mlPredictionTaskRequest, wrappedListener, modelId);
                                    }
                                }
                            }
                        }, e -> {
                            log.error("Failed to Validate Access for ModelId " + modelId, e);
                            wrappedListener.onFailure(e);
                        }));
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to find model " + modelId, e);
                    wrappedListener.onFailure(e);
                }
            };

            if (cachedMlModel != null) {
                modelActionListener.onResponse(cachedMlModel);
            } else {
                // For multi-node cluster, the function name is null in cache, so should always get model first.
                mlModelManager.getModel(modelId, modelActionListener);
            }
        }
    }

    private void executePredict(
        MLPredictionTaskRequest mlPredictionTaskRequest,
        ActionListener<MLTaskResponse> wrappedListener,
        String modelId
    ) {
        String requestId = mlPredictionTaskRequest.getRequestID();
        log.debug("receive predict request " + requestId + " for model " + mlPredictionTaskRequest.getModelId());
        long startTime = System.nanoTime();
        // For remote text embedding model, neural search will set mlPredictionTaskRequest.getMlInput().getAlgorithm() as
        // TEXT_EMBEDDING. In ml-commons we should always use the real function name of model: REMOTE. So we try to get
        // from model cache first.
        FunctionName functionName = modelCacheHelper
            .getOptionalFunctionName(modelId)
            .orElse(mlPredictionTaskRequest.getMlInput().getAlgorithm());
        mlPredictTaskRunner
            .run(
                // This is by design to NOT use mlPredictionTaskRequest.getMlInput().getAlgorithm() here
                functionName,
                mlPredictionTaskRequest,
                transportService,
                ActionListener.runAfter(wrappedListener, () -> {
                    long endTime = System.nanoTime();
                    double durationInMs = (endTime - startTime) / 1e6;
                    modelCacheHelper.addPredictRequestDuration(modelId, durationInMs);
                    log.debug("completed predict request " + requestId + " for model " + modelId);
                })
            );
    }
}
