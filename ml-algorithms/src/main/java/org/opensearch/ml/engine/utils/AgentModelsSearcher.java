package org.opensearch.ml.engine.utils;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.TOOL_PARAMETERS_PREFIX;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.search.builder.SearchSourceBuilder;

public class AgentModelsSearcher {
    private final Set<String> relatedModelIdSet;

    public AgentModelsSearcher(Map<String, Tool.Factory> toolFactories) {
        relatedModelIdSet = new HashSet<>();
        for (Map.Entry<String, Tool.Factory> entry : toolFactories.entrySet()) {
            Tool.Factory toolFactory = entry.getValue();
            if (toolFactory instanceof WithModelTool.Factory) {
                WithModelTool.Factory withModelTool = (WithModelTool.Factory) toolFactory;
                relatedModelIdSet.addAll(withModelTool.getAllModelKeys());
            }
        }
    }

    /**
     * Construct a should query to search all agent which containing candidate model Id
    
     @param candidateModelId the candidate model Id
     @return a should search request towards agent index.
     */
    public SearchRequest constructQueryRequestToSearchModelIdInsideAgent(String candidateModelId) {
        SearchRequest searchRequest = new SearchRequest(ML_AGENT_INDEX);
        BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();
        for (String keyField : relatedModelIdSet) {
            shouldQuery.should(QueryBuilders.termsQuery(TOOL_PARAMETERS_PREFIX + keyField, candidateModelId));
        }
        shouldQuery.should(QueryBuilders.termsQuery(MLAgent.IS_HIDDEN_FIELD, false));
        searchRequest.source(new SearchSourceBuilder().query(shouldQuery));
        return searchRequest;
    }

}
