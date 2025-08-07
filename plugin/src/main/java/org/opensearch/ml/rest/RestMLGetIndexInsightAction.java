package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_INDEX_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetIndexInsightAction extends BaseRestHandler {
    private static final String ML_GET_INDEX_INSIGHT_ACTION = "ml_get_index_insight_action";

    private ClusterService clusterService;

    private Settings settings;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLGetIndexInsightAction(ClusterService clusterService, Settings settings, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.clusterService = clusterService;
        this.settings = settings;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_INDEX_INSIGHT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/insights/{%s}/{insight_type}", ML_BASE_URI, PARAMETER_INDEX_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = getRequest(restRequest);
        return channel -> client.execute(MLIndexInsightGetAction.INSTANCE, mlIndexInsightGetRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLIndexInsightGetRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        String indexName = getParameterId(request, PARAMETER_INDEX_ID);
        String insightType = request.param("insight_type");
        if (insightType == null) {
            insightType = "STATISTICAL_DATA";
        }
        MLIndexInsightType type = MLIndexInsightType.fromString(insightType);
        return new MLIndexInsightGetRequest(indexName, type, tenantId);
    }
}
