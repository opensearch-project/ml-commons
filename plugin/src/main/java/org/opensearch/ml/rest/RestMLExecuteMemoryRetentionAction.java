/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EXECUTE_MEMORY_RETENTION_PATH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionAction;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler that triggers the memory retention job on demand, cluster-wide.
 *
 * <p>Scope: cluster-wide only. The underlying {@link org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor}
 * runs a single full-cluster pipeline (it iterates every container via a singleton in-progress guard)
 * and exposes no per-container entry point. A per-container route (e.g.
 * {@code POST .../memory_containers/{id}/_retention/_execute}) would require refactoring the pipeline
 * to accept a container filter, which the task explicitly scoped out as a risky refactor, so it is
 * intentionally not offered.
 */
public class RestMLExecuteMemoryRetentionAction extends BaseRestHandler {
    private static final String ML_EXECUTE_MEMORY_RETENTION_ACTION = "ml_execute_memory_retention_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLExecuteMemoryRetentionAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_EXECUTE_MEMORY_RETENTION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, EXECUTE_MEMORY_RETENTION_PATH));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        if (request.hasContent()) {
            throw new OpenSearchStatusException(
                "_execute does not accept a request body; it always runs cluster-wide.",
                RestStatus.BAD_REQUEST
            );
        }
        MLExecuteMemoryRetentionRequest executeRequest = getRequest(request);
        return channel -> client.execute(MLExecuteMemoryRetentionAction.INSTANCE, executeRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLExecuteMemoryRetentionRequest getRequest(RestRequest request) throws IOException {
        return MLExecuteMemoryRetentionRequest.builder().build();
    }
}
