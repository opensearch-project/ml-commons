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
import java.util.Optional;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

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
        return List
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
        String algorithm = request.param(PARAMETER_ALGORITHM);
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        Optional<FunctionName> functionName = modelManager.getOptionalModelFunctionName(modelId);

        if (algorithm == null && functionName.isPresent()) {
            algorithm = functionName.get().name();
        }

        if (algorithm != null) {
            MLPredictionTaskRequest mlPredictionTaskRequest = getRequest(modelId, algorithm, request);
            return channel -> client
                .execute(MLPredictionTaskAction.INSTANCE, mlPredictionTaskRequest, new RestToXContentListener<>(channel));
        }

        return channel -> {
            ActionListener<MLModel> listener = ActionListener.wrap(mlModel -> {
                String algoName = mlModel.getAlgorithm().name();
                client
                    .execute(
                        MLPredictionTaskAction.INSTANCE,
                        getRequest(modelId, algoName, request),
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
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                modelManager.getModel(modelId, ActionListener.runBefore(listener, () -> context.restore()));
            }
        };
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLPredictionTaskRequest
     */
    // VisibleForTesting
    MLPredictionTaskRequest getRequest(String modelId, String algorithm, RestRequest request) throws IOException {
        if (FunctionName.REMOTE.name().equals(algorithm) && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm);
        return new MLPredictionTaskRequest(modelId, mlInput, null);
    }

}
