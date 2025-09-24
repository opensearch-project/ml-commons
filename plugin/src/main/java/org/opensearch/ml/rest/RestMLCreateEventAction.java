/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATE_EVENT_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

/**
 * REST handler for creating short-term memory events within a memory container
 */
public class RestMLCreateEventAction extends BaseRestHandler {

    private static final String ML_CREATE_EVENT_ACTION = "ml_create_event_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLCreateEventAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, CREATE_EVENT_PATH));
    }

    @Override
    public String getName() {
        return ML_CREATE_EVENT_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        MLCreateEventRequest mlCreateEventRequest = getRequest(request);
        return channel -> client.execute(MLCreateEventAction.INSTANCE, mlCreateEventRequest, new RestToXContentListener<>(channel));
    }

    private MLCreateEventRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Create event request has empty body");
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateEventInput mlCreateEventInput = MLCreateEventInput.parse(parser, memoryContainerId);

        return new MLCreateEventRequest(mlCreateEventInput);
    }
}
