/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;

/**
 * Size limits used to split a predict request into per-call sub-batches: max_items_per_request
 * (count ceiling) and max_bytes_per_request (UTF-8 byte ceiling), either of which can be -1 to disable.
 */
@Getter
public class BatchInferenceConfig implements ToXContentObject, Writeable {

    public static final String MAX_ITEMS_PER_REQUEST_FIELD = "max_items_per_request";
    public static final String MAX_BYTES_PER_REQUEST_FIELD = "max_bytes_per_request";
    public static final String QUEUE_FIELD = "queue";

    public static final int NO_LIMIT = -1;

    private final int maxItemsPerRequest;
    private final long maxBytesPerRequest;
    private final BatchQueueConfig queue;

    @Builder(toBuilder = true)
    public BatchInferenceConfig(Integer maxItemsPerRequest, Long maxBytesPerRequest, BatchQueueConfig queue) {
        this.maxItemsPerRequest = maxItemsPerRequest == null ? NO_LIMIT : maxItemsPerRequest;
        this.maxBytesPerRequest = maxBytesPerRequest == null ? NO_LIMIT : maxBytesPerRequest;
        this.queue = queue;
        validate();
    }

    public BatchInferenceConfig(StreamInput in) throws IOException {
        this.maxItemsPerRequest = in.readInt();
        this.maxBytesPerRequest = in.readLong();
        this.queue = in.readBoolean() ? new BatchQueueConfig(in) : null;
    }

    private void validate() {
        validateLimit(MAX_ITEMS_PER_REQUEST_FIELD, maxItemsPerRequest);
        validateLimit(MAX_BYTES_PER_REQUEST_FIELD, maxBytesPerRequest);
        if (!isItemLimitEnabled() && !isByteLimitEnabled()) {
            throw new IllegalArgumentException(
                "batch_inference_config must enable at least one limit, but both "
                    + MAX_ITEMS_PER_REQUEST_FIELD
                    + " and "
                    + MAX_BYTES_PER_REQUEST_FIELD
                    + " are "
                    + NO_LIMIT
                    + " (disabled). Set one of them to a positive value, or remove batch_inference_config "
                    + "to leave requests unsplit."
            );
        }
    }

    private void validateLimit(String fieldName, long value) {
        if (value < 1 && value != NO_LIMIT) {
            throw new IllegalArgumentException(fieldName + " must be a positive value or " + NO_LIMIT + " (disabled), but got " + value);
        }
    }

    public boolean isItemLimitEnabled() {
        return maxItemsPerRequest != NO_LIMIT;
    }

    public boolean isByteLimitEnabled() {
        return maxBytesPerRequest != NO_LIMIT;
    }

    public boolean isQueueEnabled() {
        return queue != null && queue.isEnabled();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeInt(maxItemsPerRequest);
        out.writeLong(maxBytesPerRequest);
        if (queue != null) {
            out.writeBoolean(true);
            queue.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MAX_ITEMS_PER_REQUEST_FIELD, maxItemsPerRequest);
        builder.field(MAX_BYTES_PER_REQUEST_FIELD, maxBytesPerRequest);
        if (queue != null) {
            builder.field(QUEUE_FIELD, queue);
        }
        builder.endObject();
        return builder;
    }

    public static BatchInferenceConfig parse(XContentParser parser) throws IOException {
        Integer maxItemsPerRequest = null;
        Long maxBytesPerRequest = null;
        BatchQueueConfig queue = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MAX_ITEMS_PER_REQUEST_FIELD:
                    maxItemsPerRequest = parser.intValue();
                    break;
                case MAX_BYTES_PER_REQUEST_FIELD:
                    maxBytesPerRequest = parser.longValue();
                    break;
                case QUEUE_FIELD:
                    queue = BatchQueueConfig.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BatchInferenceConfig(maxItemsPerRequest, maxBytesPerRequest, queue);
    }
}
