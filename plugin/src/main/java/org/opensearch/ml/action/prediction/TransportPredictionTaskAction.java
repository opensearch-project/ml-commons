/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_VALIDATE_BACKEND_ROLES;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.SecurityUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

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

    @NonFinal
    volatile boolean filterByEnabled;

    @Inject
    public TransportPredictionTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLPredictTaskRunner mlPredictTaskRunner,
        MLModelCacheHelper modelCacheHelper,
        ClusterService clusterService,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLModelManager mlModelManager,
        Settings settings
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlModelManager = mlModelManager;
        filterByEnabled = ML_COMMONS_VALIDATE_BACKEND_ROLES.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_VALIDATE_BACKEND_ROLES, it -> filterByEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictionTaskRequest.getModelId();

        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, ActionListener.wrap(mlModel -> {
                SecurityUtils.validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                    if ((filterByEnabled) && (Boolean.FALSE.equals(access))) {
                        listener.onFailure(new MLValidationException("User Doesn't have previlege to perform this operation"));
                    } else {
                        String requestId = mlPredictionTaskRequest.getRequestID();
                        log.debug("receive predict request " + requestId + " for model " + mlPredictionTaskRequest.getModelId());
                        long startTime = System.nanoTime();
                        mlPredictTaskRunner.run(mlPredictionTaskRequest, transportService, ActionListener.runAfter(listener, () -> {
                            long endTime = System.nanoTime();
                            double durationInMs = (endTime - startTime) / 1e6;
                            modelCacheHelper.addPredictRequestDuration(modelId, durationInMs);
                            log.debug("completed predict request " + requestId + " for model " + modelId);
                        }));
                    }
                }, e -> {
                    log.error("Failed to Validate Access for ModelId " + modelId, e);
                    listener.onFailure(e);
                }));
            }, e -> {
                log.error("Failed to find model " + modelId, e);
                listener.onFailure(e);
            }));

        }
    }
}
