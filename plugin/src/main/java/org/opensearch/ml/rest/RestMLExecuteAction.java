/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.getAlgorithm;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

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
            .of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_execute/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM)));
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
        String algorithm = getAlgorithm(request).toUpperCase(Locale.ROOT);
        FunctionName functionName = FunctionName.from(algorithm);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        Input input = parser.namedObject(Input.class, algorithm, null);

        return new MLExecuteTaskRequest(functionName, input);
    }
}
