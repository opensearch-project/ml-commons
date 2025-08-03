/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORIES_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

/**
 * REST handler for adding memory to a memory container
 */
public class RestMLAddMemoryAction extends BaseRestHandler {

    private static final String ML_ADD_MEMORY_ACTION = "ml_add_memory_action";

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, MEMORIES_PATH));
    }

    @Override
    public String getName() {
        return ML_ADD_MEMORY_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLAddMemoryRequest mlAddMemoryRequest = getRequest(request);
        return channel -> client.execute(MLAddMemoryAction.INSTANCE, mlAddMemoryRequest, new RestToXContentListener<>(channel));
    }

    private MLAddMemoryRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Add memory request has empty body");
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLAddMemoryInput mlAddMemoryInput = MLAddMemoryInput.parse(parser);

        // Set the container ID from the path
        mlAddMemoryInput.setMemoryContainerId(memoryContainerId);

        return new MLAddMemoryRequest(mlAddMemoryInput);
    }

    private static void ensureExpectedToken(XContentParser.Token expected, XContentParser.Token actual, XContentParser parser) {
        if (actual != expected) {
            throw new IllegalArgumentException("Expected token [" + expected + "] but found [" + actual + "]");
        }
    }
}
