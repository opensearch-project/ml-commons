/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

/**
 * Response for a retention dry-run.
 *
 * <p>Carries one or more {@link MemoryRetentionDryRunResult} objects. A single-container dry-run
 * renders as a bare object; a cluster-wide dry-run renders as a JSON array of objects. The
 * {@code clusterWide} flag drives which rendering is used so a single-element cluster-wide result
 * still renders as an array.
 */
@Getter
public class MLMemoryRetentionDryRunResponse extends ActionResponse implements ToXContentObject {

    private final List<MemoryRetentionDryRunResult> results;
    private final boolean clusterWide;
    private final int skippedCount;
    // Cluster-wide only: true when the evaluated container count hit MAX_CONTAINERS_PER_DRY_RUN and remaining
    // containers were not evaluated. 'warning' carries a human-readable explanation of the cap when truncated.
    private final boolean truncated;
    private final String warning;

    public MLMemoryRetentionDryRunResponse(List<MemoryRetentionDryRunResult> results, boolean clusterWide, int skippedCount) {
        this(results, clusterWide, skippedCount, false, null);
    }

    public MLMemoryRetentionDryRunResponse(
        List<MemoryRetentionDryRunResult> results,
        boolean clusterWide,
        int skippedCount,
        boolean truncated,
        String warning
    ) {
        this.results = results;
        this.clusterWide = clusterWide;
        this.skippedCount = skippedCount;
        this.truncated = truncated;
        this.warning = warning;
    }

    public MLMemoryRetentionDryRunResponse(StreamInput in) throws IOException {
        super(in);
        this.results = in.readList(MemoryRetentionDryRunResult::new);
        this.clusterWide = in.readBoolean();
        this.skippedCount = in.readVInt();
        // Appended for wire-compat: older senders never wrote these, but this PR introduces the response, so all
        // peers speak this layout. Read in the same order they are written.
        this.truncated = in.readBoolean();
        this.warning = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(results);
        out.writeBoolean(clusterWide);
        out.writeVInt(skippedCount);
        out.writeBoolean(truncated);
        out.writeOptionalString(warning);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (clusterWide) {
            builder.startObject();
            builder.field("skipped_count", skippedCount);
            builder.field("truncated", truncated);
            if (warning != null) {
                builder.field("warning", warning);
            }
            builder.startArray("results");
            for (MemoryRetentionDryRunResult result : results) {
                result.toXContent(builder, params);
            }
            builder.endArray();
            builder.endObject();
            return builder;
        }
        // Single-container dry-run: render the sole result as a bare object. Guard against an empty result list so a
        // container that produced no evaluation renders "{}" instead of throwing IndexOutOfBoundsException.
        if (results == null || results.isEmpty()) {
            builder.startObject();
            builder.endObject();
            return builder;
        }
        return results.get(0).toXContent(builder, params);
    }

    public static MLMemoryRetentionDryRunResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLMemoryRetentionDryRunResponse) {
            return (MLMemoryRetentionDryRunResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryRetentionDryRunResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLMemoryRetentionDryRunResponse", e);
        }
    }
}
