/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.GET_EVENT_PATH;
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
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetEventRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for getting an event from a memory container
 */
public class RestMLGetEventAction extends BaseRestHandler {
    private static final String ML_GET_EVENT_ACTION = "ml_get_event_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetEventAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_EVENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, GET_EVENT_PATH)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLGetEventRequest mlGetEventRequest = getRequest(request);
        return channel -> client.execute(MLGetEventAction.INSTANCE, mlGetEventRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLGetEventRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLGetEventRequest
     */
    @VisibleForTesting
    MLGetEventRequest getRequest(RestRequest request) throws IOException {
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String eventId = getParameterId(request, PARAMETER_EVENT_ID);
        return new MLGetEventRequest(memoryContainerId, eventId);
    }
}
