/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLAddMemoryResponse extends ActionResponse implements ToXContentObject {

    private String memoryId;
    private String sessionId;
    private String status;

    @Builder
    public MLAddMemoryResponse(String memoryId, String sessionId, String status) {
        this.memoryId = memoryId;
        this.sessionId = sessionId;
        this.status = status;
    }

    public MLAddMemoryResponse(StreamInput in) throws IOException {
        super(in);
        this.memoryId = in.readString();
        this.sessionId = in.readString();
        this.status = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memoryId);
        out.writeString(sessionId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_ID_FIELD, memoryId);
        builder.field(SESSION_ID_FIELD, sessionId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
