/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ML Agent V2 Output in Strands message format.
 *
 * This output format provides a clean, structured response for V2 conversational agents:
 * {
 *   "stop_reason": "end_turn",
 *   "message": {
 *     "role": "assistant",
 *     "content": [
 *       {"text": "The response text"}
 *     ]
 *   }
 * }
 */
@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.AGENT_V2)
public class MLAgentV2Output extends MLOutput {
    private static final MLOutputType OUTPUT_TYPE = MLOutputType.AGENT_V2;
    public static final String STOP_REASON_FIELD = "stop_reason";
    public static final String MESSAGE_FIELD = "message";
    public static final String ROLE_FIELD = "role";
    public static final String CONTENT_FIELD = "content";
    public static final String METRICS_FIELD = "metrics";

    private String stopReason;
    private Map<String, Object> message;
    private Map<String, Object> metrics;

    @Builder(toBuilder = true)
    public MLAgentV2Output(String stopReason, Map<String, Object> message, Map<String, Object> metrics) {
        super(OUTPUT_TYPE);
        this.stopReason = stopReason;
        this.message = message;
        this.metrics = metrics;
    }

    public MLAgentV2Output(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.stopReason = in.readOptionalString();
        if (in.readBoolean()) {
            this.message = in.readMap();
        }
        if (in.readBoolean()) {
            this.metrics = in.readMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(stopReason);
        if (message != null) {
            out.writeBoolean(true);
            out.writeMap(message);
        } else {
            out.writeBoolean(false);
        }
        if (metrics != null) {
            out.writeBoolean(true);
            out.writeMap(metrics);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (stopReason != null) {
            builder.field(STOP_REASON_FIELD, stopReason);
        }
        if (message != null) {
            builder.field(MESSAGE_FIELD, message);
        }
        if (metrics != null) {
            builder.field(METRICS_FIELD, metrics);
        }
        builder.endObject();
        return builder;
    }

    @Override
    protected MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    public static MLAgentV2Output parse(XContentParser parser) throws IOException {
        String stopReason = null;
        Map<String, Object> message = null;
        Map<String, Object> metrics = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case STOP_REASON_FIELD:
                    stopReason = parser.textOrNull();
                    break;
                case MESSAGE_FIELD:
                    message = parser.map();
                    break;
                case METRICS_FIELD:
                    metrics = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLAgentV2Output.builder().stopReason(stopReason).message(message).metrics(metrics).build();
    }

    /**
     * Create MLAgentV2Output from a Map response
     * @param response Map containing "stop_reason", "message", and optional "metrics" fields
     * @return MLAgentV2Output instance
     */
    public static MLAgentV2Output fromMap(Map<String, Object> response) {
        String stopReason = (String) response.get(STOP_REASON_FIELD);
        Map<String, Object> message = (Map<String, Object>) response.get(MESSAGE_FIELD);
        Map<String, Object> metrics = (Map<String, Object>) response.get(METRICS_FIELD);
        return MLAgentV2Output.builder().stopReason(stopReason).message(message).metrics(metrics).build();
    }
}
