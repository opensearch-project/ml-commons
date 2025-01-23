package org.opensearch.ml.engine.utils;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.TOOL_PARAMETERS_PREFIX;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
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
        // Two conditions here
        // 1. {[(exists hidden field) and (hidden field = false)] or (not exist hidden field)} and
        // 2. Any model field contains candidate ID
        BoolQueryBuilder searchAgentQuery = QueryBuilders.boolQuery();

        BoolQueryBuilder hiddenFieldQuery = QueryBuilders.boolQuery();
        // not exist hidden
        //hiddenFieldQuery.should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(MLAgent.IS_HIDDEN_FIELD)));
        // exist but equal to false
        BoolQueryBuilder existHiddenFieldQuery = QueryBuilders.boolQuery();
        existHiddenFieldQuery.must(QueryBuilders.termsQuery(MLAgent.IS_HIDDEN_FIELD, false));
        existHiddenFieldQuery.must(QueryBuilders.existsQuery(MLAgent.IS_HIDDEN_FIELD));
        hiddenFieldQuery.should(existHiddenFieldQuery);

        //
        BoolQueryBuilder modelIdQuery = QueryBuilders.boolQuery();
        for (String keyField : relatedModelIdSet) {
            modelIdQuery.should(QueryBuilders.termsQuery(TOOL_PARAMETERS_PREFIX + keyField, candidateModelId));
        }

        searchAgentQuery.must(hiddenFieldQuery);
        searchAgentQuery.must(modelIdQuery);
        searchRequest.source(new SearchSourceBuilder().query(searchAgentQuery));
        return searchRequest;
    }

}
