/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for creating memory containers
 */
public class RestMLCreateMemoryContainerAction extends BaseRestHandler {

    private static final String ML_CREATE_MEMORY_CONTAINER_ACTION = "ml_create_memory_container_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLCreateMemoryContainerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_CREATE_MEMORY_CONTAINER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/memory_containers/_create", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = getRequest(request);
        return channel -> client
            .execute(MLCreateMemoryContainerAction.INSTANCE, mlCreateMemoryContainerRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCreateMemoryContainerRequest from REST request
     */
    @VisibleForTesting
    MLCreateMemoryContainerRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Request body is required");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.parse(parser);
        String tenantId = TenantAwareHelper.getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        input.setTenantId(tenantId);
        return new MLCreateMemoryContainerRequest(input);
    }
}
