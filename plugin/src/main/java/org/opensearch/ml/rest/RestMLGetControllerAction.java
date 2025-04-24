/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.CONTROLLER_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.RestActionUtils.returnContent;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.controller.MLControllerGetAction;
import org.opensearch.ml.common.transport.controller.MLControllerGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetControllerAction extends BaseRestHandler {

    private static final String ML_GET_CONTROLLER_ACTION = "ml_get_controller_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetControllerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLControllerGetRequest controllerGetRequest = getRequest(request);
        return channel -> client.execute(MLControllerGetAction.INSTANCE, controllerGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLControllerGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLControllerGetRequest
     */
    @VisibleForTesting
    MLControllerGetRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isControllerEnabled()) {
            throw new IllegalStateException(CONTROLLER_DISABLED_ERR_MSG);
        }

        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        boolean returnContent = returnContent(request);

        return new MLControllerGetRequest(modelId, returnContent);
    }
}
