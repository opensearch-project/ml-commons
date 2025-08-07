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

/**
 * Statistical Data Task: Collects sample documents from the target index for analysis.
 * This task serves as the foundation for other index insight tasks by gathering sample data
 * that provides context about the index structure and content.
 * Will expand to support additional data types beyond sample documents in the future.
 */
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
    public void runTaskLogic(ActionListener<IndexInsight> listener) {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            collectSampleDocuments(listener);
        } catch (Exception e) {
            log.error("Failed to execute statistical data task for index {}", indexName, e);
            saveFailedStatus();
            listener.onFailure(e);
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
    
    private void collectSampleDocuments(ActionListener<IndexInsight> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(5).query(new MatchAllQueryBuilder());
        SearchRequest searchRequest = new SearchRequest(new String[] { indexName }, searchSourceBuilder);
        
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            sampleDocuments = searchResponse.getHits().getHits();
            log.info("Collected {} sample documents for index: {}", sampleDocuments.length, indexName);
            
            String statisticalContent = generateStatisticalContent();
            saveResult(statisticalContent, listener);
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", indexName, e);
            saveFailedStatus();
            listener.onFailure(e);
        }));
    }
    
    private String generateStatisticalContent() {
        StringBuilder content = new StringBuilder();
        content.append("Sample documents count: ").append(sampleDocuments.length).append("\\n");
        
        for (int i = 0; i < sampleDocuments.length; i++) {
            content.append("Sample document ").append(i + 1).append(": ").append(sampleDocuments[i].getSourceAsString()).append("\\n");
        }
        
        return content.toString();
    }
}