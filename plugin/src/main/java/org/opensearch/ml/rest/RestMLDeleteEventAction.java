/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_EVENT_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_EVENT_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteEventRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for deleting an event from a memory container
 */
public class RestMLDeleteEventAction extends BaseRestHandler {
    private static final String ML_DELETE_EVENT_ACTION = "ml_delete_event_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLDeleteEventAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_EVENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, DELETE_EVENT_PATH)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLDeleteEventRequest mlDeleteEventRequest = getRequest(request);
        return channel -> client.execute(MLDeleteEventAction.INSTANCE, mlDeleteEventRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLDeleteEventRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLDeleteEventRequest
     */
    @VisibleForTesting
    MLDeleteEventRequest getRequest(RestRequest request) throws IOException {
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String eventId = getParameterId(request, PARAMETER_EVENT_ID);
        return new MLDeleteEventRequest(memoryContainerId, eventId);
    }
}
