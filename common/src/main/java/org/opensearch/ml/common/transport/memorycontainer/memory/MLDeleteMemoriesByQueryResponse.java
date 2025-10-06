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
        bulkResponse.toXContent(builder, params);
        builder.endObject();
        return builder;
    }
}
