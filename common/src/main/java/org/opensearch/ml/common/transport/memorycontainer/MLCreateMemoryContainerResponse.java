/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STATUS_FIELD;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

@Getter
public class MLCreateMemoryContainerResponse extends ActionResponse implements ToXContentObject {

    private String memoryContainerId;
    private String status;

    public MLCreateMemoryContainerResponse(String memoryContainerId, String status) {
        this.memoryContainerId = memoryContainerId;
        this.status = status;
    }

    public MLCreateMemoryContainerResponse(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.status = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memoryContainerId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
