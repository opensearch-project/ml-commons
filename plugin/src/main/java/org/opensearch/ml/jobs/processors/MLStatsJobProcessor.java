/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.stats.otel.counters.MLAdoptionMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.AdoptionMetric;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

public class MLStatsJobProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MLStatsJobProcessor.class);

    // Tag constants for agent model information
    private static final String TAG_AGENT_MODEL = "model";
    private static final String TAG_AGENT_MODEL_SERVICE_PROVIDER = "model_service_provider";
    private static final String TAG_AGENT_MODEL_DEPLOYMENT = "model_deployment";
    private static final String TAG_AGENT_MODEL_TYPE = "model_type";

    // Model tag keys for lookup
    private static final String MODEL_TAG_MODEL = "model";
    private static final String MODEL_TAG_SERVICE_PROVIDER = "service_provider";
    private static final String MODEL_TAG_DEPLOYMENT = "deployment";
    private static final String MODEL_TAG_TYPE = "type";

    private static final int BATCH_SIZE = 10_000;

    private static MLStatsJobProcessor instance;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final SdkClient sdkClient;
    private final Map<String, Tags> modelTagsCache = new HashMap<>();

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
        modelTagsCache.clear();
        collectModelAndAgentMetrics();
    }

    private void collectModelAndAgentMetrics() {
        // check if `.plugins-ml-model` index exists
        if (!clusterService.state().metadata().indices().containsKey(ML_MODEL_INDEX)) {
            log.info("Skipping ML model metrics collection - ML model index not found");
            return;
        }

        SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(MLModel.CHUNK_NUMBER_FIELD));
        searchSourceBuilder.query(boolQuery);

        searchSourceBuilder.size(BATCH_SIZE);
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
                        String modelId = model.getModelId() == null ? hit.getId() : model.getModelId();

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
                                        ActionListener.wrap(connector -> {
                                            Tags modelTags = model.getTags(connector);
                                            modelTagsCache.put(modelId, modelTags);
                                            MLAdoptionMetricsCounter.getInstance().incrementCounter(AdoptionMetric.MODEL_COUNT, modelTags);
                                        }, e -> log.error("Failed to get connector for model: {}", modelId, e))
                                    );
                            }

                            continue;
                        }

                        Tags modelTags = model.getTags();
                        modelTagsCache.put(modelId, modelTags);
                        MLAdoptionMetricsCounter.getInstance().incrementCounter(AdoptionMetric.MODEL_COUNT, modelTags);
                    } catch (Exception e) {
                        log.error("Failed to parse model from hit: {}", hit.getId(), e);
                    }
                }

                // dependent on model tags to capture rich information about agents

                collectAgentMetrics();
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to fetch models", e);
            }
        });
    }

    private void collectAgentMetrics() {
        // check if `.plugins-ml-agent` index exists
        if (!clusterService.state().metadata().indices().containsKey(ML_AGENT_INDEX)) {
            log.info("Skipping ML agent metrics collection - ML agent index not found");
            return;
        }

        SearchRequest searchRequest = new SearchRequest(ML_AGENT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(BATCH_SIZE);
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

                        MLAgent agent = MLAgent.parse(parser);
                        Tags agentTags = agent.getTags();

                        // Add model and provider info if agent has LLM spec
                        Optional
                            .of(agent)
                            .map(MLAgent::getLlm)
                            .map(LLMSpec::getModelId)
                            .map(modelTagsCache::get)
                            .map(Tags::getTagsMap)
                            .ifPresent(tagsMap -> {
                                addTagIfExists(tagsMap, MODEL_TAG_MODEL, TAG_AGENT_MODEL, agentTags);
                                addTagIfExists(tagsMap, MODEL_TAG_SERVICE_PROVIDER, TAG_AGENT_MODEL_SERVICE_PROVIDER, agentTags);
                                addTagIfExists(tagsMap, MODEL_TAG_DEPLOYMENT, TAG_AGENT_MODEL_DEPLOYMENT, agentTags);
                                addTagIfExists(tagsMap, MODEL_TAG_TYPE, TAG_AGENT_MODEL_TYPE, agentTags);
                            });

                        MLAdoptionMetricsCounter.getInstance().incrementCounter(AdoptionMetric.AGENT_COUNT, agentTags);
                    } catch (Exception e) {
                        log.error("Failed to parse agent from hit: {}", hit.getId(), e);
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to fetch agents", e);
            }
        });
    }

    @VisibleForTesting
    void addTagIfExists(Map<String, ?> sourceTagsMap, String sourceKey, String targetKey, Tags targetTags) {
        if (sourceTagsMap.containsKey(sourceKey) && sourceTagsMap.get(sourceKey) != null) {
            targetTags.addTag(targetKey, (String) sourceTagsMap.get(sourceKey));
        }
    }
}
