/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.utils.RestActionUtils.getSourceContext;
import static org.opensearch.ml.utils.RestActionUtils.getTenantID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.rest.action.RestResponseListener;
import org.opensearch.search.builder.SearchSourceBuilder;

public abstract class AbstractMLSearchAction<T extends ToXContentObject> extends BaseRestHandler {

    protected final List<String> urlPaths;
    protected final String index;
    protected final Class<T> clazz;
    protected final ActionType<SearchResponse> actionType;
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public AbstractMLSearchAction(
        List<String> urlPaths,
        String index,
        Class<T> clazz,
        ActionType<SearchResponse> actionType,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.urlPaths = urlPaths;
        this.index = index;
        this.clazz = clazz;
        this.actionType = actionType;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser());
        searchSourceBuilder.fetchSource(getSourceContext(request, searchSourceBuilder));
        searchSourceBuilder.seqNoAndPrimaryTerm(true).version(true);
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
            // Add tenant ID filter
            String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
            searchSourceBuilder.query(QueryBuilders.termQuery(TENANT_ID, tenantId));
        }
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(index);
        return channel -> client.execute(actionType, searchRequest, search(channel));
    }

    protected RestResponseListener<SearchResponse> search(RestChannel channel) {
        return new RestResponseListener<>(channel) {
            @Override
            public RestResponse buildResponse(SearchResponse response) throws Exception {
                if (response.isTimedOut()) {
                    return new BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString());
                }
                return new BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), EMPTY_PARAMS));
            }
        };
    }

    @Override
    public List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (String path : urlPaths) {
            routes.add(new Route(RestRequest.Method.POST, path));
            routes.add(new Route(RestRequest.Method.GET, path));
        }
        return routes;
    }
}
