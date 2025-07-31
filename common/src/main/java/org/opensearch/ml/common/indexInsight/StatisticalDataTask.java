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
    
    private final String targetIndex;
    private final Client client;
    private String status = "pending";
    private SearchHit[] sampleDocuments;
    
    public StatisticalDataTask(String targetIndex, Client client) {
        this.targetIndex = targetIndex;
        this.client = client;
    }
    
    @Override
    public void runTaskLogic() {
        status = "generating";
        try {
            collectSampleDocuments();
        } catch (Exception e) {
            log.error("Failed to execute statistical data task for index {}", targetIndex, e);
            saveFailedStatus();
        }
    }
    
    @Override
    public MLIndexInsightType getTaskType() {
        return MLIndexInsightType.STATISTICAL_DATA;
    }
    
    @Override
    public String getTargetIndex() {
        return targetIndex;
    }
    
    @Override
    public String getStatus() {
        return status;
    }
    
    @Override
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public Client getClient() {
        return client;
    }
    
    @Override
    public List<MLIndexInsightType> getDependencies() {
        return Collections.emptyList();
    }
    
    public SearchHit[] getSampleDocuments() {
        return sampleDocuments;
    }
    
    private void collectSampleDocuments() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(5).query(new MatchAllQueryBuilder());
        SearchRequest searchRequest = new SearchRequest(new String[] { targetIndex }, searchSourceBuilder);
        
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            sampleDocuments = searchResponse.getHits().getHits();
            log.info("Collected {} sample documents for index: {}", sampleDocuments.length, targetIndex);
            
            String statisticalContent = generateStatisticalContent();
            saveResult(statisticalContent);
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", targetIndex, e);
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