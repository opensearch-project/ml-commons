package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.search.SearchTaskAction;
import org.opensearch.ml.common.transport.search.SearchTaskRequest;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLSearchAction extends BaseMLModelManageAction {
    private static final String ML_SEARCH_ACTION = "ml_search_action";

    /**
     * Constructor
     */
    public RestMLSearchAction() {}

    @Override
    public String getName() {
        return ML_SEARCH_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, ML_BASE_URI + "/_search/"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        SearchTaskRequest searchTaskRequest = getRequest(request);
        return channel -> client.execute(SearchTaskAction.INSTANCE, searchTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a SearchTaskRequest from a RestRequest.
     * All parameters (filters) can be null.
     *
     * @param request RestRequest
     * @return SearchTaskRequest
     */
    @VisibleForTesting
    SearchTaskRequest getRequest(RestRequest request) throws IOException {
        String modelId = getModelId(request);
        String name = getName(request);
        String format = getFormat(request);
        String algorithm = getAlgorithm(request);

        return new SearchTaskRequest(modelId, name, format, algorithm);
    }
}
