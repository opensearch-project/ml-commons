/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLDeployModelAction extends BaseRestHandler {
    private static final String ML_DEPLOY_MODEL_ACTION = "ml_deploy_model_action";

    /**
     * Constructor
     */
    public RestMLDeployModelAction() {}

    @Override
    public String getName() {
        return ML_DEPLOY_MODEL_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return List
            .of(
                new ReplacedRoute(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/_deploy", ML_BASE_URI, PARAMETER_MODEL_ID),// new url
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/_load", ML_BASE_URI, PARAMETER_MODEL_ID)// old url
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLDeployModelRequest MLDeployModelRequest = getRequest(request);

        return channel -> client.execute(MLDeployModelAction.INSTANCE, MLDeployModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    // VisibleForTesting
    MLDeployModelRequest getRequest(RestRequest request) throws IOException {
        String modelId = request.param(PARAMETER_MODEL_ID);
        if (!request.hasContent()) {
            return new MLDeployModelRequest(modelId, false);
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        return MLDeployModelRequest.parse(parser, modelId);
    }
}
