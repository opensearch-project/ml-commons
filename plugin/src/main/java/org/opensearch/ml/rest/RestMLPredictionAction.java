/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@Log4j2
public class RestMLPredictionAction extends BaseRestHandler {
    private static final String ML_PREDICTION_ACTION = "ml_prediction_action";

    /**
     * Constructor
     */
    public RestMLPredictionAction() {}

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
        String algorithm = request.param(PARAMETER_ALGORITHM);
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }

        if (algorithm != null) {
            return channel -> client
                .execute(MLPredictionTaskAction.INSTANCE, getRequest(modelId, algorithm, request), new RestToXContentListener<>(channel));
        }

        return channel -> {
            MLModelGetRequest getModelRequest = new MLModelGetRequest(modelId, false);
            ActionListener<MLModelGetResponse> listener = ActionListener.wrap(r -> {
                MLModel mlModel = r.getMlModel();
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
                    channel.sendResponse(new BytesRestResponse(channel, e));
                } catch (IOException ex) {
                    log.error("Failed to send error response", ex);
                }
            });
            client.execute(MLModelGetAction.INSTANCE, getModelRequest, listener);

        };
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLPredictionTaskRequest
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(String modelId, String algorithm, RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm);
        return new MLPredictionTaskRequest(modelId, mlInput);
    }

}
