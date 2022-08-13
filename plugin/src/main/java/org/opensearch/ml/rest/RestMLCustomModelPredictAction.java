/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelAction;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelInput;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCustomModelPredictAction extends BaseRestHandler {
    private static final String ML_PREDICT_MODEL_ACTION = "ml_predict_custom_model_action";

    /**
     * Constructor
     */
    public RestMLCustomModelPredictAction() {}

    @Override
    public String getName() {
        return ML_PREDICT_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/custom_model/predict", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLPredictModelRequest mlUploadModelRequest = getRequest(request);
        return channel -> client.execute(MLPredictModelAction.INSTANCE, mlUploadModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLPredictModelRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLPredictModelInput mlInput = MLPredictModelInput.parse(parser);

        return new MLPredictModelRequest(mlInput, false);
    }
}
