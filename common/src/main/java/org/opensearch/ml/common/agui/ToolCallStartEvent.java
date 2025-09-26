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
public class ToolCallStartEvent extends BaseEvent {

    public static final String TYPE = "TOOL_CALL_START";

    private String toolCallId;
    private String toolCallName;
    private String parentMessageId;

    public ToolCallStartEvent(String toolCallId, String toolCallName, String parentMessageId) {
        super(TYPE, System.currentTimeMillis(), null);
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
        this.parentMessageId = parentMessageId;
    }

    public ToolCallStartEvent(StreamInput input) throws IOException {
        super(input);
        this.toolCallId = input.readString();
        this.toolCallName = input.readString();
        this.parentMessageId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(toolCallId);
        out.writeString(toolCallName);
        out.writeOptionalString(parentMessageId);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("toolCallId", toolCallId);
        builder.field("toolCallName", toolCallName);
        if (parentMessageId != null) {
            builder.field("parentMessageId", parentMessageId);
        }
    }
}
