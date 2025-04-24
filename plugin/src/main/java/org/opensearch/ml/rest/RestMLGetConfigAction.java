/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONFIG_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetConfigAction extends BaseRestHandler {
    private static final String ML_GET_CONFIG_ACTION = "ml_get_config_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetConfigAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_CONFIG_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/config/{%s}", ML_BASE_URI, PARAMETER_CONFIG_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLConfigGetRequest mlConfigGetRequest = getRequest(request);
        return channel -> client.execute(MLConfigGetAction.INSTANCE, mlConfigGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTaskGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTaskGetRequest
     */
    @VisibleForTesting
    MLConfigGetRequest getRequest(RestRequest request) throws IOException {
        String configID = getParameterId(request, PARAMETER_CONFIG_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        if (configID.equals(MASTER_KEY)) {
            throw new IllegalArgumentException("You are not allowed to access this config doc");
        }

        return new MLConfigGetRequest(configID, tenantId);
    }
}
