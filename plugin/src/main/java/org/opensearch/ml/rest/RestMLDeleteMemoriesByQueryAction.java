/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_MEMORIES_BY_QUERY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
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
        // Check if agentic memory feature is enabled
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        // Extract path parameters
        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String memoryType = getMemoryType(request);

        // Parse query from request body
        QueryBuilder query = null;
        if (request.hasContent()) {
            try (XContentParser parser = request.contentParser()) {
                query = parseQuery(parser);
            }
        }

        // If no query provided, default to match_all
        if (query == null) {
            query = QueryBuilders.matchAllQuery();
        }

        // Create the delete by query request
        MLDeleteMemoriesByQueryRequest deleteRequest = new MLDeleteMemoriesByQueryRequest(memoryContainerId, memoryType, query);

        // Execute the action and return response
        return channel -> client.execute(MLDeleteMemoriesByQueryAction.INSTANCE, deleteRequest, ActionListener.wrap(response -> {
            try {
                XContentBuilder builder = channel.newBuilder();
                // Build custom response format
                builder.startObject();
                builder.field("took", response.getTook().millis());
                builder.field("timed_out", response.isTimedOut());
                builder.field("deleted", response.getDeleted());
                builder.field("batches", response.getBatches());
                builder.field("version_conflicts", response.getVersionConflicts());
                builder.field("noops", response.getNoops());

                // Add retries information
                builder.startObject("retries");
                builder.field("bulk", response.getBulkRetries());
                builder.field("search", response.getSearchRetries());
                builder.endObject();

                builder.field("throttled_millis", response.getStatus().getThrottled().millis());
                builder.field("requests_per_second", response.getStatus().getRequestsPerSecond());
                builder.field("throttled_until_millis", response.getStatus().getThrottledUntil().millis());

                // Add failures if any
                if (response.getBulkFailures() != null && !response.getBulkFailures().isEmpty()) {
                    builder.startArray("bulk_failures");
                    for (var failure : response.getBulkFailures()) {
                        builder.startObject();
                        builder.field("index", failure.getIndex());
                        builder.field("id", failure.getId());
                        builder.field("cause", failure.getCause().getMessage());
                        builder.field("status", failure.getStatus());
                        builder.endObject();
                    }
                    builder.endArray();
                }

                if (response.getSearchFailures() != null && !response.getSearchFailures().isEmpty()) {
                    builder.startArray("search_failures");
                    for (var failure : response.getSearchFailures()) {
                        failure.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    }
                    builder.endArray();
                }

                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        }, e -> {
            try {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            } catch (Exception ex) {
                // Log error if needed
            }
        }));
    }

    private String getMemoryType(RestRequest request) {
        String memoryType = getParameterId(request, PARAMETER_MEMORY_TYPE);
        if (memoryType != null) {
            // Normalize memory type to lowercase
            memoryType = memoryType.toLowerCase(Locale.ROOT);
        }
        return memoryType;
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
