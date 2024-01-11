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
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLCreateModelControllerAction extends BaseRestHandler {

    public final static String ML_CREATE_MODEL_CONTROLLER_ACTION = "ml_create_model_controller_action";

    /**
     * Constructor
     */
    public RestMLCreateModelControllerAction() {}

    @Override
    public String getName() {
        return ML_CREATE_MODEL_CONTROLLER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/model_controllers/{%s}", ML_BASE_URI, PARAMETER_MODEL_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateModelControllerRequest createModelControllerRequest = getRequest(request);
        return channel -> {
            client.execute(MLCreateModelControllerAction.INSTANCE, createModelControllerRequest, new RestToXContentListener<>(channel));
        };
    }

    /**
     * Creates a MLCreateModelControllerRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLCreateModelControllerRequest
     */
    private MLCreateModelControllerRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new OpenSearchParseException("Create model controller request has empty body");
        }
        // Model ID can only be set here.
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLModelController modelControllerInput = MLModelController.parse(parser);
        modelControllerInput.setModelId(modelId);
        return new MLCreateModelControllerRequest(modelControllerInput);
    }
}
