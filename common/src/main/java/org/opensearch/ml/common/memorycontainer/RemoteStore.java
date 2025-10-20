/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Remote store configuration for storing memory in remote locations like AWS OpenSearch Serverless
 */
@Data
@EqualsAndHashCode
public class RemoteStore implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";
    public static final String CONNECTOR_ID_FIELD = "connector_id";

    private RemoteStoreType type;
    private String connectorId;
    private FunctionName embeddingModelType;
    private String embeddingModelId;
    private Integer embeddingDimension;

    @Builder
    public RemoteStore(
        RemoteStoreType type,
        String connectorId,
        FunctionName embeddingModelType,
        String embeddingModelId,
        Integer embeddingDimension
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Invalid remote store type");
        }
        this.type = type;
        this.connectorId = connectorId;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.embeddingDimension = embeddingDimension;
    }

    public RemoteStore(StreamInput input) throws IOException {
        this.type = input.readEnum(RemoteStoreType.class);
        this.connectorId = input.readOptionalString();
        if (input.readOptionalBoolean()) {
            this.embeddingModelType = input.readEnum(FunctionName.class);
        }
        this.embeddingModelId = input.readOptionalString();
        this.embeddingDimension = input.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(type);
        out.writeOptionalString(connectorId);
        if (embeddingModelType != null) {
            out.writeBoolean(true);
            out.writeEnum(embeddingModelType);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalInt(embeddingDimension);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (embeddingModelType != null) {
            builder.field(EMBEDDING_MODEL_TYPE_FIELD, embeddingModelType);
        }
        if (embeddingModelId != null) {
            builder.field(EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
        }
        if (embeddingDimension != null) {
            builder.field(DIMENSION_FIELD, embeddingDimension);
        }
        builder.endObject();
        return builder;
    }

    public static RemoteStore parse(XContentParser parser) throws IOException {
        RemoteStoreType type = null;
        String connectorId = null;
        FunctionName embeddingModelType = null;
        String embeddingModelId = null;
        Integer embeddingDimension = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = RemoteStoreType.fromString(parser.text());
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case EMBEDDING_MODEL_TYPE_FIELD:
                    embeddingModelType = FunctionName.from(parser.text());
                    break;
                case EMBEDDING_MODEL_ID_FIELD:
                    embeddingModelId = parser.text();
                    break;
                case DIMENSION_FIELD:
                    embeddingDimension = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return RemoteStore
            .builder()
            .type(type)
            .connectorId(connectorId)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .embeddingDimension(embeddingDimension)
            .build();
    }
}
