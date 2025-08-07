package org.opensearch.ml.rest;

import com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_INDEX_ID;

public class RestMLPutIndexInsightContainerAction extends BaseRestHandler {
    private static final String ML_PUT_INDEX_INSIGHT_CONTAINER_ACTION = "ml_put_index_insight_container_action";
    @Override
    public String getName() {
        return ML_PUT_INDEX_INSIGHT_CONTAINER_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient nodeClient) throws IOException {
        return null;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
                .of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/index_insight_container/", ML_BASE_URI)));
    }
}
