/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.RestActionUtils.returnContent;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.controller.MLModelControllerGetAction;
import org.opensearch.ml.common.transport.controller.MLModelControllerGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetModelControllerAction extends BaseRestHandler {
    private static final String ML_GET_MODEL_CONTROLLER_ACTION = "ml_get_model_controller_action";

    /**
     * Constructor
     */
    public RestMLGetModelControllerAction() {}

    @Override
    public String getName() {
        return ML_GET_MODEL_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/model_controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLModelControllerGetRequest modelControllerGetRequest = getRequest(request);
        return channel -> client
            .execute(MLModelControllerGetAction.INSTANCE, modelControllerGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLModelControllerGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLModelControllerGetRequest
     */
    // VisibleForTesting
    MLModelControllerGetRequest getRequest(RestRequest request) throws IOException {
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        boolean returnContent = returnContent(request);

        return new MLModelControllerGetRequest(modelId, returnContent);
    }
}
