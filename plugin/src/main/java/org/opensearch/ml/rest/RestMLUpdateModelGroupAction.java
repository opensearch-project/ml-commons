/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLUpdateModelGroupAction extends BaseRestHandler {

    private static final String ML_UPDATE_MODEL_GROUP_ACTION = "ml_update_model_group_action";

    @Override
    public String getName() {
        return ML_UPDATE_MODEL_GROUP_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
            .of(
                new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/model_groups/{%s}", ML_BASE_URI, PARAMETER_MODEL_GROUP_ID))
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateModelGroupRequest updateModelGroupRequest = getRequest(request);
        return channel -> client.execute(MLUpdateModelGroupAction.INSTANCE, updateModelGroupRequest, new RestToXContentListener<>(channel));
    }

    private MLUpdateModelGroupRequest getRequest(RestRequest request) throws IOException {
        String modelGroupID = getParameterId(request, PARAMETER_MODEL_GROUP_ID);
        boolean hasContent = request.hasContent();
        if (!hasContent) {
            throw new IOException("Model group request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLUpdateModelGroupInput input = MLUpdateModelGroupInput.parse(parser);
        input.setModelGroupID(modelGroupID);
        return new MLUpdateModelGroupRequest(input);
    }

}
