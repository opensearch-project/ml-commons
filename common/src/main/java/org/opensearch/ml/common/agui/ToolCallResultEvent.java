/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolCallResultEvent extends BaseEvent {

    public static final String TYPE = "TOOL_CALL_RESULT";

    private String messageId;
    private String toolCallId;
    private String content;
    private String role;

    public ToolCallResultEvent(String messageId, String toolCallId, String content) {
        super(TYPE, System.currentTimeMillis(), null);
        this.messageId = messageId;
        this.toolCallId = toolCallId;
        this.content = content;
        this.role = "tool";
    }

    public ToolCallResultEvent(StreamInput input) throws IOException {
        super(input);
        this.messageId = input.readString();
        this.toolCallId = input.readString();
        this.content = input.readString();
        this.role = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(messageId);
        out.writeString(toolCallId);
        out.writeString(content);
        out.writeOptionalString(role);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("messageId", messageId);
        builder.field("toolCallId", toolCallId);
        builder.field("content", content);
        if (role != null) {
            builder.field("role", role);
        }
    }
}
