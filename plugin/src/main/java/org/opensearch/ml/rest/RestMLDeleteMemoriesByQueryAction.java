/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_MEMORIES_BY_QUERY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

/**
 * REST handler for deleting memories by query
 */
public class RestMLDeleteMemoriesByQueryAction extends BaseRestHandler {

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteMemoriesByQueryAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return "ml_delete_memories_by_query_action";
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, DELETE_MEMORIES_BY_QUERY_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        // Extract path parameters
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryType = getParameterId(request, PARAMETER_MEMORY_TYPE);

        // Parse query from request body
        QueryBuilder query = null;
        if (request.hasContent()) {
            try (XContentParser parser = request.contentParser()) {
                query = parseQuery(parser);
            }
        }

        // Create the delete by query request
        MLDeleteMemoriesByQueryRequest deleteRequest = new MLDeleteMemoriesByQueryRequest(memoryContainerId, memoryType, query);

        // Execute the action and return response using RestToXContentListener
        return channel -> client.execute(MLDeleteMemoriesByQueryAction.INSTANCE, deleteRequest, new RestToXContentListener<>(channel));
    }

    private QueryBuilder parseQuery(XContentParser parser) throws IOException {
        // Move to the first token
        parser.nextToken();

        // Parse the query object
        QueryBuilder query = null;
        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
            String fieldName = null;
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                if (parser.currentToken() == XContentParser.Token.FIELD_NAME) {
                    fieldName = parser.currentName();
                } else if ("query".equals(fieldName)) {
                    query = parseInnerQueryBuilder(parser);
                } else {
                    parser.skipChildren();
                }
            }
        }

        return query;
    }
}
