/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.RetentionRule;

public class MemoryRetentionDryRunResultTests {

    private MemoryRetentionDryRunResult sampleResult() {
        Map<MemoryType, RetentionRule> policy = new EnumMap<>(MemoryType.class);
        policy.put(MemoryType.SESSIONS, new RetentionRule(30, 500));
        policy.put(MemoryType.LONG_TERM, new RetentionRule(180, 10000));
        policy.put(MemoryType.HISTORY, new RetentionRule(null, 5000));

        LinkedHashMap<String, Long> sessionsBr = new LinkedHashMap<>();
        sessionsBr.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, 35L);
        sessionsBr.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, 12L);

        LinkedHashMap<String, Long> ltBr = new LinkedHashMap<>();
        ltBr.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, 5L);
        ltBr.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, 0L);

        LinkedHashMap<String, Long> histBr = new LinkedHashMap<>();
        histBr.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, 0L);
        histBr.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, 8L);

        LinkedHashMap<String, Long> workBr = new LinkedHashMap<>();
        workBr.put(MemoryRetentionDryRunResult.REASON_CASCADE, 1380L);
        workBr.put(MemoryRetentionDryRunResult.REASON_TTL, 34L);
        workBr.put(MemoryRetentionDryRunResult.REASON_ORPHAN, 6L);

        return MemoryRetentionDryRunResult
            .builder()
            .containerId("agent-customer-support-abc123")
            .evaluatedAt(1752634800000L)
            .policySource(MemoryRetentionDryRunResult.POLICY_SOURCE_STORED)
            .effectivePolicy(policy)
            .workingMemoryTtlDays(7)
            .sessions(new MemoryRetentionDryRunResult.TypeDeletion(47, sessionsBr))
            .longTerm(new MemoryRetentionDryRunResult.TypeDeletion(5, ltBr))
            .history(new MemoryRetentionDryRunResult.TypeDeletion(8, histBr))
            .workingMemory(new MemoryRetentionDryRunResult.TypeDeletion(1420, workBr))
            .totalWouldDelete(1480)
            .pinnedWouldSkip(3)
            .warnings(List.of("pinned count (503) exceeds max_count (500) for sessions — container will grow unbounded"))
            .build();
    }

    private String toJson(ToXContent obj) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        obj.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return builder.toString();
    }

    @Test
    public void testXContentShapeMatchesContract() throws IOException {
        String json = toJson(sampleResult());

        assertTrue(json.contains("\"container_id\":\"agent-customer-support-abc123\""));
        assertTrue(json.contains("\"evaluated_at\":1752634800000"));
        assertTrue(json.contains("\"policy_source\":\"stored\""));
        // effective_policy
        assertTrue(json.contains("\"effective_policy\":{"));
        assertTrue(json.contains("\"sessions\":{\"retention_days\":30,\"max_count\":500}"));
        assertTrue(json.contains("\"long_term\":{\"retention_days\":180,\"max_count\":10000}"));
        assertTrue(json.contains("\"history\":{\"max_count\":5000}"));
        assertTrue(json.contains("\"working_memory_ttl_days\":7"));
        // would_delete
        assertTrue(json.contains("\"would_delete\":{"));
        assertTrue(json.contains("\"sessions\":{\"total\":47,\"by_reason\":{\"retention_days\":35,\"max_count\":12}}"));
        assertTrue(json.contains("\"long_term\":{\"total\":5,\"by_reason\":{\"retention_days\":5,\"max_count\":0}}"));
        assertTrue(json.contains("\"history\":{\"total\":8,\"by_reason\":{\"retention_days\":0,\"max_count\":8}}"));
        assertTrue(json.contains("\"working_memory\":{\"total\":1420,\"by_reason\":{\"cascade\":1380,\"ttl\":34,\"orphan\":6}}"));
        assertTrue(json.contains("\"total_would_delete\":1480"));
        assertTrue(json.contains("\"pinned_would_skip\":3"));
        assertTrue(json.contains("\"warnings\":[\"pinned count (503) exceeds max_count (500) for sessions"));
    }

    @Test
    public void testStreamRoundTrip() throws IOException {
        MemoryRetentionDryRunResult original = sampleResult();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryRetentionDryRunResult parsed = new MemoryRetentionDryRunResult(in);

        assertEquals("agent-customer-support-abc123", parsed.getContainerId());
        assertEquals(1752634800000L, parsed.getEvaluatedAt());
        assertEquals("stored", parsed.getPolicySource());
        assertEquals(Integer.valueOf(7), parsed.getWorkingMemoryTtlDays());
        assertEquals(47, parsed.getSessions().getTotal());
        assertEquals(Long.valueOf(35L), parsed.getSessions().getByReason().get("retention_days"));
        assertEquals(Long.valueOf(12L), parsed.getSessions().getByReason().get("max_count"));
        assertEquals(1420, parsed.getWorkingMemory().getTotal());
        assertEquals(Long.valueOf(6L), parsed.getWorkingMemory().getByReason().get("orphan"));
        assertEquals(1480, parsed.getTotalWouldDelete());
        assertEquals(3, parsed.getPinnedWouldSkip());
        assertEquals(1, parsed.getWarnings().size());
        assertEquals(3, parsed.getEffectivePolicy().size());
        assertEquals(Integer.valueOf(500), parsed.getEffectivePolicy().get(MemoryType.SESSIONS).getMaxCount());
        assertEquals(Integer.valueOf(5000), parsed.getEffectivePolicy().get(MemoryType.HISTORY).getMaxCount());
    }

    @Test
    public void testNonePolicyRendersEmptyEffectivePolicyAndNoTtl() throws IOException {
        MemoryRetentionDryRunResult result = MemoryRetentionDryRunResult
            .builder()
            .containerId("c-none")
            .evaluatedAt(1000L)
            .policySource(MemoryRetentionDryRunResult.POLICY_SOURCE_NONE)
            .effectivePolicy(null)
            .workingMemoryTtlDays(-1)
            .sessions(new MemoryRetentionDryRunResult.TypeDeletion(0, new LinkedHashMap<>()))
            .longTerm(new MemoryRetentionDryRunResult.TypeDeletion(0, new LinkedHashMap<>()))
            .history(new MemoryRetentionDryRunResult.TypeDeletion(0, new LinkedHashMap<>()))
            .workingMemory(new MemoryRetentionDryRunResult.TypeDeletion(0, new LinkedHashMap<>()))
            .totalWouldDelete(0)
            .pinnedWouldSkip(0)
            .warnings(List.of("container has no retention policy and no cluster defaults apply; nothing would be deleted"))
            .build();

        String json = toJson(result);
        assertTrue(json.contains("\"policy_source\":\"none\""));
        assertTrue(json.contains("\"effective_policy\":{}"));
        // ttl <= 0 is not rendered
        assertTrue(!json.contains("working_memory_ttl_days"));
        assertTrue(json.contains("\"total_would_delete\":0"));
    }

    @Test
    public void testResponseSingleRendersObject() throws IOException {
        MLMemoryRetentionDryRunResponse response = new MLMemoryRetentionDryRunResponse(List.of(sampleResult()), false, 0);
        String json = toJson(response);
        // Single container: bare object, not an array
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"container_id\":\"agent-customer-support-abc123\""));
        // Single-container branch does not surface skipped_count
        assertTrue(!json.contains("skipped_count"));
    }

    @Test
    public void testResponseClusterWideRendersArray() throws IOException {
        MLMemoryRetentionDryRunResponse response = new MLMemoryRetentionDryRunResponse(List.of(sampleResult()), true, 2);
        String json = toJson(response);
        // Cluster-wide: object wrapping a skipped_count and a results array (even with a single element)
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"skipped_count\":2"));
        assertTrue(json.contains("\"results\":["));
    }

    @Test
    public void testResponseStreamRoundTrip() throws IOException {
        MLMemoryRetentionDryRunResponse original = new MLMemoryRetentionDryRunResponse(List.of(sampleResult(), sampleResult()), true, 3);
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemoryRetentionDryRunResponse parsed = new MLMemoryRetentionDryRunResponse(in);
        assertEquals(2, parsed.getResults().size());
        assertTrue(parsed.isClusterWide());
        assertEquals(3, parsed.getSkippedCount());
        assertEquals("agent-customer-support-abc123", parsed.getResults().get(0).getContainerId());
    }

    @Test
    public void testResponseFromActionResponseSame() {
        MLMemoryRetentionDryRunResponse response = new MLMemoryRetentionDryRunResponse(List.of(sampleResult()), false, 0);
        assertEquals(response, MLMemoryRetentionDryRunResponse.fromActionResponse(response));
    }

    @Test
    public void testXContentTypeParsableJson() throws IOException {
        // Ensure the emitted document is well-formed JSON (parseable end to end).
        String json = toJson(sampleResult());
        Map<String, Object> parsed = XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            )
            .map();
        assertEquals("agent-customer-support-abc123", parsed.get("container_id"));
        assertEquals(1480, ((Number) parsed.get("total_would_delete")).intValue());
    }
}
