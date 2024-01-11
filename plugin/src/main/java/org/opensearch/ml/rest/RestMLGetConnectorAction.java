/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.RestActionUtils.returnContent;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetConnectorAction extends BaseRestHandler {
    private static final String ML_GET_CONNECTOR_ACTION = "ml_get_connector_action";

    /**
     * Constructor
     */
    public RestMLGetConnectorAction() {}

    @Override
    public String getName() {
        return ML_GET_CONNECTOR_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
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
    // VisibleForTesting
    MLConnectorGetRequest getRequest(RestRequest request) throws IOException {
        String connectorId = getParameterId(request, PARAMETER_CONNECTOR_ID);
        boolean returnContent = returnContent(request);

        return new MLConnectorGetRequest(connectorId, returnContent);
    }
}
