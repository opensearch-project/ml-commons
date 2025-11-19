/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MessagesSnapshotEvent extends BaseEvent {

    public static final String TYPE = "MESSAGES_SNAPSHOT";

    private List<Object> messages;

    public MessagesSnapshotEvent(List<Object> messages) {
        super(TYPE, System.currentTimeMillis(), null);
        this.messages = messages;
    }

    public MessagesSnapshotEvent(StreamInput input) throws IOException {
        super(input);
        this.messages = input.readList(StreamInput::readGenericValue);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeCollection(messages, StreamOutput::writeGenericValue);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("messages", messages);
    }
}
