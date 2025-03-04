/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLPredictionAction extends BaseRestHandler {
    private static final String ML_PREDICTION_ACTION = "ml_prediction_action";

    private MLModelManager modelManager;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLPredictionAction(MLModelManager modelManager, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.modelManager = modelManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_PREDICTION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/_predict/{%s}/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM, PARAMETER_MODEL_ID)
                ),
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/models/{%s}/_predict", ML_BASE_URI, PARAMETER_MODEL_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String userAlgorithm = request.param(PARAMETER_ALGORITHM);
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        Optional<FunctionName> functionName = modelManager.getOptionalModelFunctionName(modelId);

        // check if the model is in cache
        if (functionName.isPresent()) {
            MLPredictionTaskRequest predictionRequest = getRequest(
                modelId,
                functionName.get().name(),
                Objects.requireNonNullElse(userAlgorithm, functionName.get().name()),
                request
            );
            return channel -> client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, new RestToXContentListener<>(channel));
        }

        // If the model isn't in cache
        return channel -> {
            MLModelGetRequest getModelRequest = new MLModelGetRequest(modelId, false);
            ActionListener<MLModelGetResponse> listener = ActionListener.wrap(r -> {
                MLModel mlModel = r.getMlModel();
                String modelType = mlModel.getAlgorithm().name();
                String modelAlgorithm = Objects.requireNonNullElse(userAlgorithm, mlModel.getAlgorithm().name());
                client
                    .execute(
                        MLPredictionTaskAction.INSTANCE,
                        getRequest(modelId, modelType, modelAlgorithm, request),
                        new RestToXContentListener<>(channel)
                    );
            }, e -> {
                log.error("Failed to get ML model", e);
                try {
                    channel.sendResponse(new BytesRestResponse(channel, RestStatus.NOT_FOUND, e));
                } catch (IOException ex) {
                    log.error("Failed to send error response", ex);
                }
            });
            client.execute(MLModelGetAction.INSTANCE, getModelRequest, listener);

        };
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest. This method validates the request based on
     * enabled features and model types, and parses the input data for prediction.
     *
     * @param modelId The ID of the ML model to use for prediction
     * @param modelType The type of the ML model, extracted from model cache to specify if its a remote model or a local model
     * @param userAlgorithm The algorithm specified by the user for prediction, this is used todetermine the interface of the model
     * @param request The REST request containing prediction input data
     * @return MLPredictionTaskRequest configured with the model and input parameters
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(String modelId, String modelType, String userAlgorithm, RestRequest request) throws IOException {
        if (FunctionName.REMOTE.name().equals(modelType) && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, userAlgorithm);
        return new MLPredictionTaskRequest(modelId, mlInput, null);
    }

}
