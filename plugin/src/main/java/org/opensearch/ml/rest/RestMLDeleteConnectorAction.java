/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

/**
 * This class consists of the REST handler to delete ML Connector.
 */
public class RestMLDeleteConnectorAction extends BaseRestHandler {
    private static final String ML_DELETE_CONNECTOR_ACTION = "ml_delete_connector_action";

    public void RestMLDeleteConnectorAction() {}

    @Override
    public String getName() {
        return ML_DELETE_CONNECTOR_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/connectors/{%s}", ML_BASE_URI, PARAMETER_CONNECTOR_ID))
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String connectorId = request.param(PARAMETER_CONNECTOR_ID);

        MLConnectorDeleteRequest mlConnectorDeleteRequest = new MLConnectorDeleteRequest(connectorId);
        return channel -> client.execute(MLConnectorDeleteAction.INSTANCE, mlConnectorDeleteRequest, new RestToXContentListener<>(channel));
    }

}
