/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_MEMORY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for deleting a memory from a memory container
 */
public class RestMLDeleteMemoryAction extends BaseRestHandler {

    private static final String ML_DELETE_MEMORY_ACTION = "ml_delete_memory_action";

    /**
     * Constructor
     */
    public RestMLDeleteMemoryAction() {}

    @Override
    public String getName() {
        return ML_DELETE_MEMORY_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.DELETE, DELETE_MEMORY_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLDeleteMemoryRequest mlDeleteMemoryRequest = getRequest(request);
        return channel -> client.execute(MLDeleteMemoryAction.INSTANCE, mlDeleteMemoryRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLDeleteMemoryRequest from a RestRequest
     */
    @VisibleForTesting
    MLDeleteMemoryRequest getRequest(RestRequest request) {
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryId = getParameterId(request, PARAMETER_MEMORY_ID);

        return MLDeleteMemoryRequest.builder().memoryContainerId(memoryContainerId).memoryId(memoryId).build();
    }
}
