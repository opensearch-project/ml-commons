package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_INDEX_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
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

    public RestMLGetIndexInsightAction(ClusterService clusterService, Settings settings) {
        this.clusterService = clusterService;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return ML_GET_INDEX_INSIGHT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/index_insight/{%s}", ML_BASE_URI, PARAMETER_INDEX_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = getRequest(restRequest);
        return channel -> client.execute(MLIndexInsightGetAction.INSTANCE, mlIndexInsightGetRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLIndexInsightGetRequest getRequest(RestRequest request) throws IOException {
        String indexName = getParameterId(request, PARAMETER_INDEX_ID);
        String insightType = request.param("insight_type", "STATISTICAL_DATA");
        MLIndexInsightType type = MLIndexInsightType.fromString(insightType);
        return new MLIndexInsightGetRequest(indexName, type);
    }
}
