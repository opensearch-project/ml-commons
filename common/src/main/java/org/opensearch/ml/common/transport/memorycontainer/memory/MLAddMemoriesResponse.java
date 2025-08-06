/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
public class MLAddMemoriesResponse extends ActionResponse implements ToXContentObject {

    private List<MemoryResult> results;
    private String sessionId;

    @Builder
    public MLAddMemoriesResponse(List<MemoryResult> results, String sessionId) {
        this.results = results != null ? results : new ArrayList<>();
        this.sessionId = sessionId;
    }

    public MLAddMemoriesResponse(StreamInput in) throws IOException {
        super(in);
        int size = in.readVInt();
        this.results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.results.add(new MemoryResult(in));
        }
        this.sessionId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(results.size());
        for (MemoryResult result : results) {
            result.writeTo(out);
        }
        out.writeString(sessionId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startArray("results");
        for (MemoryResult result : results) {
            result.toXContent(builder, params);
        }
        builder.endArray();
        builder.field(SESSION_ID_FIELD, sessionId);
        builder.endObject();
        return builder;
    }
}
