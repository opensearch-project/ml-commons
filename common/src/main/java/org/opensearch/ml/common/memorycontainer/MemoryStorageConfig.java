/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INVALID_EMBEDDING_MODEL_TYPE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_LIMIT_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_SHORT_TERM_MEMORIES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_SHORT_TERM_MEMORIES_SEMANTIC_LIMIT_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_INDEX_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_ENABLED_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_LLM_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR;

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
    @Builder.Default
    private Integer maxShortTermMemories = MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE;
    @Builder.Default
    private Integer maxInferSize = MAX_INFER_SIZE_DEFAULT_VALUE;

    public MemoryStorageConfig(
        String memoryIndexName,
        boolean semanticStorageEnabled,
        FunctionName embeddingModelType,
        String embeddingModelId,
        String llmModelId,
        Integer dimension,
        Integer maxShortTermMemories,
        Integer maxInferSize
    ) {
        this.memoryIndexName = memoryIndexName;
        this.semanticStorageEnabled = semanticStorageEnabled;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.llmModelId = llmModelId;
        this.dimension = dimension;
        this.maxShortTermMemories = maxShortTermMemories != null ? maxShortTermMemories : MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE;
        this.maxInferSize = maxInferSize != null ? maxInferSize : MAX_INFER_SIZE_DEFAULT_VALUE;

        // Validate the configuration
        validate();
    }

    public MemoryStorageConfig(StreamInput input) throws IOException {
        this.memoryIndexName = input.readOptionalString();
        this.semanticStorageEnabled = input.readBoolean();
        String embeddingModelTypeStr = input.readOptionalString();
        this.embeddingModelType = embeddingModelTypeStr != null ? FunctionName.from(embeddingModelTypeStr) : null;
        this.embeddingModelId = input.readOptionalString();
        this.llmModelId = input.readOptionalString();
        this.dimension = input.readOptionalInt();
        Integer maxShortTermMemoriesVal = input.readOptionalInt();
        this.maxShortTermMemories = maxShortTermMemoriesVal != null ? maxShortTermMemoriesVal : MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE;
        Integer maxInferSizeVal = input.readOptionalInt();
        this.maxInferSize = maxInferSizeVal != null ? maxInferSizeVal : MAX_INFER_SIZE_DEFAULT_VALUE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryIndexName);
        out.writeBoolean(semanticStorageEnabled);
        out.writeOptionalString(embeddingModelType != null ? embeddingModelType.name() : null);
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalString(llmModelId);
        out.writeOptionalInt(dimension);
        out.writeOptionalInt(maxShortTermMemories);
        out.writeOptionalInt(maxInferSize);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();

        // Always output these fields
        if (memoryIndexName != null) {
            builder.field(MEMORY_INDEX_NAME_FIELD, memoryIndexName);
        }
        builder.field(SEMANTIC_STORAGE_ENABLED_FIELD, semanticStorageEnabled);

        if (!semanticStorageEnabled) {
            // When semantic storage is disabled, only output allowed fields
            if (maxShortTermMemories != null) {
                builder.field(MAX_SHORT_TERM_MEMORIES_FIELD, maxShortTermMemories);
            }
            // Do not output other fields when semantic storage is disabled
        } else {
            // When semantic storage is enabled, output all relevant fields
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
            if (maxShortTermMemories != null) {
                builder.field(MAX_SHORT_TERM_MEMORIES_FIELD, maxShortTermMemories);
            }
            if (maxInferSize != null) {
                builder.field(MAX_INFER_SIZE_FIELD, maxInferSize);
            }
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
        Integer maxShortTermMemories = null;
        Integer maxInferSize = null;

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
                case MAX_SHORT_TERM_MEMORIES_FIELD:
                    maxShortTermMemories = parser.intValue();
                    break;
                case MAX_INFER_SIZE_FIELD:
                    maxInferSize = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .memoryIndexName(memoryIndexName)
            .semanticStorageEnabled(semanticStorageEnabled)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .llmModelId(llmModelId)
            .dimension(dimension)
            .maxShortTermMemories(maxShortTermMemories != null ? maxShortTermMemories : MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE)
            .maxInferSize(maxInferSize != null ? maxInferSize : MAX_INFER_SIZE_DEFAULT_VALUE)
            .build();

        // Note: validation is already called in the constructor
        return config;
    }

    /**
     * Validates the memory storage configuration based on semantic storage settings
     * and enforces field restrictions and limits.
     */
    public void validate() {
        if (!semanticStorageEnabled) {
            // When semantic storage is disabled, only allow specific fields
            // Clear fields that aren't allowed
            this.embeddingModelType = null;
            this.embeddingModelId = null;
            this.llmModelId = null;
            this.dimension = null;
            this.maxInferSize = null;
            this.maxShortTermMemories = null;

            // No limit on max_short_term_memories for non-semantic storage since all memories are LONG_TERM
        } else {
            // When semantic storage is enabled, validate required fields and limits
            // Validate max_infer_size limit (applies regardless of semantic storage)
            if (maxInferSize != null && maxInferSize > 10) {
                throw new IllegalArgumentException(MAX_INFER_SIZE_LIMIT_ERROR);
            }
            // Validate max_short_term_memories limit for semantic storage
            if (maxShortTermMemories != null && maxShortTermMemories > 10) {
                throw new IllegalArgumentException(MAX_SHORT_TERM_MEMORIES_SEMANTIC_LIMIT_ERROR);
            }

            // Validate required fields for semantic storage
            if (embeddingModelType == null) {
                throw new IllegalArgumentException(SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR);
            }

            if (embeddingModelId == null) {
                throw new IllegalArgumentException(SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR);
            }

            if (llmModelId == null) {
                throw new IllegalArgumentException(SEMANTIC_STORAGE_LLM_MODEL_ID_REQUIRED_ERROR);
            }

            // Validate embedding model type
            if (embeddingModelType != FunctionName.TEXT_EMBEDDING && embeddingModelType != FunctionName.SPARSE_ENCODING) {
                throw new IllegalArgumentException(INVALID_EMBEDDING_MODEL_TYPE_ERROR);
            }

            // Validate dimension based on embedding type
            if (embeddingModelType == FunctionName.TEXT_EMBEDDING && dimension == null) {
                throw new IllegalArgumentException(TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR);
            }

            if (embeddingModelType == FunctionName.SPARSE_ENCODING && dimension != null) {
                throw new IllegalArgumentException(SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR);
            }
        }
    }
}
