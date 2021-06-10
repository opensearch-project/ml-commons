package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLTrainingAction extends BaseMLSearchAction {
    private static final String ML_TRAINING_ACTION = "ml_training_action";

    /**
     * Constructor
     */
    public RestMLTrainingAction() {}

    @Override
    public String getName() {
        return ML_TRAINING_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, ML_BASE_URI + "/training/"));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLTrainingTaskRequest mlTrainingTaskRequest = getRequest(request, client);
        return channel -> client.execute(MLTrainingTaskAction.INSTANCE, mlTrainingTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @param client NodeClient
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLTrainingTaskRequest getRequest(RestRequest request, NodeClient client) throws IOException {
        String algorithm = getAlgorithm(request);
        MLInputDataset inputDataset = buildSearchQueryInput(request, client);
        List<MLParameter> parameters = getMLParameters(request);

        return new MLTrainingTaskRequest(algorithm, parameters, inputDataset);
    }
}
