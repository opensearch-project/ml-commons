/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.RetentionRule;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable result of a single container's retention dry-run: exactly what the scheduled
 * {@code MemoryRetentionJobProcessor} would delete on its next run, without deleting anything.
 *
 * <p>The counts here are produced by reusing the job's own read-only {@code identify*} / count
 * logic (see the dry-run methods on the processor), so they cannot drift from the real job's
 * deletion behavior.
 */
@Getter
public class MemoryRetentionDryRunResult implements ToXContentObject, Writeable {

    // Top-level response field names (kept local to this contract).
    public static final String CONTAINER_ID_FIELD = "container_id";
    public static final String EVALUATED_AT_FIELD = "evaluated_at";
    public static final String POLICY_SOURCE_FIELD = "policy_source";
    public static final String EFFECTIVE_POLICY_FIELD = "effective_policy";
    public static final String WORKING_MEMORY_TTL_DAYS_FIELD = "working_memory_ttl_days";
    public static final String WOULD_DELETE_FIELD = "would_delete";
    public static final String TOTAL_WOULD_DELETE_FIELD = "total_would_delete";
    public static final String PINNED_WOULD_SKIP_FIELD = "pinned_would_skip";
    public static final String WARNINGS_FIELD = "warnings";

    // would_delete sub-keys
    public static final String SESSIONS_KEY = "sessions";
    public static final String LONG_TERM_KEY = "long_term";
    public static final String HISTORY_KEY = "history";
    public static final String WORKING_MEMORY_KEY = "working_memory";

    // TypeDeletion sub-keys
    public static final String TOTAL_KEY = "total";
    public static final String BY_REASON_KEY = "by_reason";

    // by_reason reason keys
    public static final String REASON_RETENTION_DAYS = "retention_days";
    public static final String REASON_MAX_COUNT = "max_count";
    public static final String REASON_CASCADE = "cascade";
    public static final String REASON_TTL = "ttl";
    public static final String REASON_ORPHAN = "orphan";

    /** Valid values for {@link #policySource}. */
    public static final String POLICY_SOURCE_STORED = "stored";
    public static final String POLICY_SOURCE_DEFAULT = "default";
    public static final String POLICY_SOURCE_NONE = "none";

    private final String containerId;
    private final long evaluatedAt;
    private final String policySource;
    /** The retention rules that would be applied (stored, backfilled default, or empty for none). */
    private final Map<MemoryType, RetentionRule> effectivePolicy;
    /** Cluster working-memory TTL in days; only rendered when {@code > 0} (session-less containers). */
    private final Integer workingMemoryTtlDays;
    private final TypeDeletion sessions;
    private final TypeDeletion longTerm;
    private final TypeDeletion history;
    private final TypeDeletion workingMemory;
    private final long totalWouldDelete;
    private final long pinnedWouldSkip;
    private final List<String> warnings;

    @Builder
    public MemoryRetentionDryRunResult(
        String containerId,
        long evaluatedAt,
        String policySource,
        Map<MemoryType, RetentionRule> effectivePolicy,
        Integer workingMemoryTtlDays,
        TypeDeletion sessions,
        TypeDeletion longTerm,
        TypeDeletion history,
        TypeDeletion workingMemory,
        long totalWouldDelete,
        long pinnedWouldSkip,
        List<String> warnings
    ) {
        this.containerId = containerId;
        this.evaluatedAt = evaluatedAt;
        this.policySource = policySource;
        this.effectivePolicy = effectivePolicy;
        this.workingMemoryTtlDays = workingMemoryTtlDays;
        this.sessions = sessions;
        this.longTerm = longTerm;
        this.history = history;
        this.workingMemory = workingMemory;
        this.totalWouldDelete = totalWouldDelete;
        this.pinnedWouldSkip = pinnedWouldSkip;
        this.warnings = warnings;
    }

    public MemoryRetentionDryRunResult(StreamInput in) throws IOException {
        this.containerId = in.readString();
        this.evaluatedAt = in.readLong();
        this.policySource = in.readString();
        if (in.readBoolean()) {
            int size = in.readVInt();
            Map<MemoryType, RetentionRule> policy = new EnumMap<>(MemoryType.class);
            for (int i = 0; i < size; i++) {
                MemoryType key = in.readEnum(MemoryType.class);
                policy.put(key, new RetentionRule(in));
            }
            this.effectivePolicy = policy;
        } else {
            this.effectivePolicy = null;
        }
        this.workingMemoryTtlDays = in.readOptionalInt();
        this.sessions = new TypeDeletion(in);
        this.longTerm = new TypeDeletion(in);
        this.history = new TypeDeletion(in);
        this.workingMemory = new TypeDeletion(in);
        this.totalWouldDelete = in.readLong();
        this.pinnedWouldSkip = in.readLong();
        this.warnings = in.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(containerId);
        out.writeLong(evaluatedAt);
        out.writeString(policySource);
        if (effectivePolicy != null && !effectivePolicy.isEmpty()) {
            out.writeBoolean(true);
            out.writeVInt(effectivePolicy.size());
            for (Map.Entry<MemoryType, RetentionRule> entry : effectivePolicy.entrySet()) {
                out.writeEnum(entry.getKey());
                entry.getValue().writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInt(workingMemoryTtlDays);
        sessions.writeTo(out);
        longTerm.writeTo(out);
        history.writeTo(out);
        workingMemory.writeTo(out);
        out.writeLong(totalWouldDelete);
        out.writeLong(pinnedWouldSkip);
        out.writeStringCollection(warnings == null ? new ArrayList<>() : warnings);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(CONTAINER_ID_FIELD, containerId);
        builder.field(EVALUATED_AT_FIELD, evaluatedAt);
        builder.field(POLICY_SOURCE_FIELD, policySource);

        builder.startObject(EFFECTIVE_POLICY_FIELD);
        if (effectivePolicy != null) {
            if (effectivePolicy.containsKey(MemoryType.SESSIONS)) {
                builder.field(SESSIONS_KEY);
                effectivePolicy.get(MemoryType.SESSIONS).toXContent(builder, params);
            }
            if (effectivePolicy.containsKey(MemoryType.LONG_TERM)) {
                builder.field(LONG_TERM_KEY);
                effectivePolicy.get(MemoryType.LONG_TERM).toXContent(builder, params);
            }
            if (effectivePolicy.containsKey(MemoryType.HISTORY)) {
                builder.field(HISTORY_KEY);
                effectivePolicy.get(MemoryType.HISTORY).toXContent(builder, params);
            }
        }
        if (workingMemoryTtlDays != null && workingMemoryTtlDays > 0) {
            builder.field(WORKING_MEMORY_TTL_DAYS_FIELD, workingMemoryTtlDays);
        }
        builder.endObject();

        builder.startObject(WOULD_DELETE_FIELD);
        builder.field(SESSIONS_KEY);
        sessions.toXContent(builder, params);
        builder.field(LONG_TERM_KEY);
        longTerm.toXContent(builder, params);
        builder.field(HISTORY_KEY);
        history.toXContent(builder, params);
        builder.field(WORKING_MEMORY_KEY);
        workingMemory.toXContent(builder, params);
        builder.endObject();

        builder.field(TOTAL_WOULD_DELETE_FIELD, totalWouldDelete);
        builder.field(PINNED_WOULD_SKIP_FIELD, pinnedWouldSkip);
        builder.field(WARNINGS_FIELD, warnings == null ? new ArrayList<>() : warnings);
        builder.endObject();
        return builder;
    }

    /**
     * Per-memory-type deletion count with a breakdown by the reason the job would evict each document.
     * {@code total} is the number the job would actually delete (deduplicated where the job dedups);
     * the {@code byReason} components are a disjoint attribution that sums to {@code total}.
     */
    @Getter
    public static class TypeDeletion implements ToXContentObject, Writeable {
        private final long total;
        private final LinkedHashMap<String, Long> byReason;

        public TypeDeletion(long total, LinkedHashMap<String, Long> byReason) {
            this.total = total;
            this.byReason = byReason == null ? new LinkedHashMap<>() : byReason;
        }

        public TypeDeletion(StreamInput in) throws IOException {
            this.total = in.readLong();
            int size = in.readVInt();
            this.byReason = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                this.byReason.put(in.readString(), in.readLong());
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeLong(total);
            out.writeVInt(byReason.size());
            for (Map.Entry<String, Long> entry : byReason.entrySet()) {
                out.writeString(entry.getKey());
                out.writeLong(entry.getValue());
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
            builder.startObject();
            builder.field(TOTAL_KEY, total);
            builder.startObject(BY_REASON_KEY);
            for (Map.Entry<String, Long> entry : byReason.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
            builder.endObject();
            return builder;
        }
    }
}
