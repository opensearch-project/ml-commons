/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a single memory result in the MLAddMemoryResponse
 */
@Getter
@ToString
@Builder
public class MemoryResult implements ToXContentObject, Writeable {

    private String memoryId;
    private String memory;
    private MemoryEvent event;
    private String oldMemory;

    public MemoryResult(String memoryId, String memory, MemoryEvent event, String oldMemory) {
        this.memoryId = memoryId;
        this.memory = memory;
        this.event = event;
        this.oldMemory = oldMemory;
    }

    public MemoryResult(StreamInput in) throws IOException {
        this.memoryId = in.readString();
        this.memory = in.readOptionalString();
        if (in.readBoolean()) {
            this.event = MemoryEvent.fromString(in.readString());
        }
        this.oldMemory = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memoryId);
        out.writeOptionalString(memory);
        if (event != null) {
            out.writeBoolean(true);
            out.writeString(event.getValue());
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(oldMemory);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("id", memoryId);
        if (memory != null) {
            builder.field("text", memory);
        }
        if (event != null) {
            builder.field("event", event.getValue());
        }
        if (oldMemory != null) {
            builder.field("old_memory", oldMemory);
        }
        builder.endObject();
        return builder;
    }
}
