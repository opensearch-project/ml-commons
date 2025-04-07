package org.opensearch.ml.jobs.processors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

public class MLStatsJobProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MLStatsJobProcessor.class);

    private static MLStatsJobProcessor instance;

    public static MLStatsJobProcessor getInstance(ClusterService clusterService, Client client, ThreadPool threadPool) {
        if (instance != null) {
            return instance;
        }

        synchronized (MLStatsJobProcessor.class) {
            if (instance != null) {
                return instance;
            }

            instance = new MLStatsJobProcessor(clusterService, client, threadPool);
            return instance;
        }
    }

    public MLStatsJobProcessor(ClusterService clusterService, Client client, ThreadPool threadPool) {
        super(clusterService, client, threadPool);
    }

    @Override
    public void run() {
        // do something
        log.info("=======v==============vv=======MLStatsProcessor startedvv===================================");

        // fetch all models
    }

    public List<MLModel> fetchAllModels() {
        List<MLModel> models = new ArrayList<>();
        SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(10000); // Adjust this value based on your needs
        searchRequest.source(searchSourceBuilder);



        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                MLModel model = MLModel.parse(hit.getSourceAsMap());
                models.add(model);
            }
        } catch (IOException e) {
            log.error("Failed to fetch models from index", e);
        }

        return models;
    }
}
