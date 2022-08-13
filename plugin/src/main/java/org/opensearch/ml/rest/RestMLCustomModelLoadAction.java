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
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.custom.load.MLDeployModelInput;
import org.opensearch.ml.common.transport.custom.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.custom.load.MLLoadModelRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCustomModelLoadAction extends BaseRestHandler {
    private static final String ML_LOAD_MODEL_ACTION = "ml_load_model_action";

    /**
     * Constructor
     */
    public RestMLCustomModelLoadAction() {}

    @Override
    public String getName() {
        return ML_LOAD_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/custom_model/load", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLLoadModelRequest mlLoadModelRequest = getRequest(request);

        return channel -> client.execute(MLLoadModelAction.INSTANCE, mlLoadModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLLoadModelRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLDeployModelInput mlInput = MLDeployModelInput.parse(parser);

        return new MLLoadModelRequest(mlInput, false);
    }
}
