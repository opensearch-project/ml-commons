/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.CONTROLLER_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.controller.MLControllerDeleteAction;
import org.opensearch.ml.common.transport.controller.MLControllerDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to delete ML Model.
 */
public class RestMLDeleteControllerAction extends BaseRestHandler {

    private static final String ML_DELETE_CONTROLLER_ACTION = "ml_delete_controller_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteControllerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isControllerEnabled()) {
            throw new IllegalStateException(CONTROLLER_DISABLED_ERR_MSG);
        }
        String modelId = request.param(PARAMETER_MODEL_ID);

        MLControllerDeleteRequest mlControllerDeleteRequest = new MLControllerDeleteRequest(modelId);
        return channel -> client
            .execute(MLControllerDeleteAction.INSTANCE, mlControllerDeleteRequest, new RestToXContentListener<>(channel));
    }
}
