/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetModelGroupAction extends BaseRestHandler {
    private static final String ML_GET_MODEL_GROUP_ACTION = "ml_get_model_group_action";

    /**
     * Constructor
     */
    public RestMLGetModelGroupAction() {}

    @Override
    public String getName() {
        return ML_GET_MODEL_GROUP_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/model_groups/{%s}", ML_BASE_URI, PARAMETER_MODEL_GROUP_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLModelGroupGetRequest mlModelGroupGetRequest = getRequest(request);
        return channel -> client.execute(MLModelGroupGetAction.INSTANCE, mlModelGroupGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLModelGroupGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLModelGroupGetRequest
     */
    // VisibleForTesting
    MLModelGroupGetRequest getRequest(RestRequest request) throws IOException {
        String modelGroupId = getParameterId(request, PARAMETER_MODEL_GROUP_ID);

        return new MLModelGroupGetRequest(modelGroupId);
    }
}
