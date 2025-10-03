/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSIONS_PATH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionInput;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for creating sessions
 */
public class RestMLCreateSessionAction extends BaseRestHandler {

    private static final String ML_CREATE_SESSION_ACTION = "ml_create_session_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLCreateSessionAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_CREATE_SESSION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, SESSIONS_PATH));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLCreateSessionRequest mlCreateSessionRequest = getRequest(request);
        return channel -> client.execute(MLCreateSessionAction.INSTANCE, mlCreateSessionRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCreateSessionRequest from REST request
     */
    @VisibleForTesting
    MLCreateSessionRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Request body is required");
        }

        String containerId = RestActionUtils.getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory container ID is required");
        }

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateSessionInput input = MLCreateSessionInput.parse(parser);
        String tenantId = TenantAwareHelper.getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        input.setTenantId(tenantId);
        input.setMemoryContainerId(containerId);
        return new MLCreateSessionRequest(input);
    }
}
