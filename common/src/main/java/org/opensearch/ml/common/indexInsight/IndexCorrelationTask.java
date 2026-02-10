/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.PATTERN_TYPE_CACHE_EXPIRATION;
import static org.opensearch.ml.common.utils.StringUtils.MAPPER;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
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

    private static final String LLM_TYPE_DETECTION_TEMPLATE = """
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
     * Strategy: First mask consecutive digits, then calculate prefix/suffix overlap with high threshold
     */
    private Map<String, List<String>> extractIndexPatterns(List<String> indices) {
        if (indices.isEmpty()) {
            return new HashMap<>();
        }

        // High threshold (80%) after masking digits - ensures very similar patterns
        final double OVERLAP_THRESHOLD = 0.85;

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
     * Mask consecutive digits (length >= 2) in index name with a placeholder
     * Single digits are preserved as they often represent versions (v1, v2, etc.)
     * This normalizes index names for similarity comparison
     *
     * Examples:
     * - "logs-otel-v1-000001" -> "logs-otel-v1-#"  (v1 preserved, 000001 masked)
     * - "jaeger-span-2025-12-19" -> "jaeger-span-#-#-#"  (all multi-digit numbers masked)
     * - "ss4o_metrics-otel-2025.12.19" -> "ss4o_metrics-otel-#.#.#"
     * - "logs-v2-2025" -> "logs-v2-#"  (v2 preserved, 2025 masked)
     */
    private String maskConsecutiveDigits(String indexName) {
        if (indexName == null) {
            return null;
        }
        // Replace consecutive digits with length >= 2 with a single '#' placeholder
        // Single digits (like v1, v2) are preserved
        return indexName.replaceAll("\\d{2,}", "#");
    }

    /**
     * Calculate name overlap between two index names
     * Strategy: First mask consecutive digits (length >= 2), then calculate prefix+suffix overlap
     * Returns a value between 0.0 and 1.0
     *
     * Examples:
     * - "logs-otel-v1-000001" vs "logs-otel-v1-000002"
     *   -> masked: "logs-otel-v1-#" vs "logs-otel-v1-#"
     *   -> overlap: 1.0 (perfect match, v1 preserved)
     *
     * - "logs-otel-2025-01" vs "logs-app-2025-01"
     *   -> masked: "logs-otel-#-01" vs "logs-app-#-01"
     *   -> overlap: ~0.65 (prefix "logs-" + suffix "-01")
     *
     * - "logs-v1-2025" vs "logs-v2-2025"
     *   -> masked: "logs-v1-#" vs "logs-v2-#"
     *   -> overlap: ~0.82 (prefix "logs-v" + suffix "-#", but v1 vs v2 differs)
     */
    private double calculateOverlap(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        // Step 1: Mask consecutive digits to normalize index names
        String masked1 = maskConsecutiveDigits(s1);
        String masked2 = maskConsecutiveDigits(s2);

        int n1 = masked1.length();
        int n2 = masked2.length();
        int minLen = Math.min(n1, n2);
        if (minLen == 0) {
            return 0.0;
        }

        // Step 2: Calculate common prefix length
        int prefix = commonPrefixLen(masked1, masked2);

        // Step 3: Calculate common suffix length (without overlapping with prefix)
        int maxSuffixAllowed = Math.max(0, minLen - prefix);
        int suffix = commonSuffixLenWithCap(masked1, masked2, maxSuffixAllowed);

        // Step 4: Return overlap ratio
        return (prefix + suffix) / (double) Math.max(n1, n2);
    }

    // Longest common prefix length
    private static int commonPrefixLen(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    /**
     * Longest common suffix length, but capped so that prefix+suffix won't overlap.
     * cap is max suffix length allowed.
     */
    private static int commonSuffixLenWithCap(String a, String b, int cap) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int matched = 0;

        while (matched < cap && i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
            matched++;
            i--;
            j--;
        }
        return matched;
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
     * Detect types for each pattern using LLM (in parallel)
     */
    private void detectPatternTypes(
        Map<String, List<String>> patterns,
        String tenantId,
        ActionListener<Map<String, PatternInfo>> listener
    ) {
        if (patterns.isEmpty()) {
            listener.onResponse(new HashMap<>());
            return;
        }

        // Use ConcurrentHashMap for thread-safe writes from parallel operations
        Map<String, PatternInfo> result = new ConcurrentHashMap<>();
        AtomicInteger pendingCount = new AtomicInteger(patterns.size());

        // Launch detection for all patterns in parallel
        for (Map.Entry<String, List<String>> entry : patterns.entrySet()) {
            String pattern = entry.getKey();
            List<String> sampleIndices = entry.getValue();

            detectSinglePattern(pattern, sampleIndices, tenantId, ActionListener.wrap(patternInfo -> {
                if (patternInfo != null) {
                    result.put(pattern, patternInfo);
                }

                // Check if all patterns are processed
                if (pendingCount.decrementAndGet() == 0) {
                    listener.onResponse(result);
                }
            }, e -> {
                log.error("Failed to detect type for pattern: {}", pattern, e);

                // Continue even if this pattern fails
                if (pendingCount.decrementAndGet() == 0) {
                    listener.onResponse(result);
                }
            }));
        }
    }

    /**
     * Detect a single pattern's type with caching (used in parallel execution)
     */
    private void detectSinglePattern(String pattern, List<String> sampleIndices, String tenantId, ActionListener<PatternInfo> listener) {

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
                listener.onResponse(updatedInfo);
                return;
            }

            // Step 2: Cache miss - get mapping, sample documents, and call LLM
            String sampleIndex = sampleIndices.get(0);
            getMappingFields(sampleIndex, ActionListener.wrap(fields -> {
                // Get sample documents
                getSampleDocuments(sampleIndex, ActionListener.wrap(sampleDocs -> {
                    // Use LLM to detect type
                    detectPatternType(pattern, sampleIndices, fields, sampleDocs, tenantId, ActionListener.wrap(patternInfo -> {
                        // Step 3: Save to cache (fire and forget)
                        savePatternToCache(
                            patternInfo,
                            tenantId,
                            ActionListener
                                .wrap(
                                    success -> {}, // Ignore success
                                    e -> log.warn("Cache save failed for pattern: {}", pattern, e)
                                )
                        );

                        listener.onResponse(patternInfo);

                    }, e -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e);
                        listener.onFailure(e);
                    }));

                }, e -> {
                    log.warn("Failed to get sample documents for index: {}, proceeding with empty list", sampleIndex, e);
                    // Proceed with empty sample documents list
                    detectPatternType(
                        pattern,
                        sampleIndices,
                        fields,
                        Collections.emptyList(),
                        tenantId,
                        ActionListener.wrap(patternInfo -> {
                            savePatternToCache(
                                patternInfo,
                                tenantId,
                                ActionListener.wrap(success -> {}, e2 -> log.warn("Cache save failed for pattern: {}", pattern, e2))
                            );
                            listener.onResponse(patternInfo);
                        }, e2 -> {
                            log.error("Failed to detect type for pattern: {}", pattern, e2);
                            listener.onFailure(e2);
                        })
                    );
                }));

            }, e -> {
                log.error("Failed to get mapping for index: {}", sampleIndex, e);
                listener.onFailure(e);
            }));

        }, e -> {
            log.warn("Cache query failed for pattern: {}, falling back to LLM", pattern, e);
            // Fallback to normal detection flow on cache query failure
            String sampleIndex = sampleIndices.get(0);
            getMappingFields(sampleIndex, ActionListener.wrap(fields -> {
                getSampleDocuments(sampleIndex, ActionListener.wrap(sampleDocs -> {
                    detectPatternType(pattern, sampleIndices, fields, sampleDocs, tenantId, ActionListener.wrap(patternInfo -> {
                        listener.onResponse(patternInfo);
                    }, e2 -> {
                        log.error("Failed to detect type for pattern: {}", pattern, e2);
                        listener.onFailure(e2);
                    }));
                }, e2 -> {
                    log.warn("Failed to get sample documents for index: {}, proceeding with empty list", sampleIndex, e2);
                    detectPatternType(
                        pattern,
                        sampleIndices,
                        fields,
                        Collections.emptyList(),
                        tenantId,
                        ActionListener.wrap(patternInfo -> {
                            listener.onResponse(patternInfo);
                        }, e3 -> {
                            log.error("Failed to detect type for pattern: {}", pattern, e3);
                            listener.onFailure(e3);
                        })
                    );
                }));
            }, e2 -> {
                log.error("Failed to get mapping for index: {}", sampleIndex, e2);
                listener.onFailure(e2);
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
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(5);

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
            String json = response.split("<index_type_analysis>", 2)[1].split("</index_type_analysis>", 2)[0].trim();

            Map<String, Object> analysis = MAPPER.readValue(json, new TypeReference<>() {
            });

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

        // Step 1: Find which pattern the source index belongs to
        PatternInfo sourcePatternInfo = findSourceIndexPattern();
        if (sourcePatternInfo == null) {
            log.error("Source index {} does not match any detected pattern", sourceIndex);
            listener.onFailure(new IllegalStateException("Source index does not match any detected pattern"));
            return;
        }

        String sourceType = sourcePatternInfo.type;
        log.info("Source index {} belongs to pattern {} of type {}", sourceIndex, sourcePatternInfo.pattern, sourceType);

        // Step 2: Determine which other two types we need to find
        String[] otherTypes = getOtherTypes(sourceType);

        // Step 3: Get patterns for the other two types
        List<PatternInfo> firstTypePatterns = findPatternsByType(otherTypes[0]);
        List<PatternInfo> secondTypePatterns = findPatternsByType(otherTypes[1]);

        // Step 4: Use LLM to select correlated patterns if multiple candidates exist
        if (firstTypePatterns.size() > 1 || secondTypePatterns.size() > 1) {
            selectCorrelatedPatternsForSource(
                sourcePatternInfo,
                otherTypes[0],
                firstTypePatterns,
                otherTypes[1],
                secondTypePatterns,
                tenantId,
                ActionListener.wrap(tuple -> {
                    result.put("source_index", sourceIndex);
                    result.put("source_pattern", sourcePatternInfo.pattern);
                    result.put("source_type", sourceType);
                    result.put("total_indices_scanned", allIndices.size());
                    result.put("total_patterns_detected", detectedPatterns.size());
                    result.put("correlation_tuple", tuple);
                    listener.onResponse(result);
                }, e -> {
                    log.warn("Failed to use LLM for pattern matching, falling back to simple selection", e);
                    // Fallback to simple selection (first pattern of each type)
                    Map<String, Object> tuple = buildCorrelationTupleFromSource(
                        sourcePatternInfo,
                        sourceType,
                        firstTypePatterns.isEmpty() ? null : firstTypePatterns.get(0),
                        otherTypes[0],
                        secondTypePatterns.isEmpty() ? null : secondTypePatterns.get(0),
                        otherTypes[1]
                    );
                    result.put("source_index", sourceIndex);
                    result.put("source_pattern", sourcePatternInfo.pattern);
                    result.put("source_type", sourceType);
                    result.put("total_indices_scanned", allIndices.size());
                    result.put("total_patterns_detected", detectedPatterns.size());
                    result.put("correlation_tuple", tuple);
                    listener.onResponse(result);
                })
            );
        } else {
            // Simple case: 0 or 1 pattern per type, no need for LLM
            Map<String, Object> tuple = buildCorrelationTupleFromSource(
                sourcePatternInfo,
                sourceType,
                firstTypePatterns.isEmpty() ? null : firstTypePatterns.get(0),
                otherTypes[0],
                secondTypePatterns.isEmpty() ? null : secondTypePatterns.get(0),
                otherTypes[1]
            );
            result.put("source_index", sourceIndex);
            result.put("source_pattern", sourcePatternInfo.pattern);
            result.put("source_type", sourceType);
            result.put("total_indices_scanned", allIndices.size());
            result.put("total_patterns_detected", detectedPatterns.size());
            result.put("correlation_tuple", tuple);
            listener.onResponse(result);
        }
    }

    /**
     * Find which pattern the source index belongs to
     */
    private PatternInfo findSourceIndexPattern() {
        for (PatternInfo info : detectedPatterns.values()) {
            if (info.sampleIndices.contains(sourceIndex)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Get the other two types given a source type
     */
    private String[] getOtherTypes(String sourceType) {
        switch (sourceType) {
            case "LOG":
                return new String[] { "TRACE", "METRIC" };
            case "TRACE":
                return new String[] { "LOG", "METRIC" };
            case "METRIC":
                return new String[] { "LOG", "TRACE" };
            default:
                return new String[] { "LOG", "TRACE" };
        }
    }

    /**
     * Build correlation tuple from source pattern and other patterns
     */
    private Map<String, Object> buildCorrelationTupleFromSource(
        PatternInfo sourcePattern,
        String sourceType,
        PatternInfo firstTypePattern,
        String firstType,
        PatternInfo secondTypePattern,
        String secondType
    ) {
        Map<String, Object> tuple = new HashMap<>();

        // Add patterns based on their types
        Map<String, PatternInfo> typeMap = new HashMap<>();
        typeMap.put(sourceType, sourcePattern);
        if (firstTypePattern != null) {
            typeMap.put(firstType, firstTypePattern);
        }
        if (secondTypePattern != null) {
            typeMap.put(secondType, secondTypePattern);
        }

        // Build LOG info
        PatternInfo logPattern = typeMap.get("LOG");
        if (logPattern != null) {
            Map<String, String> logInfo = new HashMap<>();
            logInfo.put("pattern", logPattern.pattern);
            logInfo.put("type", logPattern.type);
            logInfo.put("sample_index", logPattern.sampleIndices.isEmpty() ? null : logPattern.sampleIndices.get(0));
            logInfo.put("time_field", logPattern.timeField);
            logInfo.put("span_id_field", logPattern.spanIdField);
            logInfo.put("trace_id_field", logPattern.traceIdField);
            tuple.put("logs", logInfo);
        } else {
            tuple.put("logs", null);
        }

        // Build TRACE info
        PatternInfo tracePattern = typeMap.get("TRACE");
        if (tracePattern != null) {
            Map<String, String> traceInfo = new HashMap<>();
            traceInfo.put("pattern", tracePattern.pattern);
            traceInfo.put("type", tracePattern.type);
            traceInfo.put("sample_index", tracePattern.sampleIndices.isEmpty() ? null : tracePattern.sampleIndices.get(0));
            traceInfo.put("time_field", tracePattern.timeField);
            traceInfo.put("trace_id_field", tracePattern.traceIdField);
            tuple.put("trace", traceInfo);
        } else {
            tuple.put("trace", null);
        }

        // Build METRIC info
        PatternInfo metricPattern = typeMap.get("METRIC");
        if (metricPattern != null) {
            Map<String, String> metricInfo = new HashMap<>();
            metricInfo.put("pattern", metricPattern.pattern);
            metricInfo.put("type", metricPattern.type);
            metricInfo.put("sample_index", metricPattern.sampleIndices.isEmpty() ? null : metricPattern.sampleIndices.get(0));
            metricInfo.put("time_field", metricPattern.timeField);
            tuple.put("metrics", metricInfo);
        } else {
            tuple.put("metrics", null);
        }

        return tuple;
    }

    /**
     * Use LLM to intelligently select correlated patterns based on source pattern
     */
    private void selectCorrelatedPatternsForSource(
        PatternInfo sourcePattern,
        String firstType,
        List<PatternInfo> firstTypePatterns,
        String secondType,
        List<PatternInfo> secondTypePatterns,
        String tenantId,
        ActionListener<Map<String, Object>> listener
    ) {
        // Build LLM prompt with source pattern and candidates
        String prompt = buildCorrelationMatchingPromptForSource(
            sourcePattern,
            firstType,
            firstTypePatterns,
            secondType,
            secondTypePatterns
        );

        // Get agent ID
        getAgentIdToRun(client, tenantId, ActionListener.wrap(agentId -> {
            // Call LLM
            callLLMWithAgent(client, agentId, prompt, sourceIndex, ActionListener.wrap(llmResponse -> {
                // Parse LLM response
                parseCorrelationMatchingResponseForSource(
                    llmResponse,
                    sourcePattern,
                    sourcePattern.type,
                    firstType,
                    firstTypePatterns,
                    secondType,
                    secondTypePatterns,
                    listener
                );
            }, e -> {
                log.error("Failed to execute LLM for correlation matching", e);
                listener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to get agent ID for correlation matching", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Build LLM prompt for correlation matching based on source pattern
     */
    private String buildCorrelationMatchingPromptForSource(
        PatternInfo sourcePattern,
        String firstType,
        List<PatternInfo> firstTypePatterns,
        String secondType,
        List<PatternInfo> secondTypePatterns
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert in analyzing OpenSearch observability data.\n\n");
        prompt.append("I have a source index: ").append(sourceIndex).append("\n");
        prompt
            .append("This index belongs to pattern: ")
            .append(sourcePattern.pattern)
            .append(" (type: ")
            .append(sourcePattern.type)
            .append(")\n\n");

        prompt.append("Pattern details:\n");
        prompt.append("- Time field: ").append(sourcePattern.timeField).append("\n");
        if (sourcePattern.traceIdField != null) {
            prompt.append("- Trace ID field: ").append(sourcePattern.traceIdField).append("\n");
        }
        if (sourcePattern.spanIdField != null) {
            prompt.append("- Span ID field: ").append(sourcePattern.spanIdField).append("\n");
        }
        prompt.append("\n");

        prompt.append("I have detected multiple patterns for the other observability types.\n");
        prompt.append("Please analyze these patterns and select the ones most likely correlated to the source pattern.\n\n");

        prompt.append("Consider:\n");
        prompt.append("1. Naming conventions - patterns with similar prefixes/suffixes/keywords are likely related\n");
        prompt.append("2. Field overlap - matching trace_id/span_id field names indicate correlation\n");
        prompt.append("3. Common technology indicators (e.g., 'otel', 'jaeger', 'ss4o', 'prometheus')\n");
        prompt.append("4. The source pattern name as the primary hint for finding related patterns\n\n");

        // Add first type patterns
        if (!firstTypePatterns.isEmpty()) {
            prompt.append("Available ").append(firstType).append(" patterns (").append(firstTypePatterns.size()).append("):\n");
            for (int i = 0; i < firstTypePatterns.size(); i++) {
                PatternInfo info = firstTypePatterns.get(i);
                prompt.append(i + 1).append(". Pattern: ").append(info.pattern).append("\n");
                prompt.append("   Time field: ").append(info.timeField).append("\n");
                if (info.traceIdField != null) {
                    prompt.append("   Trace ID field: ").append(info.traceIdField).append("\n");
                }
                if (info.spanIdField != null) {
                    prompt.append("   Span ID field: ").append(info.spanIdField).append("\n");
                }
            }
            prompt.append("\n");
        }

        // Add second type patterns
        if (!secondTypePatterns.isEmpty()) {
            prompt.append("Available ").append(secondType).append(" patterns (").append(secondTypePatterns.size()).append("):\n");
            for (int i = 0; i < secondTypePatterns.size(); i++) {
                PatternInfo info = secondTypePatterns.get(i);
                prompt.append(i + 1).append(". Pattern: ").append(info.pattern).append("\n");
                prompt.append("   Time field: ").append(info.timeField).append("\n");
                if (info.traceIdField != null) {
                    prompt.append("   Trace ID field: ").append(info.traceIdField).append("\n");
                }
                if (info.spanIdField != null) {
                    prompt.append("   Span ID field: ").append(info.spanIdField).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("Please respond with your analysis in this exact XML format:\n");
        prompt.append("<correlation_selection>\n");
        prompt.append("{\n");
        prompt.append("  \"selected_").append(firstType.toLowerCase()).append("_pattern\": \"<pattern_string_or_null>\",\n");
        prompt.append("  \"selected_").append(secondType.toLowerCase()).append("_pattern\": \"<pattern_string_or_null>\",\n");
        prompt.append("  \"reasoning\": \"<brief explanation of why these patterns are correlated with the source>\"\n");
        prompt.append("}\n");
        prompt.append("</correlation_selection>\n");

        return prompt.toString();
    }

    /**
     * Parse LLM response for correlation matching based on source
     */
    private void parseCorrelationMatchingResponseForSource(
        String llmResponse,
        PatternInfo sourcePattern,
        String sourceType,
        String firstType,
        List<PatternInfo> firstTypePatterns,
        String secondType,
        List<PatternInfo> secondTypePatterns,
        ActionListener<Map<String, Object>> listener
    ) {
        try {
            // Extract JSON from XML tags
            String jsonStr = extractXmlContent(llmResponse, "correlation_selection");
            if (jsonStr == null) {
                throw new IllegalArgumentException("No <correlation_selection> found in LLM response");
            }

            // Parse JSON
            Map<String, Object> selection = MAPPER.readValue(jsonStr, new TypeReference<>() {
            });

            String selectedFirstPattern = (String) selection.get("selected_" + firstType.toLowerCase() + "_pattern");
            String selectedSecondPattern = (String) selection.get("selected_" + secondType.toLowerCase() + "_pattern");
            String reasoning = (String) selection.get("reasoning");

            log.info("LLM correlation matching reasoning: {}", reasoning);

            // Find matching PatternInfo objects
            PatternInfo firstPattern = findPatternByPatternString(firstTypePatterns, selectedFirstPattern);
            PatternInfo secondPattern = findPatternByPatternString(secondTypePatterns, selectedSecondPattern);

            // Build result tuple
            Map<String, Object> tuple = buildCorrelationTupleFromSource(
                sourcePattern,
                sourceType,
                firstPattern,
                firstType,
                secondPattern,
                secondType
            );

            // Add reasoning to tuple
            tuple.put("llm_reasoning", reasoning);

            listener.onResponse(tuple);

        } catch (Exception e) {
            log.error("Failed to parse correlation matching response", e);
            listener.onFailure(e);
        }
    }

    /**
     * Extract content from XML tags
     * E.g., extract "content" from "<tag>content</tag>"
     */
    private String extractXmlContent(String text, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";

        int startIdx = text.indexOf(openTag);
        int endIdx = text.indexOf(closeTag);

        if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) {
            return null;
        }

        return text.substring(startIdx + openTag.length(), endIdx).trim();
    }

    /**
     * Find PatternInfo by pattern string
     */
    private PatternInfo findPatternByPatternString(List<PatternInfo> patterns, String patternStr) {
        if (patternStr == null || "null".equals(patternStr)) {
            return null;
        }
        return patterns.stream().filter(p -> p.pattern.equals(patternStr)).findFirst().orElse(null);
    }

    /**
     * Find all patterns by type (used for intelligent matching)
     */
    private List<PatternInfo> findPatternsByType(String type) {
        return detectedPatterns.values().stream().filter(p -> type.equals(p.type)).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Find pattern by type (returns first match)
     */
    private PatternInfo findPatternByType(String type) {
        return detectedPatterns.values().stream().filter(p -> type.equals(p.type)).findFirst().orElse(null);
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
                Map<String, Object> cachedData = MAPPER.readValue(content, new TypeReference<>() {
                });

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
    private void savePatternToCache(PatternInfo patternInfo, String tenantId, ActionListener<Boolean> listener) {
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

            IndexInsight cacheInsight = IndexInsight
                .builder()
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
