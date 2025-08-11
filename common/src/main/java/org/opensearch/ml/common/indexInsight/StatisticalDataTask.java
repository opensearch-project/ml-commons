/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.extractFieldNamesTypes;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.AggregatorFactories;
import org.opensearch.search.aggregations.bucket.sampler.InternalSampler;
import org.opensearch.search.aggregations.bucket.sampler.SamplerAggregationBuilder;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.CardinalityAggregationBuilder;
import org.opensearch.search.aggregations.metrics.InternalTopHits;
import org.opensearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.opensearch.search.aggregations.metrics.MinAggregationBuilder;
import org.opensearch.search.aggregations.metrics.TopHitsAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
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

    public static int termSize = 5;
    private static List<String> prefixs = List.of("unique_terms_", "unique_count_", "max_value_", "min_value_");
    private static List<String> UNIQUE_TERMS_LIST = List.of("text", "keyword", "integer", "long", "short");
    private static List<String> MIN_MAX_LIST = List.of("integer", "long", "float", "double", "short", "date");

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
    public void runTaskLogic(String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            collectStatisticalData(targetIndex, listener);
        } catch (Exception e) {
            log.error("Failed to execute statistical data task for index {}", indexName, e);
            saveFailedStatus(targetIndex);
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

    private GetMappingsRequest buildGetMappingRequest(String indexName) {
        String[] indices = new String[] { indexName };
        GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
        getMappingsRequest.indices(indices);
        return getMappingsRequest;
    }

    private void collectStatisticalData(String targetIndex, ActionListener<IndexInsight> listener) {

        GetMappingsRequest getMappingsRequest = buildGetMappingRequest(indexName);

        client.admin().indices().getMappings(getMappingsRequest, ActionListener.wrap(getMappingsResponse -> {

            Map<String, MappingMetadata> mappings = getMappingsResponse.getMappings();
            if (mappings.isEmpty()) {
                throw new IllegalArgumentException("No matching mapping with index name: " + indexName);
            }
            String firstIndexName = (String) mappings.keySet().toArray()[0];
            Map<String, String> fieldsToType = new HashMap<>();
            Map<String, Object> mappingSource = (Map<String, Object>) mappings.get(firstIndexName).getSourceAsMap().get("properties");
            extractFieldNamesTypes(mappingSource, fieldsToType, "", false);
            SearchRequest searchRequest = new SearchRequest(indexName);
            searchRequest.source(buildQuery(fieldsToType));

            client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                sampleDocuments = searchResponse.getHits().getHits();
                Map<String, Object> result = new HashMap<>();
                result.put("distribution", parseSearchResult(searchResponse));
                result.put("mapping", mappingSource);
                log.info("Collected {} sample documents for index: {}", sampleDocuments.length, indexName);

                // Create ordered result with example_docs at the end
                Map<String, Object> orderedResult = new LinkedHashMap<>();
                result.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("example_docs"))
                    .forEach(entry -> orderedResult.put(entry.getKey(), entry.getValue()));
                if (result.containsKey("example_docs")) {
                    orderedResult.put("example_docs", result.get("example_docs"));
                }
                String statisticalContent = gson.toJson(orderedResult);
                saveResult(statisticalContent, targetIndex, listener);
            }, e -> {
                log.error("Failed to collect statistical data for index: {}", indexName, e);
                saveFailedStatus(targetIndex);
                listener.onFailure(e);
            }));

        }, listener::onFailure));

    }

    private String generateStatisticalContent() {
        StringBuilder content = new StringBuilder();
        content.append("Sample documents count: ").append(sampleDocuments.length).append("\\n");

        for (int i = 0; i < sampleDocuments.length; i++) {
            content.append("Sample document ").append(i + 1).append(": ").append(sampleDocuments[i].getSourceAsString()).append("\\n");
        }

        return content.toString();
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        throw new IllegalArgumentException("StatisticalDataTask has no prerequisites");
    }

    public SearchSourceBuilder buildQuery(Map<String, String> fields) {
        AggregatorFactories.Builder subAggs = new AggregatorFactories.Builder();

        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey();
            String type = field.getValue();
            String fieldUsed = name;

            if ("text".equals(type)) {
                fieldUsed = name + ".keyword";
            }

            if (UNIQUE_TERMS_LIST.contains(type)) {
                TermsAggregationBuilder termsAgg = AggregationBuilders.terms("unique_terms_" + name).field(fieldUsed).size(termSize);

                CardinalityAggregationBuilder countAgg = AggregationBuilders.cardinality("unique_count_" + name).field(fieldUsed);

                subAggs.addAggregator(termsAgg);
                subAggs.addAggregator(countAgg);
            }
            if (MIN_MAX_LIST.contains(type)) {
                MinAggregationBuilder minAgg = AggregationBuilders.min("min_value_" + name).field(fieldUsed);
                MaxAggregationBuilder maxAgg = AggregationBuilders.max("max_value_" + name).field(fieldUsed);

                subAggs.addAggregator(minAgg);
                subAggs.addAggregator(maxAgg);
            }
        }

        // Add top hits example_docs
        TopHitsAggregationBuilder topHitsAgg = AggregationBuilders.topHits("example_docs").size(5);
        subAggs.addAggregator(topHitsAgg);

        // Wrap everything in a Sampler aggregation
        SamplerAggregationBuilder samplerAgg = AggregationBuilders.sampler("sample").shardSize(100000).subAggregations(subAggs);

        // Build search source
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .sort("_doc", SortOrder.DESC)
            .size(0)
            .aggregation(samplerAgg);

        return sourceBuilder;
    }

    private Map<String, Object> parseSearchResult(SearchResponse searchResponse) {
        Map<String, Aggregation> aggregationMap = ((InternalSampler) searchResponse.getAggregations().getAsMap().get("sample"))
            .getAggregations()
            .getAsMap();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Aggregation> entry : aggregationMap.entrySet()) {
            String key = entry.getKey();
            Aggregation aggregation = entry.getValue();
            if (key.equals("example_docs")) {
                SearchHit[] hits = ((InternalTopHits) aggregation).getHits().getHits();
                List<Object> values = new ArrayList<>();
                for (SearchHit hit : hits) {
                    values.add(hit.getSourceAsMap());
                }
                result.put(key, values);
            } else {
                for (String prefix : prefixs) {
                    if (key.startsWith(prefix)) {
                        String targetField = key.substring(prefix.length());
                        String aggregationType = key.substring(0, prefix.length() - 1);
                        Map<String, Object> aggregationResult = gson.fromJson(aggregation.toString(), Map.class);
                        Object targetValue;
                        if (prefix.equals("unique_terms_")) {
                            // assuming result.get(key) is a Map containing "buckets" -> List<Map<String, Object>>
                            Map<String, Object> aggResult = (Map<String, Object>) aggregationResult.get(key);
                            List<Map<String, Object>> buckets = (List<Map<String, Object>>) aggResult.get("buckets");

                            List<Object> values = new ArrayList<>();
                            for (Map<String, Object> bucket : buckets) {
                                values.add(bucket.get("key"));
                            }
                            targetValue = values;
                        } else {
                            Map<String, Object> aggResult = (Map<String, Object>) aggregationResult.get(key);
                            if (aggResult.containsKey("value_as_string")) {
                                targetValue = aggResult.get("value_as_string");
                            } else {
                                targetValue = aggResult.get("value");
                            }
                        }
                        result.computeIfAbsent(targetField, k -> new HashMap<>());
                        ((Map<String, Object>) result.get(targetField)).put(aggregationType, targetValue);
                        break;
                    }
                }
            }
        }
        return result;
    }

}
