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
public class TextMessageStartEvent extends BaseEvent {

    public static final String TYPE = "TEXT_MESSAGE_START";

    private String messageId;
    private String role;

    public TextMessageStartEvent(String messageId, String role) {
        super(TYPE, System.currentTimeMillis(), null);
        this.messageId = messageId;
        this.role = role != null ? role : "assistant";
    }

    public TextMessageStartEvent(StreamInput input) throws IOException {
        super(input);
        this.messageId = input.readString();
        this.role = input.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(messageId);
        out.writeString(role);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("messageId", messageId);
        builder.field("role", role);
    }
}
