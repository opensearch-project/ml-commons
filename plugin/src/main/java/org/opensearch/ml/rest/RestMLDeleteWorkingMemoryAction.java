/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_WORKING_MEMORY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_WORKING_MEMORY_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteWorkingMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteWorkingMemoryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLDeleteWorkingMemoryAction extends BaseRestHandler {
    private static final String ML_DELETE_WORKING_MEMORY_ACTION = "ml_delete_working_memory_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLDeleteWorkingMemoryAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_WORKING_MEMORY_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, DELETE_WORKING_MEMORY_PATH)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLDeleteWorkingMemoryRequest mlDeleteWorkingMemoryRequest = getRequest(request);
        return channel -> client
            .execute(MLDeleteWorkingMemoryAction.INSTANCE, mlDeleteWorkingMemoryRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLDeleteWorkingMemoryRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLDeleteWorkingMemoryRequest
     */
    @VisibleForTesting
    MLDeleteWorkingMemoryRequest getRequest(RestRequest request) throws IOException {
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String workingMemoryId = getParameterId(request, PARAMETER_WORKING_MEMORY_ID);
        return new MLDeleteWorkingMemoryRequest(memoryContainerId, workingMemoryId);
    }
}
