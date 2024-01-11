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
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetModelAction extends BaseRestHandler {
    private static final String ML_GET_MODEL_ACTION = "ml_get_model_action";

    /**
     * Constructor
     */
    public RestMLGetModelAction() {}

    @Override
    public String getName() {
        return ML_GET_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/models/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLModelGetRequest mlModelGetRequest = getRequest(request);
        return channel -> client.execute(MLModelGetAction.INSTANCE, mlModelGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLModelGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLModelGetRequest
     */
    // VisibleForTesting
    MLModelGetRequest getRequest(RestRequest request) throws IOException {
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        boolean returnContent = returnContent(request);

        return new MLModelGetRequest(modelId, returnContent, true);
    }
}
