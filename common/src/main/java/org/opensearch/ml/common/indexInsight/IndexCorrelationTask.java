/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_STORAGE_INDEX;
import static org.opensearch.ml.common.CommonValue.PATTERN_TYPE_CACHE_EXPIRATION;
import static org.opensearch.ml.common.utils.StringUtils.MAPPER;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.core.action.ActionListener;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.hash.Hashing;

import lombok.extern.log4j.Log4j2;

/**
 * Task to find correlated indices across metrics, logs, and traces.
 *
 * Algorithm:
 * 1. List all indices in the cluster
 * 2. Extract index patterns based on name overlapping
 * 3. For each pattern, use LLM to determine type (LOG/TRACE/METRIC)
 * 4. Return tuple with field information (spanId, traceId, time columns)
 */
@Log4j2
public class IndexCorrelationTask extends AbstractIndexInsightTask {

    private static final String LLM_TYPE_DETECTION_TEMPLATE =
        """
            I will provide you with an index pattern, sample index names, mapping fields, and actual sample documents.

            Index Pattern: {pattern}
            Sample Indices: {sampleIndices}
            Sample Mapping Fields: {sampleFields}

            Sample Documents (up to 5):
            {sampleDocuments}

            Please determine if this index pattern represents LOG, TRACE, or METRIC data based on:
            1. Index naming conventions (e.g., "logs-", "jaeger-span-", "metrics-")
            2. Field names in the mapping (e.g., "spanId", "traceId", "severity", "message")
            3. Actual document content and structure from the sample documents
            4. Common observability patterns (OpenTelemetry, Jaeger, Prometheus, etc.)

            Return your analysis in the following JSON format inside tags:

            <index_type_analysis>
            {
              "type": "LOG" | "TRACE" | "METRIC" | "UNKNOWN",
              "confidence": "high" | "medium" | "low",
              "reasoning": "brief explanation",
              "time_field": "name of the time field or null",
              "trace_id_field": "name of trace ID field or null (for TRACE/LOG)",
              "span_id_field": "name of span ID field or null (for TRACE/LOG)"
            }
            </index_type_analysis>

            Rules:
            - If you cannot confidently determine the type, use "UNKNOWN"
            - time_field should be the primary timestamp field
            - trace_id_field and span_id_field are only relevant for TRACE and LOG types
            """;

    private List<String> allIndices;
    private Map<String, PatternInfo> detectedPatterns;

    public IndexCorrelationTask(String sourceIndex, Client client, SdkClient sdkClient) {
        super(MLIndexInsightType.INDEX_CORRELATION, sourceIndex, client, sdkClient);
        this.detectedPatterns = new HashMap<>();
    }

    @Override
    public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
        try {
            // Step 1: List all indices
            listAllIndices(ActionListener.wrap(indices -> {
                allIndices = indices;
                log.info("Found {} indices in cluster", allIndices.size());

                // Step 2: Extract patterns
                Map<String, List<String>> patterns = extractIndexPatterns(allIndices);
                log.info("Extracted {} index patterns", patterns.size());

                // Step 3: Detect types for each pattern
                detectPatternTypes(patterns, tenantId, ActionListener.wrap(patternInfoMap -> {
                    detectedPatterns = patternInfoMap;

                    // Step 4: Build result
                    buildCorrelationResult(tenantId, ActionListener.wrap(result -> {
                        saveResult(MAPPER.writeValueAsString(result), tenantId, ActionListener.wrap(insight -> {
                            log.info("Index correlation completed for index: {}", sourceIndex);
                            listener.onResponse(insight);
                        }, e -> handleError("Failed to save correlation result for {}", e, tenantId, listener)));
                    }, e -> handleError("Failed to build correlation result for {}", e, tenantId, listener)));

                }, e -> handleError("Failed to detect pattern types for {}", e, tenantId, listener)));

            }, e -> handleError("Failed to list indices for {}", e, tenantId, listener)));

        } catch (Exception e) {
            handleError("Failed index correlation for {}", e, tenantId, listener);
        }
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.emptyList();
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        throw new IllegalStateException("IndexCorrelationTask has no prerequisites");
    }

    /**
     * List all indices in the cluster
     */
    private void listAllIndices(ActionListener<List<String>> listener) {
        ClusterStateRequest request = new ClusterStateRequest();
        request.clear().metadata(true);
        request.indices("*");

        client.admin().cluster().state(request, ActionListener.wrap(response -> {
            try {
                List<String> indices = new ArrayList<>();
                response.getState().metadata().getIndices().keySet().forEach(index -> {
                    // Filter out system indices
                    if (!index.startsWith(".")) {
                        indices.add(index);
                    }
                });
                listener.onResponse(indices);
            } catch (Exception e) {
                log.error("Failed to parse cluster state", e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to get cluster state", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Extract index patterns based on name overlapping
     * Logic: Group indices by name similarity - if overlap exceeds threshold, they belong to same pattern
     */
    private Map<String, List<String>> extractIndexPatterns(List<String> indices) {
        if (indices.isEmpty()) {
            return new HashMap<>();
        }

        // Threshold for considering indices as same pattern (70% overlap)
        final double OVERLAP_THRESHOLD = 0.7;

        List<IndexGroup> groups = new ArrayList<>();

        // Group indices by similarity
        for (String index : indices) {
            boolean addedToGroup = false;

            // Try to find a matching group
            for (IndexGroup group : groups) {
                double overlap = calculateOverlap(index, group.getRepresentative());
                if (overlap >= OVERLAP_THRESHOLD) {
                    group.addIndex(index);
                    addedToGroup = true;
                    break;
                }
            }

            // If no matching group, create new one
            if (!addedToGroup) {
                groups.add(new IndexGroup(index));
            }
        }

        // Extract patterns from groups
        Map<String, List<String>> patterns = new HashMap<>();
        for (IndexGroup group : groups) {
            String pattern = extractPattern(group.getIndices());
            patterns.put(pattern, group.getIndices());
        }

        return patterns;
    }

    /**
     * Calculate name overlap between two index names using Longest Common Subsequence (LCS)
     * Returns a value between 0.0 and 1.0
     *
     * LCS captures the similarity between strings even when differences are in the middle,
     * making it more robust than simple prefix/suffix matching.
     */
    private double calculateOverlap(String index1, String index2) {
        if (index1.equals(index2)) {
            return 1.0;
        }

        // Calculate LCS length
        int lcsLength = longestCommonSubsequence(index1, index2);

        // Calculate overlap ratio based on LCS
        int maxLen = Math.max(index1.length(), index2.length());

        return (double) lcsLength / maxLen;
    }

    /**
     * Compute the length of the Longest Common Subsequence (LCS) between two strings
     * Uses dynamic programming with space optimization
     *
     * Time Complexity: O(m * n)
     * Space Complexity: O(min(m, n))
     */
    private int longestCommonSubsequence(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0;
        }

        // Ensure s1 is the shorter string for space optimization
        if (s1.length() > s2.length()) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
        }

        int m = s1.length();
        int n = s2.length();

        // Use two rows instead of full 2D array for space optimization
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        // Fill the DP table
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    curr[i] = prev[i - 1] + 1;
                } else {
                    curr[i] = Math.max(curr[i - 1], prev[i]);
                }
            }
            // Swap rows
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[m];
    }

    /**
     * Extract pattern from a list of similar index names
     * Finds common prefix and suffix, replaces varying part with wildcard
     */
    private String extractPattern(List<String> indices) {
        if (indices.isEmpty()) {
            return "";
        }

        if (indices.size() == 1) {
            return indices.get(0);
        }

        // Find longest common prefix
        String commonPrefix = indices.get(0);
        for (int i = 1; i < indices.size(); i++) {
            commonPrefix = getCommonPrefix(commonPrefix, indices.get(i));
        }

        // Find longest common suffix
        String commonSuffix = indices.get(0);
        for (int i = 1; i < indices.size(); i++) {
            commonSuffix = getCommonSuffix(commonSuffix, indices.get(i));
        }

        // Avoid overlap between prefix and suffix
        if (commonPrefix.length() + commonSuffix.length() >= indices.get(0).length()) {
            // If prefix and suffix would overlap, just use prefix
            return commonPrefix.isEmpty() ? indices.get(0) : commonPrefix + "*";
        }

        // Build pattern: prefix + * + suffix
        if (commonPrefix.isEmpty() && commonSuffix.isEmpty()) {
            // No common parts, use first index with wildcard
            return indices.get(0).replaceAll("\\d+", "*");
        } else if (commonSuffix.isEmpty()) {
            return commonPrefix + "*";
        } else if (commonPrefix.isEmpty()) {
            return "*" + commonSuffix;
        } else {
            return commonPrefix + "*" + commonSuffix;
        }
    }

    /**
     * Get common prefix of two strings
     */
    private String getCommonPrefix(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return s1.substring(0, i);
            }
        }
        return s1.substring(0, minLen);
    }

    /**
     * Get common suffix of two strings
     */
    private String getCommonSuffix(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int minLen = Math.min(len1, len2);

        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(len1 - 1 - i) != s2.charAt(len2 - 1 - i)) {
                return s1.substring(len1 - i);
            }
        }
        return s1.substring(len1 - minLen);
    }

    /**
     * Helper class to group similar indices
     */
    private static class IndexGroup {
        private final List<String> indices;
        private final String representative;

        IndexGroup(String firstIndex) {
            this.indices = new ArrayList<>();
            this.indices.add(firstIndex);
            this.representative = firstIndex;
        }

        void addIndex(String index) {
            this.indices.add(index);
        }

        List<String> getIndices() {
            return indices;
        }

        String getRepresentative() {
            return representative;
        }
    }

    /**
     * Detect types for each pattern using LLM
     */
    private void detectPatternTypes(
        Map<String, List<String>> patterns,
        String tenantId,
        ActionListener<Map<String, PatternInfo>> listener
    ) {
        Map<String, PatternInfo> result = new HashMap<>();
        List<String> patternKeys = new ArrayList<>(patterns.keySet());

        if (patternKeys.isEmpty()) {
            listener.onResponse(result);
            return;
        }

        detectNextPattern(patterns, patternKeys, 0, tenantId, result, listener);
    }

    /**
     * Recursively detect pattern types with caching
     */
    private void detectNextPattern(
        Map<String, List<String>> patterns,
        List<String> patternKeys,
        int index,
        String tenantId,
        Map<String, PatternInfo> result,
        ActionListener<Map<String, PatternInfo>> listener
    ) {
        if (index >= patternKeys.size()) {
            listener.onResponse(result);
            return;
        }

        String pattern = patternKeys.get(index);
        List<String> sampleIndices = patterns.get(pattern);

        // Step 1: Check cache first
        getPatternFromCache(pattern, tenantId, ActionListener.wrap(cachedPatternInfo -> {
            if (cachedPatternInfo != null) {
                // Cache hit - use cached result but update with current sample indices
                PatternInfo updatedInfo = new PatternInfo(
                    pattern,
                    sampleIndices,  // Use current sample indices
                    cachedPatternInfo.type,
                    cachedPatternInfo.timeField,
                    cachedPatternInfo.traceIdField,
                    cachedPatternInfo.spanIdField
                );
                result.put(pattern, updatedInfo);

                // Process next pattern
                detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                return;
            }

            // Step 2: Cache miss - get mapping, sample documents, and call LLM
            String sampleIndex = sampleIndices.get(0);
            getMappingFields(sampleIndex, ActionListener.wrap(fields -> {
                // Get sample documents
                getSampleDocuments(sampleIndex, ActionListener.wrap(sampleDocs -> {
                    // Use LLM to detect type
                    detectPatternType(pattern, sampleIndices, fields, sampleDocs, tenantId, ActionListener.wrap(patternInfo -> {
                        result.put(pattern, patternInfo);

                        // Step 3: Save to cache (fire and forget)
                        savePatternToCache(patternInfo, tenantId, ActionListener.wrap(
                            success -> {}, // Ignore success
                            e -> log.warn("Cache save failed for pattern: {}", pattern, e)
                        ));

                        // Process next pattern
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);

                    }, e -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e);
                        // Continue with next pattern even if this one fails
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }));

                }, e -> {
                    log.warn("Failed to get sample documents for index: {}, proceeding with empty list", sampleIndex, e);
                    // Proceed with empty sample documents list
                    detectPatternType(pattern, sampleIndices, fields, Collections.emptyList(), tenantId, ActionListener.wrap(patternInfo -> {
                        result.put(pattern, patternInfo);
                        savePatternToCache(patternInfo, tenantId, ActionListener.wrap(
                            success -> {},
                            e2 -> log.warn("Cache save failed for pattern: {}", pattern, e2)
                        ));
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }, e2 -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e2);
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }));
                }));

            }, e -> {
                log.error("Failed to get mapping for index: {}", sampleIndex, e);
                // Continue with next pattern
                detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
            }));

        }, e -> {
            log.warn("Cache query failed for pattern: {}, falling back to LLM", pattern, e);
            // Fallback to normal detection flow on cache query failure
            String sampleIndex = sampleIndices.get(0);
            getMappingFields(sampleIndex, ActionListener.wrap(fields -> {
                getSampleDocuments(sampleIndex, ActionListener.wrap(sampleDocs -> {
                    detectPatternType(pattern, sampleIndices, fields, sampleDocs, tenantId, ActionListener.wrap(patternInfo -> {
                        result.put(pattern, patternInfo);
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }, e2 -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e2);
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }));
                }, e2 -> {
                    log.warn("Failed to get sample documents for index: {}, proceeding with empty list", sampleIndex, e2);
                    detectPatternType(pattern, sampleIndices, fields, Collections.emptyList(), tenantId, ActionListener.wrap(patternInfo -> {
                        result.put(pattern, patternInfo);
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }, e3 -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e3);
                        detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
                    }));
                }));
            }, e2 -> {
                log.error("Failed to get mapping for index: {}", sampleIndex, e2);
                detectNextPattern(patterns, patternKeys, index + 1, tenantId, result, listener);
            }));
        }));
    }

    /**
     * Get mapping fields for an index
     */
    private void getMappingFields(String indexName, ActionListener<Set<String>> listener) {
        GetMappingsRequest request = new GetMappingsRequest().indices(indexName);

        client.admin().indices().getMappings(request, ActionListener.wrap(response -> {
            try {
                MappingMetadata mappingMetadata = response.getMappings().get(indexName);
                if (mappingMetadata != null) {
                    Map<String, Object> mappingSource = mappingMetadata.getSourceAsMap();
                    Set<String> fields = extractFieldNames(mappingSource);
                    listener.onResponse(fields);
                } else {
                    listener.onResponse(Collections.emptySet());
                }
            } catch (Exception e) {
                log.error("Failed to parse mapping for index: {}", indexName, e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to get mappings for index: {}", indexName, e);
            listener.onFailure(e);
        }));
    }

    /**
     * Get sample documents from an index using match_all query
     * Returns up to 5 sample documents
     */
    private void getSampleDocuments(String indexName, ActionListener<List<Map<String, Object>>> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .size(5);

        SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

        client.search(searchRequest, ActionListener.wrap(response -> {
            try {
                List<Map<String, Object>> sampleDocs = new ArrayList<>();
                for (SearchHit hit : response.getHits().getHits()) {
                    sampleDocs.add(hit.getSourceAsMap());
                }
                listener.onResponse(sampleDocs);
            } catch (Exception e) {
                log.error("Failed to parse search response for index: {}", indexName, e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to search sample documents for index: {}", indexName, e);
            listener.onFailure(e);
        }));
    }

    /**
     * Extract field names from mapping source
     */
    private Set<String> extractFieldNames(Map<String, Object> mappingSource) {
        Set<String> fields = new HashSet<>();
        if (mappingSource.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) mappingSource.get("properties");
            extractFieldNamesRecursive(properties, "", fields);
        }
        return fields;
    }

    /**
     * Recursively extract field names
     */
    private void extractFieldNamesRecursive(Map<String, Object> properties, String prefix, Set<String> fields) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            fields.add(fieldName);

            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();
                if (fieldDef.containsKey("properties")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedProps = (Map<String, Object>) fieldDef.get("properties");
                    extractFieldNamesRecursive(nestedProps, fieldName, fields);
                }
            }
        }
    }

    /**
     * Detect pattern type using LLM
     */
    private void detectPatternType(
        String pattern,
        List<String> sampleIndices,
        Set<String> fields,
        List<Map<String, Object>> sampleDocuments,
        String tenantId,
        ActionListener<PatternInfo> listener
    ) {
        getAgentIdToRun(client, tenantId, ActionListener.wrap(agentId -> {
            String indicesStr = sampleIndices.stream().limit(5).collect(Collectors.joining(", "));
            String fieldsStr = fields.stream().limit(20).collect(Collectors.joining(", "));

            // Format sample documents as JSON string
            String sampleDocsStr;
            try {
                sampleDocsStr = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sampleDocuments);
            } catch (Exception e) {
                log.warn("Failed to serialize sample documents for pattern: {}, using empty list", pattern, e);
                sampleDocsStr = "[]";
            }

            String prompt = LLM_TYPE_DETECTION_TEMPLATE
                .replace("{pattern}", pattern)
                .replace("{sampleIndices}", indicesStr)
                .replace("{sampleFields}", fieldsStr)
                .replace("{sampleDocuments}", sampleDocsStr);

            callLLMWithAgent(client, agentId, prompt, sourceIndex, ActionListener.wrap(response -> {
                try {
                    PatternInfo patternInfo = parseTypeDetectionResponse(response, pattern, sampleIndices);
                    listener.onResponse(patternInfo);
                } catch (Exception e) {
                    log.error("Failed to parse type detection response for pattern: {}", pattern, e);
                    // Return UNKNOWN type as fallback
                    listener.onResponse(new PatternInfo(pattern, sampleIndices, "UNKNOWN", null, null, null));
                }
            }, e -> {
                log.error("Failed to call LLM for type detection: {}", pattern, e);
                listener.onFailure(e);
            }));

        }, listener::onFailure));
    }

    /**
     * Parse LLM type detection response
     */
    private PatternInfo parseTypeDetectionResponse(String response, String pattern, List<String> indices) {
        try {
            String json = response.split("<index_type_analysis>", 2)[1]
                .split("</index_type_analysis>", 2)[0]
                .trim();

            Map<String, Object> analysis = MAPPER.readValue(json, new TypeReference<>() {});

            String type = (String) analysis.get("type");
            String timeField = (String) analysis.get("time_field");
            String traceIdField = (String) analysis.get("trace_id_field");
            String spanIdField = (String) analysis.get("span_id_field");

            return new PatternInfo(pattern, indices, type, timeField, traceIdField, spanIdField);

        } catch (Exception e) {
            log.warn("Failed to parse type detection response for pattern: {}", pattern, e);
            return new PatternInfo(pattern, indices, "UNKNOWN", null, null, null);
        }
    }

    /**
     * Build correlation result
     */
    private void buildCorrelationResult(String tenantId, ActionListener<Map<String, Object>> listener) {
        Map<String, Object> result = new HashMap<>();

        // Find patterns for each type
        PatternInfo logPattern = findPatternByType("LOG");
        PatternInfo tracePattern = findPatternByType("TRACE");
        PatternInfo metricPattern = findPatternByType("METRIC");

        // Build tuple
        Map<String, Object> tuple = new HashMap<>();

        if (logPattern != null) {
            Map<String, String> logInfo = new HashMap<>();
            logInfo.put("pattern", logPattern.pattern);
            logInfo.put("sample_index", logPattern.sampleIndices.isEmpty() ? null : logPattern.sampleIndices.get(0));
            logInfo.put("time_field", logPattern.timeField);
            logInfo.put("span_id_field", logPattern.spanIdField);
            logInfo.put("trace_id_field", logPattern.traceIdField);
            tuple.put("logs", logInfo);
        } else {
            tuple.put("logs", null);
        }

        if (tracePattern != null) {
            Map<String, String> traceInfo = new HashMap<>();
            traceInfo.put("pattern", tracePattern.pattern);
            traceInfo.put("sample_index", tracePattern.sampleIndices.isEmpty() ? null : tracePattern.sampleIndices.get(0));
            traceInfo.put("time_field", tracePattern.timeField);
            traceInfo.put("trace_id_field", tracePattern.traceIdField);
            tuple.put("trace", traceInfo);
        } else {
            tuple.put("trace", null);
        }

        if (metricPattern != null) {
            Map<String, String> metricInfo = new HashMap<>();
            metricInfo.put("pattern", metricPattern.pattern);
            metricInfo.put("sample_index", metricPattern.sampleIndices.isEmpty() ? null : metricPattern.sampleIndices.get(0));
            metricInfo.put("time_field", metricPattern.timeField);
            tuple.put("metrics", metricInfo);
        } else {
            tuple.put("metrics", null);
        }

        result.put("source_index", sourceIndex);
        result.put("total_indices_scanned", allIndices.size());
        result.put("total_patterns_detected", detectedPatterns.size());
        result.put("correlation_tuple", tuple);

        // Add all detected patterns for debugging
        List<Map<String, Object>> allPatterns = new ArrayList<>();
        for (PatternInfo info : detectedPatterns.values()) {
            Map<String, Object> patternMap = new HashMap<>();
            patternMap.put("pattern", info.pattern);
            patternMap.put("type", info.type);
            patternMap.put("sample_count", info.sampleIndices.size());
            allPatterns.add(patternMap);
        }
        result.put("all_patterns", allPatterns);

        listener.onResponse(result);
    }

    /**
     * Find pattern by type
     */
    private PatternInfo findPatternByType(String type) {
        return detectedPatterns.values().stream()
            .filter(p -> type.equals(p.type))
            .findFirst()
            .orElse(null);
    }

    /**
     * Generate cache document ID for a pattern
     */
    private String generatePatternCacheDocId(String pattern) {
        String combined = pattern + "_" + MLIndexInsightType.PATTERN_TYPE_CACHE.toString();
        return Hashing.sha256().hashString(combined, StandardCharsets.UTF_8).toString();
    }

    /**
     * Query cache for a pattern
     * Returns null if cache miss or expired
     */
    private void getPatternFromCache(String pattern, String tenantId, ActionListener<PatternInfo> listener) {
        String docId = generatePatternCacheDocId(pattern);

        getIndexInsight(docId, tenantId, ActionListener.wrap(getResponse -> {
            if (!getResponse.isExists()) {
                listener.onResponse(null);
                return;
            }

            Map<String, Object> source = getResponse.getSourceAsMap();
            String status = (String) source.get(IndexInsight.STATUS_FIELD);
            Long lastUpdateTime = (Long) source.get(IndexInsight.LAST_UPDATE_FIELD);

            // Check if completed and not expired
            if (!"COMPLETED".equals(status)) {
                listener.onResponse(null);
                return;
            }

            long currentTime = Instant.now().toEpochMilli();
            if (lastUpdateTime == null || (currentTime - lastUpdateTime) > PATTERN_TYPE_CACHE_EXPIRATION) {
                listener.onResponse(null);
                return;
            }

            // Parse cached PatternInfo
            try {
                String content = (String) source.get(IndexInsight.CONTENT_FIELD);
                Map<String, Object> cachedData = MAPPER.readValue(content, new TypeReference<>() {});

                String cachedPattern = (String) cachedData.get("pattern");
                List<String> sampleIndices = (List<String>) cachedData.get("sample_indices");
                String type = (String) cachedData.get("type");
                String timeField = (String) cachedData.get("time_field");
                String traceIdField = (String) cachedData.get("trace_id_field");
                String spanIdField = (String) cachedData.get("span_id_field");

                PatternInfo patternInfo = new PatternInfo(
                    cachedPattern != null ? cachedPattern : pattern,
                    sampleIndices != null ? sampleIndices : Collections.emptyList(),
                    type,
                    timeField,
                    traceIdField,
                    spanIdField
                );

                log.info("Cache hit for pattern: {}", pattern);
                listener.onResponse(patternInfo);
            } catch (Exception e) {
                log.warn("Failed to parse cached pattern info for: {}", pattern, e);
                listener.onResponse(null);
            }
        }, e -> {
            log.warn("Failed to query cache for pattern: {}", pattern, e);
            listener.onResponse(null);
        }));
    }

    /**
     * Save pattern detection result to cache
     */
    private void savePatternToCache (PatternInfo patternInfo, String tenantId, ActionListener<Boolean> listener) {
        try {
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("pattern", patternInfo.pattern);
            cacheData.put("sample_indices", patternInfo.sampleIndices);
            cacheData.put("type", patternInfo.type);
            cacheData.put("time_field", patternInfo.timeField);
            cacheData.put("trace_id_field", patternInfo.traceIdField);
            cacheData.put("span_id_field", patternInfo.spanIdField);

            String content = MAPPER.writeValueAsString(cacheData);
            String docId = generatePatternCacheDocId(patternInfo.pattern);

            IndexInsight cacheInsight = IndexInsight.builder()
                .index(patternInfo.pattern)
                .taskType(MLIndexInsightType.PATTERN_TYPE_CACHE)
                .content(content)
                .status(IndexInsightTaskStatus.COMPLETED)
                .lastUpdatedTime(Instant.now())
                .tenantId(tenantId)
                .build();

            writeIndexInsight(cacheInsight, docId, tenantId, ActionListener.wrap(success -> {
                log.info("Saved pattern to cache: {}", patternInfo.pattern);
                listener.onResponse(success);
            }, e -> {
                log.warn("Failed to save pattern to cache: {}", patternInfo.pattern, e);
                listener.onResponse(false);
            }));
        } catch (Exception e) {
            log.warn("Failed to serialize pattern for cache: {}", patternInfo.pattern, e);
            listener.onResponse(false);
        }
    }

    // Helper classes
    private static class PatternInfo {
        final String pattern;
        final List<String> sampleIndices;
        final String type;              // LOG, TRACE, METRIC, UNKNOWN
        final String timeField;
        final String traceIdField;
        final String spanIdField;

        PatternInfo(String pattern, List<String> sampleIndices, String type, String timeField, String traceIdField, String spanIdField) {
            this.pattern = pattern;
            this.sampleIndices = sampleIndices;
            this.type = type;
            this.timeField = timeField;
            this.traceIdField = traceIdField;
            this.spanIdField = spanIdField;
        }
    }
}
