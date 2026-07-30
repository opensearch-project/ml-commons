/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RETENTION_DRY_RUN_ALL_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RETENTION_DRY_RUN_PATH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for the retention dry-run. Two routes:
 * <ul>
 *   <li>POST {@code /_plugins/_ml/memory_containers/{memory_container_id}/_retention/_dry_run} — one container</li>
 *   <li>POST {@code /_plugins/_ml/memory/_retention/_dry_run} — all containers (cluster-wide array)</li>
 * </ul>
 */
public class RestMLMemoryRetentionDryRunAction extends BaseRestHandler {
    private static final String ML_MEMORY_RETENTION_DRY_RUN_ACTION = "ml_memory_retention_dry_run_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLMemoryRetentionDryRunAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_MEMORY_RETENTION_DRY_RUN_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.POST, RETENTION_DRY_RUN_PATH), new Route(RestRequest.Method.POST, RETENTION_DRY_RUN_ALL_PATH));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        MLMemoryRetentionDryRunRequest dryRunRequest = getRequest(request);
        return channel -> client.execute(MLMemoryRetentionDryRunAction.INSTANCE, dryRunRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLMemoryRetentionDryRunRequest getRequest(RestRequest request) throws IOException {
        // memory_container_id is present only on the single-container route; null selects cluster-wide.
        String memoryContainerId = request.hasParam(PARAMETER_MEMORY_CONTAINER_ID)
            ? getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID)
            : null;
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLMemoryRetentionDryRunRequest(memoryContainerId, tenantId);
    }
}
