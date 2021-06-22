package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLPredictionAction extends BaseMLSearchAction {
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
        return ImmutableList.of(new Route(RestRequest.Method.GET, ML_BASE_URI + "/_predict/"));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLPredictionTaskRequest mlPredictionTaskRequest = getRequest(request, client);
        return channel -> client.execute(MLPredictionTaskAction.INSTANCE, mlPredictionTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @param client NodeClient
     * @return MLPredictionTaskRequest
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(RestRequest request, NodeClient client) throws IOException {
        String algorithm = getAlgorithm(request);
        String modelId = getModelId(request);
        MLInputDataset inputDataset = buildSearchQueryInput(request, client);
        List<MLParameter> parameters = getMLParameters(request);

        return new MLPredictionTaskRequest(algorithm, parameters, modelId, inputDataset);
    }
}
