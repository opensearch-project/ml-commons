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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.utils.mergeMetaDataUtils.MergeRuleHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.AggregatorFactories;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;
import org.opensearch.search.aggregations.bucket.filter.InternalFilters;
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

    private static final int TERM_SIZE = 5;
    private static final List<String> PREFIXS = List.of("unique_terms_", "unique_count_", "max_value_", "min_value_");
    private static final List<String> UNIQUE_TERMS_LIST = List.of("text", "keyword", "integer", "long", "short");
    private static final List<String> MIN_MAX_LIST = List.of("integer", "long", "float", "double", "short", "date");
    private static final String NOT_NULL_KEYWORD = "not_null";
    private static final Double THRESHOLD = 0.01;
    private static final int SAMPLE_NUMBER = 100000;
    public static final String IMPORTANT_COLUMN_KEYWORD = "important_column_and_distribution";
    public static final String EXAMPLE_DOC_KEYWORD = "example_docs";

    private final String sourceIndex;
    private final Client client;
    private SearchHit[] sampleDocuments;

    public StatisticalDataTask(String sourceIndex, Client client) {
        this.sourceIndex = sourceIndex;
        this.client = client;
    }

    @Override
    public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        try {
            collectStatisticalData(storageIndex, listener);
        } catch (Exception e) {
            log.error("Failed to execute statistical data task for index {}", sourceIndex, e);
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }
    }

    @Override
    public MLIndexInsightType getTaskType() {
        return MLIndexInsightType.STATISTICAL_DATA;
    }

    @Override
    public String getSourceIndex() {
        return sourceIndex;
    }

    @Override
    public Client getClient() {
        return client;
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.emptyList();
    }

    private GetMappingsRequest buildGetMappingRequest(String indexName) {
        String[] indices = new String[] { indexName };
        GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
        getMappingsRequest.indices(indices);
        return getMappingsRequest;
    }

    private void collectStatisticalData(String storageIndex, ActionListener<IndexInsight> listener) {

        GetMappingsRequest getMappingsRequest = buildGetMappingRequest(sourceIndex);

        client.admin().indices().getMappings(getMappingsRequest, ActionListener.wrap(getMappingsResponse -> {

            Map<String, MappingMetadata> mappings = getMappingsResponse.getMappings();
            if (mappings.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("No matching mapping with index name: " + sourceIndex));
                return;
            }

            Map<String, Object> allFields = new HashMap<>();
            for (MappingMetadata mappingMetadata : mappings.values()) {
                Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
                MergeRuleHelper.merge(mappingSource, allFields);
            }
            Map<String, String> fieldsToType = new HashMap<>();
            extractFieldNamesTypes(allFields, fieldsToType, "", false);
            SearchRequest searchRequest = new SearchRequest(sourceIndex);
            searchRequest.source(buildQuery(fieldsToType));

            client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                sampleDocuments = searchResponse.getHits().getHits();
                Set<String> highPriorityColumns = filterColumns(fieldsToType, searchResponse);

                String statisticalContent = gson.toJson(parseSearchResult(fieldsToType, highPriorityColumns, searchResponse));
                saveResult(statisticalContent, storageIndex, listener);
            }, e -> {
                log.error("Failed to collect statistical data for index: {}", sourceIndex, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }));

        }, listener::onFailure));

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
                TermsAggregationBuilder termsAgg = AggregationBuilders.terms("unique_terms_" + name).field(fieldUsed).size(TERM_SIZE);

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
        TopHitsAggregationBuilder topHitsAgg = AggregationBuilders.topHits(EXAMPLE_DOC_KEYWORD).size(5);
        subAggs.addAggregator(topHitsAgg);

        // Add not none count
        List<KeyedFilter> keyedFilters = new ArrayList<>();
        for (String fieldName : fields.keySet()) {
            keyedFilters.add(new KeyedFilter(fieldName + "_" + NOT_NULL_KEYWORD, QueryBuilders.existsQuery(fieldName)));
        }
        FiltersAggregationBuilder nonNullAgg = AggregationBuilders.filters(NOT_NULL_KEYWORD, keyedFilters.toArray(new KeyedFilter[0]));
        subAggs.addAggregator(nonNullAgg);

        // Wrap everything in a Sampler aggregation
        SamplerAggregationBuilder samplerAgg = AggregationBuilders.sampler("sample").shardSize(SAMPLE_NUMBER).subAggregations(subAggs);

        // Build search source
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .sort("_doc", SortOrder.DESC)
            .size(0)
            .aggregation(samplerAgg);

        return sourceBuilder;
    }

    private Map<String, Object> parseSearchResult(
        Map<String, String> allFieldsToType,
        Set<String> filteredNames,
        SearchResponse searchResponse
    ) {
        Map<String, Aggregation> aggregationMap = ((InternalSampler) searchResponse.getAggregations().getAsMap().get("sample"))
            .getAggregations()
            .getAsMap();
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> finalResult = new LinkedHashMap<>();
        List<Object> exampleDocs = null;
        for (Map.Entry<String, Aggregation> entry : aggregationMap.entrySet()) {
            String key = entry.getKey();
            Aggregation aggregation = entry.getValue();
            if (key.equals(EXAMPLE_DOC_KEYWORD)) {
                SearchHit[] hits = ((InternalTopHits) aggregation).getHits().getHits();
                exampleDocs = new ArrayList<>(hits.length);
                for (SearchHit hit : hits) {
                    exampleDocs.add(hit.getSourceAsMap());
                }
            } else {
                for (String prefix : PREFIXS) {
                    if (key.startsWith(prefix)) {
                        String targetField = key.substring(prefix.length());
                        if (!filteredNames.contains(targetField)) {
                            continue;
                        }
                        String aggregationType = key.substring(0, prefix.length() - 1);
                        Map<String, Object> aggregationResult = gson.fromJson(aggregation.toString(), Map.class);
                        Object targetValue;
                        try {
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
                            result.computeIfAbsent(targetField, k -> new HashMap<>(Map.of("type", allFieldsToType.get(targetField))));
                            ((Map<String, Object>) result.get(targetField)).put(aggregationType, targetValue);
                            break;
                        } catch (Exception e) {
                            log.error("fail to parse result from DSL in statistical index insight for: ", e);
                        }
                    }
                }
            }
        }
        if (exampleDocs != null) {
            finalResult.put(EXAMPLE_DOC_KEYWORD, exampleDocs);
        }
        finalResult.put(IMPORTANT_COLUMN_KEYWORD, result);
        return finalResult;
    }

    private Set<String> filterColumns(Map<String, String> allFieldsToType, SearchResponse searchResponse) {
        Map<String, Aggregation> aggregationMap = ((InternalSampler) searchResponse.getAggregations().getAsMap().get("sample"))
            .getAggregations()
            .getAsMap();
        long totalDocCount = ((InternalSampler) searchResponse.getAggregations().getAsMap().get("sample")).getDocCount();
        Set<String> filteredNames = new HashSet<>();
        InternalFilters aggregation = (InternalFilters) aggregationMap.get(NOT_NULL_KEYWORD);
        for (InternalFilters.InternalBucket bucket : aggregation.getBuckets()) {
            String targetField = bucket.getKey();
            targetField = targetField.substring(0, targetField.length() - 1 - NOT_NULL_KEYWORD.length());
            long docCount = bucket.getDocCount();
            if (docCount > THRESHOLD * totalDocCount && allFieldsToType.containsKey(targetField)) {
                filteredNames.add(targetField);
            }
        }
        return filteredNames;
    }

}
