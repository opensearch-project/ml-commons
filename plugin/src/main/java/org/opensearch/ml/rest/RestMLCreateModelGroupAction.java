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
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCreateModelGroupAction extends BaseRestHandler {
    private static final String ML_CREATE_MODEL_GROUP_ACTION = "ml_create_model_group_action";

    /**
     * Constructor
     */
    public RestMLCreateModelGroupAction() {}

    @Override
    public String getName() {
        return ML_CREATE_MODEL_GROUP_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/model_groups", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateModelGroupRequest createModelGroupRequest = getRequest(request);
        return channel -> client.execute(MLCreateModelGroupAction.INSTANCE, createModelGroupRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUploadModelMetaRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLUploadModelMetaRequest
     */
    @VisibleForTesting
    MLCreateModelGroupRequest getRequest(RestRequest request) throws IOException {
        boolean hasContent = request.hasContent();
        if (!hasContent) {
            throw new IOException("Model group request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateModelGroupInput input = MLCreateModelGroupInput.parse(parser);
        return new MLCreateModelGroupRequest(input);
    }
}
