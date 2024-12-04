package org.opensearch.ml.engine.utils;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.TOOL_MODEL_RELATED_FIELD_PREFIX;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.search.builder.SearchSourceBuilder;

public class AgentModelsSearcher {
    private final Set<String> relatedModelIdSet;

    public AgentModelsSearcher(Map<String, Tool.Factory> toolFactories) {
        relatedModelIdSet = new HashSet<>();
        for (Map.Entry<String, Tool.Factory> entry : toolFactories.entrySet()) {
            Tool.Factory toolFactory = entry.getValue();
            relatedModelIdSet.addAll(toolFactory.getAllModelKeys());
        }
    }

    public SearchRequest constructQueryRequest(String candidateModelId) {
        SearchRequest searchRequest = new SearchRequest(ML_AGENT_INDEX);
        BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();
        for (String keyField : relatedModelIdSet) {
            shouldQuery.should(QueryBuilders.termsQuery(TOOL_MODEL_RELATED_FIELD_PREFIX + keyField, candidateModelId));
        }
        searchRequest.source(new SearchSourceBuilder().query(shouldQuery));
        return searchRequest;
    }

}
