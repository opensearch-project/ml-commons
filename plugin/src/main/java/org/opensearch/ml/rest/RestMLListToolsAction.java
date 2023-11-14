/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.tools.MLListToolsAction;
import org.opensearch.ml.common.transport.tools.MLToolsListRequest;
import org.opensearch.ml.engine.tools.ToolsFactory;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLListToolsAction extends BaseRestHandler {
    private static final String ML_GET_MODEL_ACTION = "ml_get_tools_action";

    private ToolsFactory toolsFactory;

    public RestMLListToolsAction(ToolsFactory toolsFactory) {
        this.toolsFactory = toolsFactory;
    }

    @Override
    public String getName() {
        return ML_GET_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/tools", ML_BASE_URI)));
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
        List<ToolMetadata> toolList = toolsFactory.getAllTools().stream()
                .map(tool -> ToolMetadata.builder().name(tool.getName()).description(tool.getDescription()).build())
                .collect(Collectors.toList());
        MLToolsListRequest mlToolsGetRequest = MLToolsListRequest.builder().externalTools(toolList).build();
        return channel -> client.execute(MLListToolsAction.INSTANCE, mlToolsGetRequest, new RestToXContentListener<>(channel));
    }
}
