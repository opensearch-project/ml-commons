/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.getAlgorithm;
import static org.opensearch.ml.utils.RestActionUtils.isAsync;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLTrainAndPredictAction extends BaseRestHandler {
    private static final String ML_TRAIN_AND_PREDICT_ACTION = "ml_train_and_predict_action";

    /**
     * Constructor
     */
    public RestMLTrainAndPredictAction() {}

    @Override
    public String getName() {
        return ML_TRAIN_AND_PREDICT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_train_predict/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLTrainingTaskRequest mlTrainingTaskRequest = getRequest(request);
        return channel -> client
            .execute(MLTrainAndPredictionTaskAction.INSTANCE, mlTrainingTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    // VisibleForTesting
    MLTrainingTaskRequest getRequest(RestRequest request) throws IOException {
        String algorithm = getAlgorithm(request);
        boolean async = isAsync(request);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm);

        return new MLTrainingTaskRequest(mlInput, async);
    }
}
