/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.UPDATE_MEMORY_CONTAINER_PATH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLUpdateMemoryContainerAction extends BaseRestHandler {
    private static final String ML_UPDATE_MEMORY_CONTAINER_ACTION = "ml_update_memory_container_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLUpdateMemoryContainerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_UPDATE_MEMORY_CONTAINER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.PUT, UPDATE_MEMORY_CONTAINER_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        MLUpdateMemoryContainerRequest mlUpdateMemoryContainerRequest = getRequest(request);
        return channel -> client
            .execute(MLUpdateMemoryContainerAction.INSTANCE, mlUpdateMemoryContainerRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUpdateMemoryContainerRequest from a RestRequest
     */
    @VisibleForTesting
    MLUpdateMemoryContainerRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Update memory container request has empty body");
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLUpdateMemoryContainerInput mlUpdateMemoryContainerInput = MLUpdateMemoryContainerInput.parse(parser);

        return MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .mlUpdateMemoryContainerInput(mlUpdateMemoryContainerInput)
            .build();
    }

}
