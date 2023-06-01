/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCreateConnectorAction extends BaseRestHandler {
    private static final String ML_CREATE_CONNECTOR_ACTION = "ml_create_connector_action";

    /**
     * Constructor *
     */
    public RestMLCreateConnectorAction() {}

    @Override
    public String getName() {
        return ML_CREATE_CONNECTOR_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/connectors/_create", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateConnectorRequest mlCreateConnectorRequest = getRequest(request);
        return channel -> client.execute(MLCreateConnectorAction.INSTANCE, mlCreateConnectorRequest, new RestToXContentListener<>(channel));
    }

    /**
     * * Creates a MLCreateConnectorRequest from a RestRequest
     * @param request
     * @return MLCreateConnectorRequest
     * @throws IOException
     */
    @VisibleForTesting
    MLCreateConnectorRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Create Connector request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateConnectorInput mlCreateConnectorInput = MLCreateConnectorInput.parse(parser);
        return new MLCreateConnectorRequest(mlCreateConnectorInput);
    }
}
