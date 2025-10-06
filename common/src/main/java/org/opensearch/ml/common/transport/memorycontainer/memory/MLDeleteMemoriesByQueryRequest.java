/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;

/**
 * Request for deleting memories by query in a memory container
 */
@Getter
@Builder
public class MLDeleteMemoriesByQueryRequest extends ActionRequest implements ToXContentObject {

    private static final String QUERY_FIELD = "query";

    private String memoryContainerId;
    private MemoryType memoryType;
    private QueryBuilder query;

    public MLDeleteMemoriesByQueryRequest(String memoryContainerId, MemoryType memoryType, QueryBuilder query) {
        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType;
        this.query = query;
    }

    public MLDeleteMemoriesByQueryRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.memoryType = in.readEnum(MemoryType.class);
        this.query = in.readNamedWriteable(QueryBuilder.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        out.writeEnum(memoryType);
        out.writeNamedWriteable(query);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;

        if (memoryContainerId == null || memoryContainerId.isEmpty()) {
            validationException = addValidationError("Memory container ID is required", validationException);
        }

        if (memoryType == null) {
            validationException = addValidationError("Memory type is required", validationException);
        }

        if (query == null) {
            validationException = addValidationError("Query is required", validationException);
        }

        return validationException;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(PARAMETER_MEMORY_CONTAINER_ID, memoryContainerId);
        builder.field(PARAMETER_MEMORY_TYPE, memoryType.getValue());
        builder.field(QUERY_FIELD, query);
        builder.endObject();
        return builder;
    }

    public static MLDeleteMemoriesByQueryRequest parse(XContentParser parser) throws IOException {
        String memoryContainerId = null;
        MemoryType memoryType = null;
        QueryBuilder query = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PARAMETER_MEMORY_CONTAINER_ID:
                    memoryContainerId = parser.text();
                    break;
                case PARAMETER_MEMORY_TYPE:
                    memoryType = MemoryType.fromString(parser.text());
                    break;
                case QUERY_FIELD:
                    query = parseQuery(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLDeleteMemoriesByQueryRequest(memoryContainerId, memoryType, query);
    }

    private static QueryBuilder parseQuery(XContentParser parser) throws IOException {
        // Parse the query directly from the current position
        return parseInnerQueryBuilder(parser);
    }
}
