/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Data;

/**
 * Request structure for memory decision making
 */
@Data
@Builder
public class MemoryDecisionRequest implements ToXContentObject {

    // List of existing memories with scores
    private List<OldMemory> oldMemory;

    // List of newly extracted facts
    private List<String> retrievedFacts;

    @Data
    @Builder
    public static class OldMemory implements ToXContentObject {
        private String id;
        private String text;
        private float score;

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
            builder.startObject();
            builder.field("id", id);
            builder.field("text", text);
            builder.field("score", score);
            builder.endObject();
            return builder;
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();

        // Build old_memory array
        builder.startArray(OLD_MEMORY_FIELD);
        if (oldMemory != null) {
            for (OldMemory memory : oldMemory) {
                memory.toXContent(builder, params);
            }
        }
        builder.endArray();

        // Build retrieved_facts array
        builder.startArray(RETRIEVED_FACTS_FIELD);
        if (retrievedFacts != null) {
            for (String fact : retrievedFacts) {
                builder.value(fact);
            }
        }
        builder.endArray();

        builder.endObject();
        return builder;
    }

    /**
     * Convert to string for LLM request
     */
    public String toJsonString() {
        try {
            XContentBuilder builder = XContentBuilder.builder(org.opensearch.common.xcontent.json.JsonXContent.jsonXContent);
            toXContent(builder, ToXContent.EMPTY_PARAMS);
            return builder.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MemoryDecisionRequest", e);
        }
    }
}
