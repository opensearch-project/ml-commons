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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_INDEX_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_ENABLED_FIELD;
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
    private Integer maxInferSize = MAX_INFER_SIZE_DEFAULT_VALUE;

    public MemoryStorageConfig(
        String memoryIndexName,
        boolean semanticStorageEnabled,
        FunctionName embeddingModelType,
        String embeddingModelId,
        String llmModelId,
        Integer dimension,
        Integer maxInferSize
    ) {
        // Validate first
        validateInputs(embeddingModelType, embeddingModelId, dimension, maxInferSize);

        // Auto-determine semantic storage based on embedding configuration
        boolean determinedSemanticStorage = (embeddingModelId != null && embeddingModelType != null);

        // Assign values after validation
        this.memoryIndexName = memoryIndexName;
        this.semanticStorageEnabled = determinedSemanticStorage;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.llmModelId = llmModelId;
        this.dimension = dimension;
        this.maxInferSize = (llmModelId != null) ? (maxInferSize != null ? maxInferSize : MAX_INFER_SIZE_DEFAULT_VALUE) : null;
    }

    public MemoryStorageConfig(StreamInput input) throws IOException {
        this.memoryIndexName = input.readOptionalString();
        this.semanticStorageEnabled = input.readBoolean();
        String embeddingModelTypeStr = input.readOptionalString();
        this.embeddingModelType = embeddingModelTypeStr != null ? FunctionName.from(embeddingModelTypeStr) : null;
        this.embeddingModelId = input.readOptionalString();
        this.llmModelId = input.readOptionalString();
        this.dimension = input.readOptionalInt();
        this.maxInferSize = input.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryIndexName);
        out.writeBoolean(semanticStorageEnabled);
        out.writeOptionalString(embeddingModelType != null ? embeddingModelType.name() : null);
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalString(llmModelId);
        out.writeOptionalInt(dimension);
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

        // Always output LLM model if present (decoupled from semantic storage)
        if (llmModelId != null) {
            builder.field(LLM_MODEL_ID_FIELD, llmModelId);
        }

        // When semantic storage is enabled, output embedding-related fields
        if (semanticStorageEnabled) {
            if (embeddingModelType != null) {
                builder.field(EMBEDDING_MODEL_TYPE_FIELD, embeddingModelType.name());
            }
            if (embeddingModelId != null) {
                builder.field(EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
            }
            if (dimension != null) {
                builder.field(DIMENSION_FIELD, dimension);
            }
        }

        // Output maxInferSize when LLM model is configured
        if (llmModelId != null && maxInferSize != null) {
            builder.field(MAX_INFER_SIZE_FIELD, maxInferSize);
        }

        builder.endObject();
        return builder;
    }

    public static MemoryStorageConfig parse(XContentParser parser) throws IOException {
        String memoryIndexName = null;
        FunctionName embeddingModelType = null;
        String embeddingModelId = null;
        String llmModelId = null;
        Integer dimension = null;
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
                    // Skip this field - it's now auto-determined
                    parser.skipChildren();
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
                case MAX_INFER_SIZE_FIELD:
                    maxInferSize = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Note: validation is already called in the constructor
        return MemoryStorageConfig
            .builder()
            .memoryIndexName(memoryIndexName)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .llmModelId(llmModelId)
            .dimension(dimension)
            .maxInferSize(maxInferSize)
            .build();
    }

    /**
     * Validates input parameters before construction.
     */
    private static void validateInputs(FunctionName embeddingModelType, String embeddingModelId, Integer dimension, Integer maxInferSize) {
        validateEmbeddingConfiguration(embeddingModelType, embeddingModelId, dimension);
        validateMaxInferSize(maxInferSize);
    }

    /**
     * Validates embedding configuration including model pairing and dimension requirements.
     */
    private static void validateEmbeddingConfiguration(FunctionName embeddingModelType, String embeddingModelId, Integer dimension) {
        // Check for partial embedding configuration
        if (embeddingModelId != null && embeddingModelType == null) {
            throw new IllegalArgumentException(SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR);
        }
        if (embeddingModelType != null && embeddingModelId == null) {
            throw new IllegalArgumentException(SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR);
        }

        // If embedding model type is provided, validate it
        if (embeddingModelType != null) {
            validateEmbeddingModelType(embeddingModelType);
            validateDimensionRequirements(embeddingModelType, dimension);
        }
    }

    /**
     * Validates max infer size limit.
     */
    private static void validateMaxInferSize(Integer maxInferSize) {
        if (maxInferSize != null && maxInferSize > 10) {
            throw new IllegalArgumentException(MAX_INFER_SIZE_LIMIT_ERROR);
        }
    }

    /**
     * Validates that the embedding model type is supported.
     */
    private static void validateEmbeddingModelType(FunctionName embeddingModelType) {
        if (embeddingModelType != FunctionName.TEXT_EMBEDDING && embeddingModelType != FunctionName.SPARSE_ENCODING) {
            throw new IllegalArgumentException(INVALID_EMBEDDING_MODEL_TYPE_ERROR);
        }
    }

    /**
     * Validates dimension requirements based on embedding type.
     * TEXT_EMBEDDING requires dimension, SPARSE_ENCODING does not allow dimension.
     */
    private static void validateDimensionRequirements(FunctionName embeddingModelType, Integer dimension) {
        if (embeddingModelType == FunctionName.TEXT_EMBEDDING && dimension == null) {
            throw new IllegalArgumentException(TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR);
        }

        if (embeddingModelType == FunctionName.SPARSE_ENCODING && dimension != null) {
            throw new IllegalArgumentException(SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR);
        }
    }
}
