/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.GET_MEMORY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetMemoryAction extends BaseRestHandler {
    private static final String ML_GET_MEMORY_ACTION = "ml_get_memory_action";

    /**
     * Constructor
     */
    public RestMLGetMemoryAction() {}

    @Override
    public String getName() {
        return ML_GET_MEMORY_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, GET_MEMORY_PATH)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLGetMemoryRequest mlMemoryGetRequest = getRequest(request);
        return channel -> client.execute(MLGetMemoryAction.INSTANCE, mlMemoryGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLMemoryGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLMemoryGetRequest
     */
    @VisibleForTesting
    MLGetMemoryRequest getRequest(RestRequest request) throws IOException {
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryId = getParameterId(request, PARAMETER_MEMORY_ID);
        return new MLGetMemoryRequest(memoryContainerId, memoryId);
    }
}
