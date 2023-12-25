/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.getAlgorithm;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLExecuteAction extends BaseRestHandler {
    private static final String ML_EXECUTE_ACTION = "ml_execute_action";

    /**
     * Constructor
     */
    public RestMLExecuteAction() {}

    @Override
    public String getName() {
        return ML_EXECUTE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_execute/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM)),
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/agents/{%s}/_execute", ML_BASE_URI, PARAMETER_AGENT_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(request);
        return channel -> client.execute(MLExecuteTaskAction.INSTANCE, mlExecuteTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLExecuteTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLExecuteTaskRequest
     */
    @VisibleForTesting
    MLExecuteTaskRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        String uri = request.getHttpRequest().uri();
        FunctionName functionName = null;
        Input input = null;
        if (uri.startsWith(ML_BASE_URI + "/agents/")) {
            String agentId = request.param(PARAMETER_AGENT_ID);
            functionName = FunctionName.AGENT;
            input = MLInput.parse(parser, functionName.name());
            ((AgentMLInput) input).setAgentId(agentId);
        } else {
            String algorithm = getAlgorithm(request).toUpperCase(Locale.ROOT);
            functionName = FunctionName.from(algorithm);
            input = parser.namedObject(Input.class, functionName.name(), null);
        }

        return new MLExecuteTaskRequest(functionName, input);
    }
}
