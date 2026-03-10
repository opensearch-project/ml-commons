/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Base class for semantic and hybrid search input on long-term memories.
 * Contains all common fields and serialization logic.
 */
@Getter
@Setter
public abstract class MLSearchMemoriesBaseInput implements ToXContentObject, Writeable {

    protected String memoryContainerId;
    protected String query;
    protected int k;
    protected Map<String, String> namespace;
    protected Map<String, String> tags;
    protected Float minScore;
    protected QueryBuilder filter;

    protected MLSearchMemoriesBaseInput(
        String memoryContainerId,
        String query,
        int k,
        Map<String, String> namespace,
        Map<String, String> tags,
        Float minScore,
        QueryBuilder filter
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be null or blank");
        }
        this.memoryContainerId = memoryContainerId;
        this.query = query;
        this.k = k;
        this.namespace = namespace;
        this.tags = tags;
        this.minScore = minScore;
        this.filter = filter;
    }

    protected MLSearchMemoriesBaseInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.query = in.readString();
        this.k = in.readVInt();
        this.namespace = in.readBoolean() ? in.readMap(StreamInput::readString, StreamInput::readString) : null;
        this.tags = in.readBoolean() ? in.readMap(StreamInput::readString, StreamInput::readString) : null;
        this.minScore = in.readBoolean() ? in.readFloat() : null;
        this.filter = in.readBoolean() ? in.readNamedWriteable(QueryBuilder.class) : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeString(query);
        out.writeVInt(k);
        out.writeBoolean(namespace != null);
        if (namespace != null) {
            out.writeMap(namespace, StreamOutput::writeString, StreamOutput::writeString);
        }
        out.writeBoolean(tags != null);
        if (tags != null) {
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        }
        out.writeBoolean(minScore != null);
        if (minScore != null) {
            out.writeFloat(minScore);
        }
        out.writeBoolean(filter != null);
        if (filter != null) {
            out.writeNamedWriteable(filter);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field("memory_container_id", memoryContainerId);
        }
        builder.field("query", query);
        builder.field("k", k);
        if (namespace != null) {
            builder.field("namespace", namespace);
        }
        if (tags != null) {
            builder.field("tags", tags);
        }
        if (minScore != null) {
            builder.field("min_score", minScore);
        }
        if (filter != null) {
            builder.field("filter", filter);
        }
        builder.endObject();
        return builder;
    }
}
