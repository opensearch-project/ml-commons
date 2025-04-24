/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.RestActionUtils.returnContent;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetConnectorAction extends BaseRestHandler {
    private static final String ML_GET_CONNECTOR_ACTION = "ml_get_connector_action";

    private ClusterService clusterService;

    private Settings settings;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetConnectorAction(ClusterService clusterService, Settings settings, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.clusterService = clusterService;
        this.settings = settings;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_CONNECTOR_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/connectors/{%s}", ML_BASE_URI, PARAMETER_CONNECTOR_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLConnectorGetRequest mlConnectorGetRequest = getRequest(request);
        return channel -> client.execute(MLConnectorGetAction.INSTANCE, mlConnectorGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLConnectorGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLConnectorGetRequest
     */
    @VisibleForTesting
    MLConnectorGetRequest getRequest(RestRequest request) throws IOException {
        String connectorId = getParameterId(request, PARAMETER_CONNECTOR_ID);
        boolean returnContent = returnContent(request);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLConnectorGetRequest(connectorId, tenantId, returnContent);
    }
}
