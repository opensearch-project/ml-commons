/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.agent;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Standardized output format for V2 agents (CONVERSATIONAL_V2).
 * Follows Strands-style response format with structured fields.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.AGENT_V2)
public class AgentV2Output extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.AGENT_V2;

    public static final String STOP_REASON_FIELD = "stop_reason";
    public static final String MESSAGE_FIELD = "message";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String METRICS_FIELD = "metrics";

    /**
     * The reason the agent stopped execution.
     * Values: "end_turn", "max_iterations", "tool_use"
     */
    private String stopReason;

    /**
     * The assistant's response message with content blocks.
     */
    private Message message;

    /**
     * The memory/conversation ID for session tracking.
     */
    private String memoryId;

    /**
     * Execution metrics (token usage, latency, etc.).
     */
    @Builder.Default
    private Map<String, Object> metrics = new HashMap<>();

    @Builder
    public AgentV2Output(String stopReason, Message message, String memoryId, Map<String, Object> metrics) {
        super(OUTPUT_TYPE);
        this.stopReason = stopReason;
        this.message = message;
        this.memoryId = memoryId;
        this.metrics = metrics != null ? metrics : new HashMap<>();
    }

    public AgentV2Output(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.stopReason = in.readOptionalString();
        // Deserialize Message from JSON string
        String messageJson = in.readOptionalString();
        this.message = messageJson != null ? gson.fromJson(messageJson, Message.class) : null;
        this.memoryId = in.readOptionalString();
        this.metrics = in.readBoolean() ? in.readMap() : new HashMap<>();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(stopReason);
        // Serialize Message as JSON string
        String messageJson = message != null ? gson.toJson(message) : null;
        out.writeOptionalString(messageJson);
        out.writeOptionalString(memoryId);
        if (metrics != null && !metrics.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(metrics);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    protected MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (stopReason != null) {
            builder.field(STOP_REASON_FIELD, stopReason);
        }
        if (message != null) {
            builder.startObject(MESSAGE_FIELD);

            // Write content blocks (simplified format without "type" field)
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                builder.startArray("content");
                for (var contentBlock : message.getContent()) {
                    builder.startObject();
                    if (contentBlock.getText() != null) {
                        builder.field("text", contentBlock.getText());
                    }
                    builder.endObject();
                }
                builder.endArray();
            }

            // Write role
            if (message.getRole() != null) {
                builder.field("role", message.getRole());
            }

            builder.endObject();
        }
        if (memoryId != null) {
            builder.field(MEMORY_ID_FIELD, memoryId);
        }
        if (metrics != null && !metrics.isEmpty()) {
            builder.field(METRICS_FIELD, metrics);
        }
        builder.endObject();
        return builder;
    }
}
