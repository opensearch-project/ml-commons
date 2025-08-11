/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLDeleteIndexInsightContainerAction extends BaseRestHandler {
    private static final String ML_DELETE_INDEX_INSIGHT_CONTAINER_ACTION = "ml_delete_index_insight_container_action";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteIndexInsightContainerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_INDEX_INSIGHT_CONTAINER_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = getRequest(restRequest);
        return channel -> client
            .execute(
                MLIndexInsightContainerDeleteAction.INSTANCE,
                mlIndexInsightContainerDeleteRequest,
                new RestToXContentListener<>(channel)
            );
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/index_insight_container/", ML_BASE_URI)));
    }

    @VisibleForTesting
    MLIndexInsightContainerDeleteRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLIndexInsightContainerDeleteRequest(tenantId);
    }
}
