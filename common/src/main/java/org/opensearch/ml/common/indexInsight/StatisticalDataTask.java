/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.utils.mergeMetaDataUtils.MergeRuleHelper;
import org.opensearch.remote.metadata.client.SdkClient;
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

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

/**
 * Statistical Data Task: Collects sample documents from the target index for analysis.
 * This task serves as the foundation for other index insight tasks by gathering sample data
 * that provides context about the index structure and content.
 * Will expand to support additional data types beyond sample documents in the future.
 */
@Log4j2
public class StatisticalDataTask extends AbstractIndexInsightTask {

    private static final int TERM_SIZE = 5;
    private static final List<String> PREFIXES = List.of("unique_terms_", "unique_count_", "max_value_", "min_value_");
    private static final List<String> UNIQUE_TERMS_LIST = List.of("text", "keyword", "integer", "long", "short");
    private static final List<String> MIN_MAX_LIST = List.of("integer", "long", "float", "double", "short", "date");
    private static final Double HIGH_PRIORITY_COLUMN_THRESHOLD = 0.001;
    private static final int SAMPLE_NUMBER = 100000;
    private static final String PARSE_COLUMN_NAME_PATTERN = "<column_name>(.*?)</column_name>";
    private static final int FILTER_LLM_NUMBERS = 30;
    public static final String NOT_NULL_KEYWORD = "not_null";
    public static final String IMPORTANT_COLUMN_KEYWORD = "important_column_and_distribution";
    public static final String EXAMPLE_DOC_KEYWORD = "example_docs";

    private static final String UNIQUE_TERM_PREFIX = "unique_terms_";
    private static final String MAX_VALUE_PREFIX = "max_value_";
    private static final String MIN_VALUE_PREFIX = "min_value_";
    private static final String UNIQUE_COUNT_PREFIX = "unique_count_";

    private static final String PROMPT_TEMPLATE = """
        Now I will give you the sample examples and some field's data distribution of one Opensearch index.
        You should help me filter at most %s important columns.
        For logs/trace/metric related indices, make sure you contain error/http response/time/latency/metric related columns.
        You should contain your response column name inside tag <column_name></column_name>
        Here is the information of sample examples and some field's data distribution.

        IndexName: %s
        detailed information: %s
        """;

    public StatisticalDataTask(String sourceIndex, Client client, SdkClient sdkClient) {
        super(MLIndexInsightType.STATISTICAL_DATA, sourceIndex, client, sdkClient);
    }

    @Override
    public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
        runTask(tenantId, listener, true);
    }

    public void runTask(String tenantId, ActionListener<IndexInsight> listener, boolean shouldStore) {
        try {
            collectStatisticalData(tenantId, shouldStore, listener);
        } catch (Exception e) {
            handleError("Failed to execute statistical data task for index: {}", e, tenantId, listener, shouldStore);
        }
    }

    @Override
    protected void handlePatternMatchedDoc(Map<String, Object> patternSource, String tenantId, ActionListener<IndexInsight> listener) {
        // For StatisticalDataTask, run without storing when pattern matched
        runTask(tenantId, listener, false);
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.emptyList();
    }

    private void collectStatisticalData(String tenantId, boolean shouldStore, ActionListener<IndexInsight> listener) {
        GetMappingsRequest getMappingsRequest = new GetMappingsRequest().indices(sourceIndex);

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
                Set<String> highPriorityColumns = filterColumns(fieldsToType, searchResponse);
                Map<String, Object> parsedResult = parseSearchResult(fieldsToType, highPriorityColumns, searchResponse);
                filterImportantColumnByLLM(parsedResult, tenantId, ActionListener.wrap(response -> {
                    Map<String, Object> filteredResponse = new HashMap<>();
                    filteredResponse
                        .put(
                            EXAMPLE_DOC_KEYWORD,
                            filterSampleColumns((List<Map<String, Object>>) parsedResult.get(EXAMPLE_DOC_KEYWORD), response)
                        );
                    Map<String, Object> importantColumns = (Map<String, Object>) parsedResult.get(IMPORTANT_COLUMN_KEYWORD);
                    Map<String, Object> filteredImportantColumns = importantColumns
                        .entrySet()
                        .stream()
                        .filter(entry -> response.isEmpty() || response.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    filteredResponse.put(IMPORTANT_COLUMN_KEYWORD, filteredImportantColumns);
                    String statisticalContent = gson.toJson(filteredResponse);

                    if (shouldStore) {
                        saveResult(statisticalContent, tenantId, listener);
                    } else {
                        // Return IndexInsight directly without storing
                        IndexInsight insight = IndexInsight
                            .builder()
                            .index(sourceIndex)
                            .taskType(taskType)
                            .content(statisticalContent)
                            .status(IndexInsightTaskStatus.COMPLETED)
                            .lastUpdatedTime(Instant.now())
                            .build();
                        listener.onResponse(insight);
                    }
                }, listener::onFailure));
            }, e -> handleError("Failed to collect statistical data for index: {}", e, tenantId, listener, shouldStore)));
        }, listener::onFailure));
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        throw new IllegalStateException("StatisticalDataTask has no prerequisites");
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
                TermsAggregationBuilder termsAgg = AggregationBuilders.terms(UNIQUE_TERM_PREFIX + name).field(fieldUsed).size(TERM_SIZE);

                CardinalityAggregationBuilder countAgg = AggregationBuilders.cardinality(UNIQUE_COUNT_PREFIX + name).field(fieldUsed);

                subAggs.addAggregator(termsAgg);
                subAggs.addAggregator(countAgg);
            }
            if (MIN_MAX_LIST.contains(type)) {
                MinAggregationBuilder minAgg = AggregationBuilders.min(MIN_VALUE_PREFIX + name).field(fieldUsed);
                MaxAggregationBuilder maxAgg = AggregationBuilders.max(MAX_VALUE_PREFIX + name).field(fieldUsed);

                subAggs.addAggregator(minAgg);
                subAggs.addAggregator(maxAgg);
            }
        }

        // Add top hits example_docs
        TopHitsAggregationBuilder topHitsAgg = AggregationBuilders.topHits(EXAMPLE_DOC_KEYWORD).size(3);
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

    private void filterImportantColumnByLLM(Map<String, Object> parsedResult, String tenantId, ActionListener<List<String>> listener) {
        Map<String, Object> importantColumns = (Map<String, Object>) parsedResult.get(IMPORTANT_COLUMN_KEYWORD);
        if (importantColumns.size() <= FILTER_LLM_NUMBERS) {
            listener.onResponse(new ArrayList<>()); // Too few columns and don't need to filter
            return;
        }
        String prompt = generateFilterColumnPrompt(parsedResult);
        getAgentIdToRun(client, tenantId, ActionListener.wrap(agentId -> {
            callLLMWithAgent(client, agentId, prompt, tenantId, ActionListener.wrap(response -> {
                listener.onResponse(parseLLMFilteredResult(response));
            }, e -> { listener.onResponse(new ArrayList<>()); }));
        }, e -> { listener.onResponse(new ArrayList<>()); }));
    }

    private String generateFilterColumnPrompt(Map<String, Object> parsedResult) {
        return String.format(PROMPT_TEMPLATE, FILTER_LLM_NUMBERS, sourceIndex, gson.toJson(parsedResult));
    }

    @VisibleForTesting
    List<Map<String, Object>> filterSampleColumns(List<Map<String, Object>> originalDocs, List<String> targetColumns) {
        if (targetColumns.isEmpty()) {
            return originalDocs;
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> originalDoc : originalDocs) {
            results.add(constructFilterMap("", originalDoc, targetColumns));
        }
        return results;
    }

    private Map<String, Object> constructFilterMap(String prefix, Map<String, Object> currentNode, List<String> targetColumns) {
        Map<String, Object> filterResult = new HashMap<>();
        for (Map.Entry<String, Object> entry : currentNode.entrySet()) {
            String currentKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object currentValue = entry.getValue();
            if (targetColumns.contains(currentKey)) {
                filterResult.put(entry.getKey(), currentValue);
            } else if (currentValue instanceof Map) {
                Map<String, Object> tmpNode = constructFilterMap(currentKey, (Map<String, Object>) currentValue, targetColumns);
                if (!tmpNode.isEmpty()) {
                    filterResult.put(entry.getKey(), tmpNode);
                }
            } else if (currentValue instanceof List) {
                List<?> list = (List<?>) currentValue;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    List<Map<String, Object>> newList = new ArrayList<>();
                    for (Object item : list) {
                        Map<String, Object> tmpNode = constructFilterMap(currentKey, (Map<String, Object>) item, targetColumns);
                        if (!tmpNode.isEmpty()) {
                            newList.add(tmpNode);
                        }
                    }
                    if (!newList.isEmpty()) {
                        filterResult.put(entry.getKey(), newList);
                    }
                }
            }
        }
        return filterResult;
    }

    private List<String> parseLLMFilteredResult(String LLMResponse) {
        try {
            Pattern pattern = Pattern.compile(PARSE_COLUMN_NAME_PATTERN);
            Matcher matcher = pattern.matcher(LLMResponse);
            List<String> columns = new ArrayList<>();
            while (matcher.find()) {
                columns.add(matcher.group(1).trim());
            }
            return columns;
        } catch (Exception e) {
            throw new IllegalArgumentException("fail to parse LLM response");
        }
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
                for (String prefix : PREFIXES) {
                    if (key.startsWith(prefix)) {
                        String targetField = key.substring(prefix.length());
                        if (!filteredNames.contains(targetField)) {
                            continue;
                        }
                        String aggregationType = key.substring(0, prefix.length() - 1);
                        Map<String, Object> aggregationResult = gson.fromJson(aggregation.toString(), Map.class);
                        Object targetValue;
                        try {
                            if (prefix.equals(UNIQUE_TERM_PREFIX)) {
                                // assuming result.get(key) is a Map containing "buckets" -> List<Map<String, Object>>
                                Map<String, Object> aggResult = (Map<String, Object>) aggregationResult.get(key);
                                List<Map<String, Object>> buckets = aggResult != null
                                    ? (List<Map<String, Object>>) aggResult.get("buckets")
                                    : null;
                                if (buckets == null) {
                                    continue;
                                }

                                targetValue = buckets.stream().filter(bucket -> bucket != null).map(bucket -> bucket.get("key")).toList();
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
                            log
                                .error(
                                    "Failed to parse aggregation result from DSL in statistical index insight for index: {}",
                                    sourceIndex,
                                    e
                                );
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
        InternalSampler sampleAggregation = ((InternalSampler) searchResponse.getAggregations().getAsMap().get("sample"));
        Map<String, Aggregation> aggregationMap = sampleAggregation.getAggregations().getAsMap();
        long totalDocCount = sampleAggregation.getDocCount();
        Set<String> filteredNames = new HashSet<>();
        InternalFilters aggregation = (InternalFilters) aggregationMap.get(NOT_NULL_KEYWORD);
        for (InternalFilters.InternalBucket bucket : aggregation.getBuckets()) {
            String targetField = bucket.getKey();
            targetField = targetField.substring(0, targetField.length() - 1 - NOT_NULL_KEYWORD.length());
            long docCount = bucket.getDocCount();
            if (docCount > HIGH_PRIORITY_COLUMN_THRESHOLD * totalDocCount && allFieldsToType.containsKey(targetField)) {
                filteredNames.add(targetField);
            }
        }
        return filteredNames;
    }

}
