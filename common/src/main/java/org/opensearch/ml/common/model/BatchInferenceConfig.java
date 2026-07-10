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
 * (count ceiling) and max_bytes_per_request (UTF-8 byte ceiling, -1 to disable).
 */
@Getter
public class BatchInferenceConfig implements ToXContentObject, Writeable {

    public static final String MAX_ITEMS_PER_REQUEST_FIELD = "max_items_per_request";
    public static final String MAX_BYTES_PER_REQUEST_FIELD = "max_bytes_per_request";

    // Default when the field is omitted; callers should set their model's documented batch limit.
    public static final int DEFAULT_MAX_ITEMS_PER_REQUEST = 512;
    public static final long DISABLED_MAX_BYTES_PER_REQUEST = -1L;

    private final int maxItemsPerRequest;
    private final long maxBytesPerRequest;

    @Builder(toBuilder = true)
    public BatchInferenceConfig(Integer maxItemsPerRequest, Long maxBytesPerRequest) {
        this.maxItemsPerRequest = maxItemsPerRequest == null ? DEFAULT_MAX_ITEMS_PER_REQUEST : maxItemsPerRequest;
        this.maxBytesPerRequest = maxBytesPerRequest == null ? DISABLED_MAX_BYTES_PER_REQUEST : maxBytesPerRequest;
        validate();
    }

    public BatchInferenceConfig(StreamInput in) throws IOException {
        this.maxItemsPerRequest = in.readInt();
        this.maxBytesPerRequest = in.readLong();
    }

    private void validate() {
        if (maxItemsPerRequest < 1) {
            throw new IllegalArgumentException(MAX_ITEMS_PER_REQUEST_FIELD + " must be a positive integer, but got " + maxItemsPerRequest);
        }
        if (maxBytesPerRequest < 1 && maxBytesPerRequest != DISABLED_MAX_BYTES_PER_REQUEST) {
            throw new IllegalArgumentException(
                MAX_BYTES_PER_REQUEST_FIELD
                    + " must be a positive value or "
                    + DISABLED_MAX_BYTES_PER_REQUEST
                    + " (disabled), but got "
                    + maxBytesPerRequest
            );
        }
    }

    public boolean isByteLimitEnabled() {
        return maxBytesPerRequest != DISABLED_MAX_BYTES_PER_REQUEST;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeInt(maxItemsPerRequest);
        out.writeLong(maxBytesPerRequest);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MAX_ITEMS_PER_REQUEST_FIELD, maxItemsPerRequest);
        builder.field(MAX_BYTES_PER_REQUEST_FIELD, maxBytesPerRequest);
        builder.endObject();
        return builder;
    }

    public static BatchInferenceConfig parse(XContentParser parser) throws IOException {
        Integer maxItemsPerRequest = null;
        Long maxBytesPerRequest = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BatchInferenceConfig(maxItemsPerRequest, maxBytesPerRequest);
    }
}
