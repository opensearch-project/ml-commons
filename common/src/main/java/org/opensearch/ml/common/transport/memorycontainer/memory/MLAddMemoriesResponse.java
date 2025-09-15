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
    private String shorTermMemoryId;

    @Builder
    public MLAddMemoriesResponse(List<MemoryResult> results, String sessionId, String shorTermMemoryId) {
        this.results = results != null ? results : new ArrayList<>();
        this.sessionId = sessionId;
        this.shorTermMemoryId = shorTermMemoryId;
    }

    public MLAddMemoriesResponse(StreamInput in) throws IOException {
        super(in);
        this.shorTermMemoryId = in.readOptionalString();
        int size = in.readVInt();
        this.results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.results.add(new MemoryResult(in));
        }
        this.sessionId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(shorTermMemoryId);
        out.writeVInt(results.size());
        for (MemoryResult result : results) {
            result.writeTo(out);
        }
        out.writeOptionalString(sessionId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (shorTermMemoryId != null) {
            builder.field(SHORT_TERM_MEMORY_ID_FIELD, shorTermMemoryId);
        }
        if (results != null && results.size() > 0) {
            builder.startArray("long_term_memories"); //TODO: add constant
            for (MemoryResult result : results) {
                result.toXContent(builder, params);
            }
            builder.endArray();
        }

        builder.endObject();
        return builder;
    }
}
