/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchParseException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLRegisterModelGroupAction extends BaseRestHandler {
    private static final String ML_REGISTER_MODEL_GROUP_ACTION = "ml_register_model_group_action";

    /**
     * Constructor
     */
    public RestMLRegisterModelGroupAction() {}

    @Override
    public String getName() {
        return ML_REGISTER_MODEL_GROUP_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/model_groups/_register", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLRegisterModelGroupRequest createModelGroupRequest = getRequest(request);
        return channel -> client
            .execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLRegisterModelGroupRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLRegisterModelGroupRequest
     */
    // VisibleForTesting
    MLRegisterModelGroupRequest getRequest(RestRequest request) throws IOException {
        boolean hasContent = request.hasContent();
        if (!hasContent) {
            throw new OpenSearchParseException("Model group request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLRegisterModelGroupInput input = MLRegisterModelGroupInput.parse(parser);
        return new MLRegisterModelGroupRequest(input);
    }
}
