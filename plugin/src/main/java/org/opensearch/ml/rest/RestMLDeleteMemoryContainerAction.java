/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BASE_MEMORY_CONTAINERS_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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
        String memoryContainerId = request.param(PARAMETER_MEMORY_CONTAINER_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = new MLMemoryContainerDeleteRequest(memoryContainerId, tenantId);
        return channel -> client
            .execute(MLMemoryContainerDeleteAction.INSTANCE, mlMemoryContainerDeleteRequest, new RestToXContentListener<>(channel));
    }
}
