/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEARCH_MEMORIES_PATH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.RestActionUtils.getSourceContext;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for searching memories in a memory container
 */
public class RestMLSearchMemoriesAction extends BaseRestHandler {

    private static final String ML_SEARCH_MEMORIES_ACTION = "ml_search_memories_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLSearchMemoriesAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_SEARCH_MEMORIES_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.POST, SEARCH_MEMORIES_PATH), new Route(RestRequest.Method.GET, SEARCH_MEMORIES_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        MLSearchMemoriesRequest mlSearchMemoriesRequest = getRequest(request);
        return channel -> client.execute(MLSearchMemoriesAction.INSTANCE, mlSearchMemoriesRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLSearchMemoriesRequest from a RestRequest
     */
    @VisibleForTesting
    MLSearchMemoriesRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Search memories request has empty body");
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryTypeStr = getParameterId(request, PARAMETER_MEMORY_TYPE);
        MemoryType memoryType = MemoryType.fromString(memoryTypeStr);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser());
        searchSourceBuilder.fetchSource(getSourceContext(request, searchSourceBuilder));
        searchSourceBuilder.seqNoAndPrimaryTerm(true).version(true);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        MLSearchMemoriesInput mlSearchMemoriesInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        return new MLSearchMemoriesRequest(mlSearchMemoriesInput, tenantId);
    }

}
