package org.opensearch.ml.engine.tools;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.TOOL_MODEL_RELATED_FIELD_PREFIX;

public class RelatedModelIdHelper {
    private Map<String, List<String> > relatedModelIdMap;

    public RelatedModelIdHelper(Map<String, Tool.Factory> ToolFactories) {
        relatedModelIdMap = new HashMap<>();
        for (Map.Entry<String, Tool.Factory> entry : ToolFactories.entrySet()) {
            String toolType = entry.getKey();
            Tool.Factory toolFactory = entry.getValue();
            relatedModelIdMap.put(toolType, toolFactory.getRelatedModelIDKeyFields());
        }
    }

    public SearchRequest constructQueryRequest(String candidateModelId){
        SearchRequest searchRequest = new SearchRequest(ML_AGENT_INDEX);
        List<String> allKeyFields = collectAllKeys();
        BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();
        for (String keyField : allKeyFields) {
            shouldQuery.should(QueryBuilders.termsQuery(TOOL_MODEL_RELATED_FIELD_PREFIX + keyField, candidateModelId));
        }
        searchRequest.source(new SearchSourceBuilder().query(shouldQuery));
        return searchRequest;
    }

    private List<String> collectAllKeys(){
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : relatedModelIdMap.entrySet()) {
            keys.addAll(entry.getValue());
        }
        return new ArrayList<>(keys);
    }
}
