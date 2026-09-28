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
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Settings for the dynamic_batching block within batch_inference_config: whether to coalesce concurrent
 * predict requests to a model into shared calls, and how long to wait before flushing (flush_timeout_ms).
 * Disabled by default. Enable only for a model whose callers send homogeneous requests (same input type
 * and parameters) and whose output is one tensor per input, since coalesced requests share one model
 * call and a failed call fails every caller whose items were in it.
 */
@Getter
@EqualsAndHashCode
public class DynamicBatchingConfig implements ToXContentObject, Writeable {

    public static final String ENABLED_FIELD = "enabled";
    public static final String FLUSH_TIMEOUT_MS_FIELD = "flush_timeout_ms";

    public static final long DEFAULT_FLUSH_TIMEOUT_MS = 50L;
    public static final long MAX_FLUSH_TIMEOUT_MS = 10_000L;

    private final boolean enabled;
    private final long flushTimeoutMs;

    @Builder(toBuilder = true)
    public DynamicBatchingConfig(Boolean enabled, Long flushTimeoutMs) {
        this.enabled = enabled != null && enabled;
        this.flushTimeoutMs = flushTimeoutMs == null ? DEFAULT_FLUSH_TIMEOUT_MS : flushTimeoutMs;
        validate();
    }

    public DynamicBatchingConfig(StreamInput in) throws IOException {
        this.enabled = in.readBoolean();
        this.flushTimeoutMs = in.readLong();
    }

    private void validate() {
        if (flushTimeoutMs < 1 || flushTimeoutMs > MAX_FLUSH_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                FLUSH_TIMEOUT_MS_FIELD + " must be between 1 and " + MAX_FLUSH_TIMEOUT_MS + " milliseconds, but got " + flushTimeoutMs
            );
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(enabled);
        out.writeLong(flushTimeoutMs);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ENABLED_FIELD, enabled);
        builder.field(FLUSH_TIMEOUT_MS_FIELD, flushTimeoutMs);
        builder.endObject();
        return builder;
    }

    /** Lenient: skips unknown fields, so a stored model written by a newer version stays readable. */
    public static DynamicBatchingConfig parse(XContentParser parser) throws IOException {
        return parse(parser, false);
    }

    /** See {@link BatchInferenceConfig#parse(XContentParser, boolean)} for when rejectUnknownFields applies. */
    public static DynamicBatchingConfig parse(XContentParser parser, boolean rejectUnknownFields) throws IOException {
        Boolean enabled = null;
        Long flushTimeoutMs = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ENABLED_FIELD:
                    enabled = parser.booleanValue();
                    break;
                case FLUSH_TIMEOUT_MS_FIELD:
                    flushTimeoutMs = parser.longValue();
                    break;
                default:
                    if (rejectUnknownFields) {
                        // flush_timeout instead of flush_timeout_ms would otherwise be accepted silently and leave
                        // every request waiting the default timeout.
                        throw new IllegalArgumentException(
                            "Unsupported field ["
                                + fieldName
                                + "] in the batch_inference_config "
                                + BatchInferenceConfig.DYNAMIC_BATCHING_FIELD
                                + " block. Supported fields are ["
                                + ENABLED_FIELD
                                + ", "
                                + FLUSH_TIMEOUT_MS_FIELD
                                + "]."
                        );
                    }
                    parser.skipChildren();
                    break;
            }
        }
        return new DynamicBatchingConfig(enabled, flushTimeoutMs);
    }
}
