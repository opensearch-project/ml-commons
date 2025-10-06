/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BASE_MEMORY_CONTAINERS_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_DELETE_ALL_MEMORIES;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_DELETE_MEMORIES;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to delete ML Memory Container.
 */
public class RestMLDeleteMemoryContainerAction extends BaseRestHandler {
    private static final String ML_DELETE_MEMORY_CONTAINER_ACTION = "ml_delete_memory_container_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteMemoryContainerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_MEMORY_CONTAINER_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.DELETE,
                    String.format(Locale.ROOT, "%s/{%s}", BASE_MEMORY_CONTAINERS_PATH, PARAMETER_MEMORY_CONTAINER_ID)
                )
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        String memoryContainerId = request.param(PARAMETER_MEMORY_CONTAINER_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        // Parse URL parameters
        boolean deleteAllMemories = request.paramAsBoolean(PARAMETER_DELETE_ALL_MEMORIES, false);
        Set<String> deleteMemoriesStr = null;
        String[] memoryArray = request.paramAsStringArray(PARAMETER_DELETE_MEMORIES, Strings.EMPTY_ARRAY);
        if (memoryArray != null && memoryArray.length > 0) {
            deleteMemoriesStr = new LinkedHashSet<>(Arrays.asList(memoryArray));
        }

        // Parse request body if present (URL params take precedence)
        if (request.hasContent()) {
            try (XContentParser parser = request.contentParser()) {
                Map<String, Object> map = parser.map();

                // Parse delete_all_memories from body if not set from URL
                if (!deleteAllMemories && map.containsKey(PARAMETER_DELETE_ALL_MEMORIES)) {
                    Object value = map.get(PARAMETER_DELETE_ALL_MEMORIES);
                    if (value instanceof Boolean) {
                        deleteAllMemories = (Boolean) value;
                    }
                }

                // Parse delete_memories from body if not set from URL
                if ((deleteMemoriesStr == null || deleteMemoriesStr.isEmpty()) && map.containsKey(PARAMETER_DELETE_MEMORIES)) {
                    Object value = map.get(PARAMETER_DELETE_MEMORIES);
                    if (value instanceof List) {
                        deleteMemoriesStr = new LinkedHashSet<>();
                        for (Object item : (List<?>) value) {
                            if (item instanceof String) {
                                deleteMemoriesStr.add((String) item);
                            }
                        }
                    }
                }
            }
        }

        // Convert String set to MemoryType enum set
        Set<MemoryType> deleteMemories = null;
        if (deleteMemoriesStr != null && !deleteMemoriesStr.isEmpty()) {
            deleteMemories = deleteMemoriesStr.stream().map(MemoryType::fromString).collect(Collectors.toSet());
        }

        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = new MLMemoryContainerDeleteRequest(
            memoryContainerId,
            deleteAllMemories,
            deleteMemories,
            tenantId
        );
        return channel -> client
            .execute(MLMemoryContainerDeleteAction.INSTANCE, mlMemoryContainerDeleteRequest, new RestToXContentListener<>(channel));
    }
}
