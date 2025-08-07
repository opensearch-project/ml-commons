/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a memory decision made by the LLM
 */
@Data
@Builder
public class MemoryDecision implements ToXContentObject, Writeable {

    private String id;
    private String text;
    private MemoryEvent event;
    private String oldMemory; // Only for UPDATE events

    public MemoryDecision(String id, String text, MemoryEvent event, String oldMemory) {
        this.id = id;
        this.text = text;
        this.event = event;
        this.oldMemory = oldMemory;
    }

    public MemoryDecision(StreamInput in) throws IOException {
        this.id = in.readString();
        this.text = in.readString();
        this.event = MemoryEvent.valueOf(in.readString());
        this.oldMemory = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeString(text);
        out.writeString(event.toString());
        out.writeOptionalString(oldMemory);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_ID_FIELD, id);
        builder.field(TEXT_FIELD, text);
        builder.field(EVENT_FIELD, event.toString());
        if (oldMemory != null) {
            builder.field(OLD_MEMORY_FIELD, oldMemory);
        }
        builder.endObject();
        return builder;
    }

    public static MemoryDecision parse(XContentParser parser) throws IOException {
        String id = null;
        String text = null;
        MemoryEvent event = null;
        String oldMemory = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_ID_FIELD:
                case "id": // Support both formats
                    id = parser.text();
                    break;
                case TEXT_FIELD:
                    text = parser.text();
                    break;
                case EVENT_FIELD:
                    event = MemoryEvent.fromString(parser.text());
                    break;
                case OLD_MEMORY_FIELD:
                    oldMemory = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MemoryDecision.builder().id(id).text(text).event(event).oldMemory(oldMemory).build();
    }
}
