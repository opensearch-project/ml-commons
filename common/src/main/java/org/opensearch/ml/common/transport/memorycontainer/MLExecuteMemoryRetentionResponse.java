/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import lombok.ToString;

/**
 * Response for an on-demand memory retention trigger.
 *
 * <p>The trigger is an acknowledgement: it reports whether the retention pipeline was started
 * ({@code triggered}) or why it was not, without blocking until the (fully async) delete pipeline
 * finishes. Every job-level outcome returns HTTP 200; the {@code status} and {@code triggered}
 * fields in the body communicate what happened so a benign "already running" or "disabled" state
 * is never surfaced as an error the caller must special-case.
 */
@Getter
@ToString
public class MLExecuteMemoryRetentionResponse extends ActionResponse implements ToXContentObject {

    /**
     * Job-level outcome of an on-demand trigger. Mirrors the guards inside
     * MemoryRetentionJobProcessor#triggerRun() one-to-one so the API surfaces exactly what the
     * scheduled run would have done.
     */
    // Wire format note: writeEnum/readEnum serialize by ordinal. Constants are APPEND-ONLY —
    // never reorder or remove existing values, or the stream format breaks across node versions.
    public enum TriggerStatus {
        /** Pipeline started on this invocation. */
        TRIGGERED("triggered", true),
        /** A scheduled or previous on-demand run is still in progress; not double-run. */
        ALREADY_RUNNING("already_running", false),
        /** plugins.ml_commons.memory.retention_enabled=false. */
        RETENTION_DISABLED("retention_disabled", false),
        /**
         * Multi-tenancy is enabled; the native retention job cannot route by tenant.
         *
         * @deprecated RFC #4859: multi-tenancy no longer gates the retention job (local-metadata multi-tenancy runs
         *             tenant-isolated via per-container memory_container_id filters). The job now skips only when a
         *             REMOTE metadata store is configured; see {@link #REMOTE_METADATA_STORE}. This constant is
         *             retained (never emitted by current code) purely for wire-compatibility: writeEnum/readEnum
         *             serialize by ordinal, so it must not be removed or reordered.
         */
        @Deprecated
        MULTI_TENANCY_ENABLED("multi_tenancy_enabled", false),
        /**
         * A remote metadata store (e.g. AWS OpenSearch Serverless / DynamoDB) is configured; the container registry
         * lives outside the local cluster, so the native-client retention job cannot enumerate containers. See RFC #4859.
         */
        REMOTE_METADATA_STORE("remote_metadata_store", false);

        private final String value;
        private final boolean triggered;

        TriggerStatus(String value, boolean triggered) {
            this.value = value;
            this.triggered = triggered;
        }

        public String getValue() {
            return value;
        }

        public boolean isTriggered() {
            return triggered;
        }
    }

    private final TriggerStatus status;
    private final String message;

    public MLExecuteMemoryRetentionResponse(TriggerStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public MLExecuteMemoryRetentionResponse(StreamInput in) throws IOException {
        super(in);
        this.status = in.readEnum(TriggerStatus.class);
        this.message = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(status);
        out.writeOptionalString(message);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("acknowledged", true);
        builder.field("triggered", status.isTriggered());
        builder.field("status", status.getValue());
        if (message != null) {
            builder.field("message", message);
        }
        builder.endObject();
        return builder;
    }
}
