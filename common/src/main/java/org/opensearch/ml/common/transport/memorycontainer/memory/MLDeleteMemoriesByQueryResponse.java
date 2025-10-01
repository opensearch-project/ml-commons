/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response wrapper for delete memories by query action.
 * Wraps BulkByScrollResponse and implements ToXContentObject for REST response serialization.
 */
@Getter
@AllArgsConstructor
public class MLDeleteMemoriesByQueryResponse extends ActionResponse implements ToXContentObject {

    private final BulkByScrollResponse bulkResponse;

    /**
     * Constructor for deserialization
     * @param in StreamInput to read from
     * @throws IOException if deserialization fails
     */
    public MLDeleteMemoriesByQueryResponse(StreamInput in) throws IOException {
        super(in);
        this.bulkResponse = new BulkByScrollResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        bulkResponse.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        // Basic statistics
        builder.field("took", bulkResponse.getTook().millis());
        builder.field("timed_out", bulkResponse.isTimedOut());
        builder.field("deleted", bulkResponse.getDeleted());
        builder.field("batches", bulkResponse.getBatches());
        builder.field("version_conflicts", bulkResponse.getVersionConflicts());
        builder.field("noops", bulkResponse.getNoops());

        // Retries information
        builder.startObject("retries");
        builder.field("bulk", bulkResponse.getBulkRetries());
        builder.field("search", bulkResponse.getSearchRetries());
        builder.endObject();

        // Throttling information
        builder.field("throttled_millis", bulkResponse.getStatus().getThrottled().millis());
        builder.field("requests_per_second", bulkResponse.getStatus().getRequestsPerSecond());
        builder.field("throttled_until_millis", bulkResponse.getStatus().getThrottledUntil().millis());

        // Add bulk failures if any
        if (bulkResponse.getBulkFailures() != null && !bulkResponse.getBulkFailures().isEmpty()) {
            builder.startArray("bulk_failures");
            for (var failure : bulkResponse.getBulkFailures()) {
                builder.startObject();
                builder.field("index", failure.getIndex());
                builder.field("id", failure.getId());
                builder.field("cause", failure.getCause().getMessage());
                builder.field("status", failure.getStatus());
                builder.endObject();
            }
            builder.endArray();
        }

        // Add search failures if any
        if (bulkResponse.getSearchFailures() != null && !bulkResponse.getSearchFailures().isEmpty()) {
            builder.startArray("search_failures");
            for (var failure : bulkResponse.getSearchFailures()) {
                failure.toXContent(builder, params);
            }
            builder.endArray();
        }

        builder.endObject();
        return builder;
    }
}
