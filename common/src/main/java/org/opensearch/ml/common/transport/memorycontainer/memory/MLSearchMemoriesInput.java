/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

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
    private String query;

    public MLSearchMemoriesInput(String memoryContainerId, String query) {
        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        this.memoryContainerId = memoryContainerId;
        this.query = query.trim();
    }

    public MLSearchMemoriesInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.query = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeString(query);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        builder.field(QUERY_FIELD, query);
        builder.endObject();
        return builder;
    }

    public static MLSearchMemoriesInput parse(XContentParser parser) throws IOException {
        String memoryContainerId = null;
        String query = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                case QUERY_FIELD:
                    query = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLSearchMemoriesInput.builder().memoryContainerId(memoryContainerId).query(query).build();
    }
}
