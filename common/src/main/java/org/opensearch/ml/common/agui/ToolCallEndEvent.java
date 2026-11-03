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
public class ToolCallEndEvent extends BaseEvent {

    public static final String TYPE = "TOOL_CALL_END";

    private String toolCallId;

    public ToolCallEndEvent(String toolCallId) {
        super(TYPE, System.currentTimeMillis(), null);
        this.toolCallId = toolCallId;
    }

    public ToolCallEndEvent(StreamInput input) throws IOException {
        super(input);
        this.toolCallId = input.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(toolCallId);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("toolCallId", toolCallId);
    }
}
