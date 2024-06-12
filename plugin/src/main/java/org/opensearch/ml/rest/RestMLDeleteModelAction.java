/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to delete ML Model.
 */
public class RestMLDeleteModelAction extends BaseRestHandler {
    private static final String ML_DELETE_MODEL_ACTION = "ml_delete_model_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteModelAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/models/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String modelId = request.param(PARAMETER_MODEL_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        MLModelDeleteRequest mlModelDeleteRequest = new MLModelDeleteRequest(modelId, tenantId);
        return channel -> client.execute(MLModelDeleteAction.INSTANCE, mlModelDeleteRequest, new RestToXContentListener<>(channel));
    }
}
