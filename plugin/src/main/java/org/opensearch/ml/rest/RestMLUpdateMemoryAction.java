/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.UPDATE_MEMORY_PATH;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for updating a memory in a memory container
 */
public class RestMLUpdateMemoryAction extends BaseRestHandler {

    private static final String ML_UPDATE_MEMORY_ACTION = "ml_update_memory_action";

    /**
     * Constructor
     */
    public RestMLUpdateMemoryAction() {}

    @Override
    public String getName() {
        return ML_UPDATE_MEMORY_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.PUT, UPDATE_MEMORY_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateMemoryRequest mlUpdateMemoryRequest = getRequest(request);
        return channel -> client.execute(MLUpdateMemoryAction.INSTANCE, mlUpdateMemoryRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUpdateMemoryRequest from a RestRequest
     */
    @VisibleForTesting
    MLUpdateMemoryRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Update memory request has empty body");
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryId = getParameterId(request, PARAMETER_MEMORY_ID);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLUpdateMemoryInput mlUpdateMemoryInput = MLUpdateMemoryInput.parse(parser);

        return MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(mlUpdateMemoryInput)
            .build();
    }
}
