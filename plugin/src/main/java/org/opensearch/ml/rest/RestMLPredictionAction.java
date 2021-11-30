package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLPredictionAction extends BaseMLAction {
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
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLPredictionTaskRequest mlPredictionTaskRequest = getRequest(request);
        return channel -> client.execute(MLPredictionTaskAction.INSTANCE, mlPredictionTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLPredictionTaskRequest
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(RestRequest request) throws IOException {
        String algorithm = getAlgorithm(request);
        String modelId = getModelId(request);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm);

        return new MLPredictionTaskRequest(modelId, mlInput);
    }
}
