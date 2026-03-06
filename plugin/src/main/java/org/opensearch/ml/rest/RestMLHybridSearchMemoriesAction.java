/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.HYBRID_SEARCH_MEMORIES_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

public class RestMLHybridSearchMemoriesAction extends BaseRestHandler {

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLHybridSearchMemoriesAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return "ml_hybrid_search_memories_action";
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.POST, HYBRID_SEARCH_MEMORIES_PATH),
                new Route(RestRequest.Method.GET, HYBRID_SEARCH_MEMORIES_PATH)
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }

        String memoryContainerId = getParameterId(request, PARAMETER_MEMORY_CONTAINER_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        MLHybridSearchMemoriesInput input = parseInput(request, memoryContainerId);
        MLHybridSearchMemoriesRequest searchRequest = MLHybridSearchMemoriesRequest
            .builder()
            .mlHybridSearchMemoriesInput(input)
            .tenantId(tenantId)
            .build();

        return channel -> client.execute(MLHybridSearchMemoriesAction.INSTANCE, searchRequest, new RestToXContentListener<>(channel));
    }

    private MLHybridSearchMemoriesInput parseInput(RestRequest request, String memoryContainerId) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Hybrid search memories request has empty body");
        }
        XContentParser parser = request.contentParser();
        parser.nextToken();

        String query = null;
        int k = 10;
        Map<String, String> namespace = null;
        Map<String, String> tags = null;
        Float minScore = null;
        QueryBuilder filter = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "query":
                    query = parser.text();
                    break;
                case "k":
                    k = parser.intValue();
                    break;
                case "namespace":
                    namespace = parser.mapStrings();
                    break;
                case "tags":
                    tags = parser.mapStrings();
                    break;
                case "min_score":
                    minScore = parser.floatValue();
                    break;
                case "filter":
                    filter = parseInnerQueryBuilder(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .query(query)
            .k(k)
            .namespace(namespace)
            .tags(tags)
            .minScore(minScore)
            .filter(filter)
            .build();
    }
}
