/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.rest.*;
import org.opensearch.rest.action.RestResponseListener;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to search ML Models.
 */
public class RestMLSearchModelAction extends BaseRestHandler {
    private static final String ML_SEARCH_MODEL_ACTION = "ml_search_model_action";
    private static final String OPENSEARCH_DASHBOARDS_USER_AGENT = "OpenSearch Dashboards";
    private static final String[] UI_METADATA_EXCLUDE = new String[] { "ui_metadata" };

    @Override
    public String getName() {
        return ML_SEARCH_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/models/_search", ML_BASE_URI)),
                new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/models/_search", ML_BASE_URI))
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser());
        searchSourceBuilder.fetchSource(getSourceContext(request));
        searchSourceBuilder.seqNoAndPrimaryTerm(true).version(true);
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(ML_MODEL_INDEX);
        return channel -> client.execute(MLModelSearchAction.INSTANCE, searchRequest, search(channel));
    }

    /**
     * Checks to see if the request came from Kibana, if so we want to return the UI Metadata from the document.
     * If the request came from the client then we exclude the UI Metadata from the search result.
     *
     * @param request rest request
     * @return instance of {@link org.opensearch.search.fetch.subphase.FetchSourceContext}
     */
    public static FetchSourceContext getSourceContext(RestRequest request) {
        String userAgent = Strings.coalesceToEmpty(request.header("User-Agent"));
        if (!userAgent.contains(OPENSEARCH_DASHBOARDS_USER_AGENT)) {
            return new FetchSourceContext(true, Strings.EMPTY_ARRAY, UI_METADATA_EXCLUDE);
        } else {
            return null;
        }
    }

    protected RestResponseListener<SearchResponse> search(RestChannel channel) {
        return new RestResponseListener<SearchResponse>(channel) {
            @Override
            public RestResponse buildResponse(SearchResponse response) throws Exception {
                if (response.isTimedOut()) {
                    return new BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString());
                }
                return new BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), EMPTY_PARAMS));
            }
        };
    }
}
