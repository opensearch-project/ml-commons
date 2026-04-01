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
public class TextMessageEndEvent extends BaseEvent {

    public static final String TYPE = "TEXT_MESSAGE_END";

    private String messageId;

    public TextMessageEndEvent(String messageId) {
        super(TYPE, System.currentTimeMillis(), null);
        this.messageId = messageId;
    }

    public TextMessageEndEvent(StreamInput input) throws IOException {
        super(input);
        this.messageId = input.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(messageId);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("messageId", messageId);
    }
}
