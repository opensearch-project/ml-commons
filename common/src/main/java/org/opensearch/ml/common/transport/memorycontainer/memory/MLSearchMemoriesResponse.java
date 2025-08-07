/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

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

/**
 * ML search memories response
 */
@Getter
@Builder
public class MLSearchMemoriesResponse extends ActionResponse implements ToXContentObject {

    private List<MemorySearchResult> hits;
    private long totalHits;
    private float maxScore;
    private boolean timedOut;

    public MLSearchMemoriesResponse(List<MemorySearchResult> hits, long totalHits, float maxScore, boolean timedOut) {
        this.hits = hits != null ? hits : new ArrayList<>();
        this.totalHits = totalHits;
        this.maxScore = maxScore;
        this.timedOut = timedOut;
    }

    public MLSearchMemoriesResponse(StreamInput in) throws IOException {
        super(in);
        int hitCount = in.readVInt();
        this.hits = new ArrayList<>(hitCount);
        for (int i = 0; i < hitCount; i++) {
            this.hits.add(new MemorySearchResult(in));
        }
        this.totalHits = in.readVLong();
        this.maxScore = in.readFloat();
        this.timedOut = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(hits.size());
        for (MemorySearchResult hit : hits) {
            hit.writeTo(out);
        }
        out.writeVLong(totalHits);
        out.writeFloat(maxScore);
        out.writeBoolean(timedOut);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("timed_out", timedOut);

        builder.startObject("hits");
        builder.field("total", totalHits);
        builder.field("max_score", maxScore);

        builder.startArray("hits");
        for (MemorySearchResult hit : hits) {
            hit.toXContent(builder, params);
        }
        builder.endArray();

        builder.endObject(); // end hits object
        builder.endObject(); // end root object
        return builder;
    }
}
