package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.stats.otel.counters.MLAdoptionMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.AdoptionMetric;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

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
        // check if `.plugins-ml-model` index exists
        if (!clusterService.state().metadata().indices().containsKey(ML_MODEL_INDEX)) {
            log.info("Skipping ML Stats Collector job - ML model index not found");
            return;
        }

        SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(MLModel.CHUNK_NUMBER_FIELD));
        searchSourceBuilder.query(boolQuery);

        searchSourceBuilder.size(10_000);
        searchRequest.source(searchSourceBuilder);

        client.search(searchRequest, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                for (SearchHit hit : searchResponse.getHits()) {
                    try {
                        XContentParser parser = XContentType.JSON
                            .xContent()
                            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, hit.getSourceAsString());
                        parser.nextToken();
                        String algorithmName = hit.getSourceAsMap().get(MLModel.ALGORITHM_FIELD).toString();
                        MLModel model = MLModel.parse(parser, algorithmName);
                        MLAdoptionMetricsCounter.getInstance().incrementCounter(AdoptionMetric.MODEL_COUNT, model.getTags());
                    } catch (Exception e) {
                        log.error("Failed to parse model from hit: {}", hit.getId(), e);
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to fetch models", e);
            }
        });
    }
}
