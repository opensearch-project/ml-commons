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

    private final String memoryId;
    private final String memory;
    private final MemoryEvent event;

    public MemoryResult(String memoryId, String memory, MemoryEvent event) {
        this.memoryId = memoryId;
        this.memory = memory;
        this.event = event;
    }

    public MemoryResult(StreamInput in) throws IOException {
        this.memoryId = in.readString();
        this.memory = in.readString();
        this.event = MemoryEvent.fromString(in.readString());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memoryId);
        out.writeString(memory);
        out.writeString(event.getValue());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_ID_FIELD, memoryId);
        builder.field(MEMORY_FIELD, memory);
        builder.field("event", event.getValue());
        builder.endObject();
        return builder;
    }
}
