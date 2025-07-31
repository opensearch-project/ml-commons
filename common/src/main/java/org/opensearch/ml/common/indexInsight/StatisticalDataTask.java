package org.opensearch.ml.common.indexInsight;

import java.util.Collections;
import java.util.List;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StatisticalDataTask implements IndexInsightTask {
    
    private final MLIndexInsightType taskType = MLIndexInsightType.STATISTICAL_DATA;
    private final String indexName;
    private final Client client;
    private IndexInsightTaskStatus status = IndexInsightTaskStatus.GENERATING;
    private SearchHit[] sampleDocuments;
    
    public StatisticalDataTask(String indexName, Client client) {
        this.indexName = indexName;
        this.client = client;
    }
    
    @Override
    public void runTaskLogic() {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            collectSampleDocuments();
        } catch (Exception e) {
            log.error("Failed to execute statistical data task for index {}", indexName, e);
            saveFailedStatus();
        }
    }
    
    @Override
    public MLIndexInsightType getTaskType() {
        return taskType;
    }
    
    @Override
    public String getTargetIndex() {
        return indexName;
    }
    
    @Override
    public IndexInsightTaskStatus getStatus() {
        return status;
    }
    
    @Override
    public void setStatus(IndexInsightTaskStatus status) {
        this.status = status;
    }
    
    @Override
    public Client getClient() {
        return client;
    }
    
    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.emptyList();
    }
    
    public SearchHit[] getSampleDocuments() {
        return sampleDocuments;
    }
    
    private void collectSampleDocuments() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(5).query(new MatchAllQueryBuilder());
        SearchRequest searchRequest = new SearchRequest(new String[] { indexName }, searchSourceBuilder);
        
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            sampleDocuments = searchResponse.getHits().getHits();
            log.info("Collected {} sample documents for index: {}", sampleDocuments.length, indexName);
            
            String statisticalContent = generateStatisticalContent();
            saveResult(statisticalContent);
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", indexName, e);
            saveFailedStatus();
        }));
    }
    
    private String generateStatisticalContent() {
        StringBuilder content = new StringBuilder();
        content.append("Sample documents count: ").append(sampleDocuments.length).append("\\n");
        
        if (sampleDocuments.length > 0) {
            content.append("Sample document: ").append(sampleDocuments[0].getSourceAsString());
        }
        
        return content.toString();
    }
}