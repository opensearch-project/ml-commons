/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.stats.otel.counters.MLAdoptionMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.AdoptionMetric;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLStatsJobProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MLStatsJobProcessor.class);

    private static MLStatsJobProcessor instance;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final SdkClient sdkClient;

    public static MLStatsJobProcessor getInstance(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        SdkClient sdkClient
    ) {
        if (instance != null) {
            return instance;
        }

        synchronized (MLStatsJobProcessor.class) {
            if (instance != null) {
                return instance;
            }

            instance = new MLStatsJobProcessor(clusterService, client, threadPool, connectorAccessControlHelper, sdkClient);
            return instance;
        }
    }

    /**
     * Resets the singleton instance. This method is only for testing purposes.
     */
    public static synchronized void reset() {
        instance = null;
    }

    public MLStatsJobProcessor(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        SdkClient sdkClient
    ) {
        super(clusterService, client, threadPool);
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.sdkClient = sdkClient;
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

                        if (model.getConnector() == null && model.getConnectorId() != null) {
                            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                                GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
                                    .builder()
                                    .index(ML_CONNECTOR_INDEX)
                                    .id(model.getConnectorId())
                                    .build();

                                connectorAccessControlHelper
                                    .getConnector(
                                        sdkClient,
                                        client,
                                        context,
                                        getDataObjectRequest,
                                        model.getConnectorId(),
                                        ActionListener
                                            .wrap(
                                                connector -> MLAdoptionMetricsCounter
                                                    .getInstance()
                                                    .incrementCounter(AdoptionMetric.MODEL_COUNT, model.getTags(connector)),
                                                e -> log.error("Failed to get connector for model: {}", model.getModelId(), e)
                                            )
                                    );
                            }

                            return;
                        }

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
