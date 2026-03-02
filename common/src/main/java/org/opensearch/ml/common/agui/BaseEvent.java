/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.io.IOException;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent implements ToXContentFragment, Writeable {

    protected String type;
    protected Long timestamp;
    protected Map<String, Object> rawEvent;

    public BaseEvent(StreamInput input) throws IOException {
        this.type = input.readString();
        this.timestamp = input.readOptionalLong();
        if (input.readBoolean()) {
            this.rawEvent = input.readMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeOptionalLong(timestamp);
        if (rawEvent != null) {
            out.writeBoolean(true);
            out.writeMap(rawEvent);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("type", type);
        if (timestamp != null) {
            builder.field("timestamp", timestamp);
        }
        if (rawEvent != null) {
            builder.field("rawEvent", rawEvent);
        }
        addEventSpecificFields(builder, params);
        builder.endObject();
        return builder;
    }

    protected abstract void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException;

    public String toJsonString() {
        try {
            return toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS).toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize event to JSON", e);
        }
    }
}
