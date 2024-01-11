/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.tools.MLGetToolAction;
import org.opensearch.ml.common.transport.tools.MLToolGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetToolAction extends BaseRestHandler {

    private static final String ML_GET_TOOL_ACTION = "ml_get_tool_action";

    private Map<String, Tool.Factory> toolFactories;

    public RestMLGetToolAction(Map<String, Tool.Factory> toolFactories) {
        this.toolFactories = toolFactories;
    }

    @Override
    public String getName() {
        return ML_GET_TOOL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/tools/{%s}", ML_BASE_URI, PARAMETER_TOOL_NAME)));
    }

    /**
     * Prepare the request for execution. Implementations should consume all request params before
     * returning the runnable for actual execution. Unconsumed params will immediately terminate
     * execution of the request. However, some params are only used in processing the response;
     * implementations can override {@link BaseRestHandler#responseParams()} to indicate such
     * params.
     *
     * @param request the request to execute
     * @param client  client for executing actions on the local node
     * @return the action to execute
     * @throws IOException if an I/O exception occurred parsing the request and preparing for
     *                     execution
     */
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLToolGetRequest mlToolGetRequest = getRequest(request);
        return channel -> client.execute(MLGetToolAction.INSTANCE, mlToolGetRequest, new RestToXContentListener<>(channel));
    }

    // VisibleForTesting
    MLToolGetRequest getRequest(RestRequest request) throws IOException {
        List<ToolMetadata> toolList = new ArrayList<>();
        toolFactories
            .forEach(
                (key, value) -> toolList
                    .add(
                        ToolMetadata
                            .builder()
                            .name(key)
                            .description(value.getDefaultDescription())
                            .type(value.getDefaultType())
                            .version(value.getDefaultVersion())
                            .build()
                    )
            );
        String toolName = getParameterId(request, PARAMETER_TOOL_NAME);
        MLToolGetRequest mlToolGetRequest = MLToolGetRequest.builder().toolName(toolName).toolMetadataList(toolList).build();
        return mlToolGetRequest;
    }
}
