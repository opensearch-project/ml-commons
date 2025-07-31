/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_INDEX_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_ENABLED_FIELD;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for memory storage in memory containers
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
public class MemoryStorageConfig implements ToXContentObject, Writeable {

    private String memoryIndexName;
    private boolean semanticStorageEnabled;
    private FunctionName embeddingModelType;
    private String embeddingModelId;
    private String llmModelId;
    private Integer dimension;

    public MemoryStorageConfig(
        String memoryIndexName,
        boolean semanticStorageEnabled,
        FunctionName embeddingModelType,
        String embeddingModelId,
        String llmModelId,
        Integer dimension
    ) {
        this.memoryIndexName = memoryIndexName;
        this.semanticStorageEnabled = semanticStorageEnabled;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.llmModelId = llmModelId;
        this.dimension = dimension;
    }

    public MemoryStorageConfig(StreamInput input) throws IOException {
        this.memoryIndexName = input.readOptionalString();
        this.semanticStorageEnabled = input.readBoolean();
        String embeddingModelTypeStr = input.readOptionalString();
        this.embeddingModelType = embeddingModelTypeStr != null ? FunctionName.from(embeddingModelTypeStr) : null;
        this.embeddingModelId = input.readOptionalString();
        this.llmModelId = input.readOptionalString();
        this.dimension = input.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryIndexName);
        out.writeBoolean(semanticStorageEnabled);
        out.writeOptionalString(embeddingModelType != null ? embeddingModelType.name() : null);
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalString(llmModelId);
        out.writeOptionalInt(dimension);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (memoryIndexName != null) {
            builder.field(MEMORY_INDEX_NAME_FIELD, memoryIndexName);
        }
        builder.field(SEMANTIC_STORAGE_ENABLED_FIELD, semanticStorageEnabled);
        if (embeddingModelType != null) {
            builder.field(EMBEDDING_MODEL_TYPE_FIELD, embeddingModelType.name());
        }
        if (embeddingModelId != null) {
            builder.field(EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
        }
        if (llmModelId != null) {
            builder.field(LLM_MODEL_ID_FIELD, llmModelId);
        }
        if (dimension != null) {
            builder.field(DIMENSION_FIELD, dimension);
        }
        builder.endObject();
        return builder;
    }

    public static MemoryStorageConfig parse(XContentParser parser) throws IOException {
        String memoryIndexName = null;
        boolean semanticStorageEnabled = false;
        FunctionName embeddingModelType = null;
        String embeddingModelId = null;
        String llmModelId = null;
        Integer dimension = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_INDEX_NAME_FIELD:
                    memoryIndexName = parser.text();
                    break;
                case SEMANTIC_STORAGE_ENABLED_FIELD:
                    semanticStorageEnabled = parser.booleanValue();
                    break;
                case EMBEDDING_MODEL_TYPE_FIELD:
                    embeddingModelType = FunctionName.from(parser.text());
                    break;
                case EMBEDDING_MODEL_ID_FIELD:
                    embeddingModelId = parser.text();
                    break;
                case LLM_MODEL_ID_FIELD:
                    llmModelId = parser.text();
                    break;
                case DIMENSION_FIELD:
                    dimension = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MemoryStorageConfig
            .builder()
            .memoryIndexName(memoryIndexName)
            .semanticStorageEnabled(semanticStorageEnabled)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .llmModelId(llmModelId)
            .dimension(dimension)
            .build();
    }
}
