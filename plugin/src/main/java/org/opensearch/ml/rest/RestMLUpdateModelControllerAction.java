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
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.transport.controller.MLUpdateModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLUpdateModelControllerRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLUpdateModelControllerAction extends BaseRestHandler {

    public final static String ML_UPDATE_MODEL_CONTROLLER_ACTION = "ml_update_model_controller_action";

    /**
     * Constructor
     */
    public RestMLUpdateModelControllerAction() {}

    @Override
    public String getName() {
        return ML_UPDATE_MODEL_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/model_controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateModelControllerRequest updateModelControllerRequest = getRequest(request);
        return channel -> {
            client.execute(MLUpdateModelControllerAction.INSTANCE, updateModelControllerRequest, new RestToXContentListener<>(channel));
        };
    }

    /**
     * Creates a MLUpdateModelControllerRequest from a RestRequest
     *
     * @param request RestRequest to parse
     * @return MLUpdateModelControllerRequest
     * @throws IOException if an error occurs while parsing the request
     */
    private MLUpdateModelControllerRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new OpenSearchParseException("Update model controller request has empty body");
        }
        // Model ID can only be set here.
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLModelController modelControllerInput = MLModelController.parse(parser);
        modelControllerInput.setModelId(modelId);
        return new MLUpdateModelControllerRequest(modelControllerInput);
    }
}
