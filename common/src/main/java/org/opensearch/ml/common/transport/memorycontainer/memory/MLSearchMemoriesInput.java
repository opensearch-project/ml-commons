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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for searching memories in a memory container
 */
@Getter
@Setter
@Builder
public class MLSearchMemoriesInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private MemoryType memoryType;
    private SearchSourceBuilder searchSourceBuilder;

    public MLSearchMemoriesInput(String memoryContainerId, MemoryType memoryType, SearchSourceBuilder searchSourceBuilder) {
        if (searchSourceBuilder == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType;
        this.searchSourceBuilder = searchSourceBuilder;
    }

    public MLSearchMemoriesInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.memoryType = in.readBoolean() ? in.readEnum(MemoryType.class) : null;
        this.searchSourceBuilder = new SearchSourceBuilder(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeBoolean(memoryType != null);
        if (memoryType != null) {
            out.writeEnum(memoryType);
        }
        searchSourceBuilder.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        if (memoryType != null) {
            builder.field(PARAMETER_MEMORY_TYPE, memoryType.getValue());
        }
        builder.field(QUERY_FIELD, searchSourceBuilder);
        builder.endObject();
        return builder;
    }

}
