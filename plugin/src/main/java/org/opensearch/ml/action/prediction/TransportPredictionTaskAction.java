/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_VALIDATE_BACKEND_ROLES;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.SecurityUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
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
        Settings settings
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        filterByEnabled = ML_COMMONS_VALIDATE_BACKEND_ROLES.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_VALIDATE_BACKEND_ROLES, it -> filterByEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictionTaskRequest.getModelId();

        MLModelGetRequest mlModelGetRequest = new MLModelGetRequest(modelId, false);
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModel mlModel = MLModel.parse(parser);

                        if ((mlModel.getModelGroupId() != null)
                            && (filterByEnabled)
                            && (!SecurityUtils.validateModelGroupAccess(user, mlModel.getModelGroupId(), client))) {
                            log.error("User doesn't have valid privilege to perform this operation");
                            throw new IllegalArgumentException("User doesn't have valid privilege to perform this operation");
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml model" + r.getId(), e);
                        listener.onFailure(e);
                    }
                }
            }, e -> { listener.onFailure(new MLResourceNotFoundException("Fail to find model")); }));
        } catch (Exception e) {
            log.error("Failed to get ML model " + modelId, e);
            listener.onFailure(e);
        }

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
}
