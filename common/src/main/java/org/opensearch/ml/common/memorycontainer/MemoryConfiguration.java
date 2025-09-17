/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INDEX_SETTINGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INVALID_EMBEDDING_MODEL_TYPE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_LIMIT_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_INDEX_PREFIX_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGIES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
public class MemoryConfiguration implements ToXContentObject, Writeable {

    private String indexPrefix;
    private FunctionName embeddingModelType;
    private String embeddingModelId;
    private String llmId;
    private Integer dimension;
    @Builder.Default
    private Integer maxInferSize = MAX_INFER_SIZE_DEFAULT_VALUE;
    private List<MemoryStrategy> strategies;
    @Builder.Default
    private Map<String, Map<String, Object>> indexSettings = new HashMap<>();
    private String tenantId;

    public MemoryConfiguration(
        String indexPrefix,
        FunctionName embeddingModelType,
        String embeddingModelId,
        String llmId,
        Integer dimension,
        Integer maxInferSize,
        List<MemoryStrategy> strategies,
        Map<String, Map<String, Object>> indexSettings,
        String tenantId
    ) {
        // Validate first
        validateInputs(embeddingModelType, embeddingModelId, dimension, maxInferSize);

        // Assign values after validation
        this.indexPrefix = indexPrefix == null ? "memory-" + UUID.randomUUID() : indexPrefix;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.llmId = llmId;
        this.dimension = dimension;
        this.maxInferSize = (llmId != null) ? (maxInferSize != null ? maxInferSize : MAX_INFER_SIZE_DEFAULT_VALUE) : null;
        this.strategies = new ArrayList<>();
        if (strategies != null && !strategies.isEmpty()) {
            this.strategies.addAll(strategies);
        }
        this.indexSettings = new HashMap<>();
        if (indexSettings != null && !indexSettings.isEmpty()) {
            this.indexSettings.putAll(indexSettings);
        }
        this.tenantId = tenantId;
    }

    public MemoryConfiguration(StreamInput input) throws IOException {
        this.indexPrefix = input.readOptionalString();
        String embeddingModelTypeStr = input.readOptionalString();
        this.embeddingModelType = embeddingModelTypeStr != null ? FunctionName.from(embeddingModelTypeStr) : null;
        this.embeddingModelId = input.readOptionalString();
        this.llmId = input.readOptionalString();
        this.dimension = input.readOptionalInt();
        this.maxInferSize = input.readOptionalInt();
        if (input.readBoolean()) {
            this.strategies = input.readList(MemoryStrategy::new);
        }
        if (input.readBoolean()) {
            this.indexSettings = input.readMap(StreamInput::readString, StreamInput::readMap);
        }
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(indexPrefix);
        out.writeOptionalString(embeddingModelType != null ? embeddingModelType.name() : null);
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalString(llmId);
        out.writeOptionalInt(dimension);
        out.writeOptionalInt(maxInferSize);
        if (!strategies.isEmpty()) {
            out.writeBoolean(true);
            out.writeList(strategies);
        } else {
            out.writeBoolean(false);
        }
        if (!indexSettings.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(indexSettings, StreamOutput::writeString, StreamOutput::writeMap);
        } else {
            out.writeBoolean(false);
        }

        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();

        // Always output these fields
        if (indexPrefix != null) {
            builder.field(MEMORY_INDEX_PREFIX_FIELD, indexPrefix);
        }

        // Always output LLM model if present (decoupled from semantic storage)
        if (llmId != null) {
            builder.field(LLM_ID_FIELD, llmId);
        }

        if (embeddingModelType != null) {
            builder.field(EMBEDDING_MODEL_TYPE_FIELD, embeddingModelType.name());
        }
        if (embeddingModelId != null) {
            builder.field(EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
        }
        if (dimension != null) {
            builder.field(DIMENSION_FIELD, dimension);
        }

        // Output maxInferSize when LLM model is configured
        if (llmId != null && maxInferSize != null) {
            builder.field(MAX_INFER_SIZE_FIELD, maxInferSize);
        }

        if (strategies != null && !strategies.isEmpty()) {
            builder.field(STRATEGIES_FIELD, strategies);
        }
        if (!indexSettings.isEmpty()) {
            builder.field(INDEX_SETTINGS_FIELD, indexSettings);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MemoryConfiguration parse(XContentParser parser) throws IOException {
        String indexPrefix = null;
        FunctionName embeddingModelType = null;
        String embeddingModelId = null;
        String llmId = null;
        Integer dimension = null;
        Integer maxInferSize = null;
        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Map<String, Object>> indexSettings = new HashMap<>();
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_INDEX_PREFIX_FIELD:
                    indexPrefix = parser.text();
                    break;
                case EMBEDDING_MODEL_TYPE_FIELD:
                    embeddingModelType = FunctionName.from(parser.text());
                    break;
                case EMBEDDING_MODEL_ID_FIELD:
                    embeddingModelId = parser.text();
                    break;
                case LLM_ID_FIELD:
                    llmId = parser.text();
                    break;
                case DIMENSION_FIELD:
                    dimension = parser.intValue();
                    break;
                case MAX_INFER_SIZE_FIELD:
                    maxInferSize = parser.intValue();
                    break;
                case STRATEGIES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        strategies.add(MemoryStrategy.parse(parser));
                    }
                    break;
                case INDEX_SETTINGS_FIELD:
                    indexSettings = parser.map(HashMap::new, p -> p.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Note: validation is already called in the constructor
        return MemoryConfiguration
            .builder()
            .indexPrefix(indexPrefix)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .llmId(llmId)
            .dimension(dimension)
            .maxInferSize(maxInferSize)
            .strategies(strategies)
            .indexSettings(indexSettings)
            .tenantId(tenantId)
            .build();
    }

    public String getSessionIndexName() {
        return indexPrefix + "-session";
    }

    public String getShortTermMemoryIndexName() {
        return indexPrefix + "-short-term-memory";
    }

    public String getLongMemoryIndexName() {
        return indexPrefix + "-long-term-memory";
    }
    public String getLongMemoryHistoryIndexName() {
        return indexPrefix + "-long-term-memory-history";
    }

    public Map<String, Object> getMemoryIndexMapping(String indexName) {
        Map<String, Map<String, Object>> indexSettings = this.getIndexSettings();
        if (indexSettings != null) {
            return indexSettings.get(indexName);
        }
        return null;
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
