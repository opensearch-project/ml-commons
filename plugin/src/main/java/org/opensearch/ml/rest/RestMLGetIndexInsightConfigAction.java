/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

public class RestMLGetIndexInsightConfigAction extends BaseRestHandler {
    private static final String ML_GET_INDEX_INSIGHT_CONFIG_ACTION = "ml_get_index_insight_config_action";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLGetIndexInsightConfigAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_INDEX_INSIGHT_CONFIG_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = getRequest(restRequest);
        return channel -> client
                .execute(
                        MLIndexInsightConfigGetAction.INSTANCE,
                        mlIndexInsightConfigGetRequest,
                        new RestToXContentListener<>(channel)
                );
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/index_insight_config/", ML_BASE_URI)));
    }

    @VisibleForTesting
    MLIndexInsightConfigGetRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLIndexInsightConfigGetRequest(tenantId);
    }
}
