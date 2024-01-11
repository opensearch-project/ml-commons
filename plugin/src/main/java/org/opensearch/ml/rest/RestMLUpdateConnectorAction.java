/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchParseException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLUpdateConnectorAction extends BaseRestHandler {
    private static final String ML_UPDATE_CONNECTOR_ACTION = "ml_update_connector_action";
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLUpdateConnectorAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_UPDATE_CONNECTOR_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/connectors/{%s}", ML_BASE_URI, PARAMETER_CONNECTOR_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateConnectorRequest mlUpdateConnectorRequest = getRequest(request);
        return restChannel -> client
            .execute(MLUpdateConnectorAction.INSTANCE, mlUpdateConnectorRequest, new RestToXContentListener<>(restChannel));
    }

    // VisibleForTesting
    private MLUpdateConnectorRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        }

        if (!request.hasContent()) {
            throw new OpenSearchParseException("Failed to update connector: Request body is empty");
        }

        String connectorId = getParameterId(request, PARAMETER_CONNECTOR_ID);

        try {
            XContentParser parser = request.contentParser();
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLUpdateConnectorRequest.parse(parser, connectorId);
        } catch (IllegalStateException illegalStateException) {
            throw new OpenSearchParseException(illegalStateException.getMessage());
        }
    }
}
