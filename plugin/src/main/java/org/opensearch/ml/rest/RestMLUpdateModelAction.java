/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchParseException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLUpdateModelAction extends BaseRestHandler {

    private static final String ML_UPDATE_MODEL_ACTION = "ml_update_model_action";

    @Override
    public String getName() {
        return ML_UPDATE_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/models/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateModelRequest updateModelRequest = getRequest(request);
        return channel -> client.execute(MLUpdateModelAction.INSTANCE, updateModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUpdateModelRequest from a RestRequest
     * 
     * @param request RestRequest
     * @return MLUpdateModelRequest
     */
    private MLUpdateModelRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new OpenSearchParseException("Update model request has empty body");
        }

        String modelId = getParameterId(request, PARAMETER_MODEL_ID);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        try {
            MLUpdateModelInput input = MLUpdateModelInput.parse(parser);
            if (input.getConnectorId() != null && input.getConnector() != null) {
                throw new OpenSearchStatusException(
                    "Model cannot have both stand-alone connector and internal connector. Please check your update input body.",
                    RestStatus.BAD_REQUEST
                );
            }
            // Model ID can only be set here. Model version as well as connector field can only be set automatically.
            input.setModelId(modelId);
            input.setVersion(null);
            input.setUpdatedConnector(null);
            return new MLUpdateModelRequest(input);
        } catch (IllegalStateException e) {
            throw new OpenSearchParseException(e.getMessage());
        }
    }
}
