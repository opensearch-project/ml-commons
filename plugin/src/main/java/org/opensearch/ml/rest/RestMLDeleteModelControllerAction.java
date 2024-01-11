/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.controller.MLModelControllerDeleteAction;
import org.opensearch.ml.common.transport.controller.MLModelControllerDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

/**
 * This class consists of the REST handler to delete ML Model.
 */
public class RestMLDeleteModelControllerAction extends BaseRestHandler {
    private static final String ML_DELETE_MODEL_CONTROLLER_ACTION = "ml_delete_model_controller_action";

    public void RestMLDeleteModelControllerAction() {}

    @Override
    public String getName() {
        return ML_DELETE_MODEL_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(
                    RestRequest.Method.DELETE,
                    String.format(Locale.ROOT, "%s/model_controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)
                )
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String modelId = request.param(PARAMETER_MODEL_ID);

        MLModelControllerDeleteRequest mlModelControllerDeleteRequest = new MLModelControllerDeleteRequest(modelId);
        return channel -> client
            .execute(MLModelControllerDeleteAction.INSTANCE, mlModelControllerDeleteRequest, new RestToXContentListener<>(channel));
    }
}
