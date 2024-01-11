/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

/**
 * This class consists of the REST handler to delete ML Model.
 */
public class RestMLDeleteModelGroupAction extends BaseRestHandler {
    private static final String ML_DELETE_MODEL_GROUP_ACTION = "ml_delete_model_group_action";

    public void RestMLDeleteModelGroupAction() {}

    @Override
    public String getName() {
        return ML_DELETE_MODEL_GROUP_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(
                    RestRequest.Method.DELETE,
                    String.format(Locale.ROOT, "%s/model_groups/{%s}", ML_BASE_URI, PARAMETER_MODEL_GROUP_ID)
                )
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String modelGroupId = request.param(PARAMETER_MODEL_GROUP_ID);

        MLModelGroupDeleteRequest mlModelGroupDeleteRequest = new MLModelGroupDeleteRequest(modelGroupId);
        return channel -> client
            .execute(MLModelGroupDeleteAction.INSTANCE, mlModelGroupDeleteRequest, new RestToXContentListener<>(channel));
    }
}
