/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DISABLE_HISTORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DISABLE_SESSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INDEX_SETTINGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INVALID_EMBEDDING_MODEL_TYPE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_INFER_SIZE_LIMIT_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_INDEX_PREFIX_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETERS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGIES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USE_SYSTEM_INDEX_FIELD;

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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Configuration for memory storage in memory containers
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@Log4j2
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
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();
    @Builder.Default
    private boolean disableHistory = false;
    @Builder.Default
    private boolean disableSession = true;
    @Builder.Default
    private boolean useSystemIndex = true;
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
        Map<String, Object> parameters,
        boolean disableHistory,
        boolean disableSession,
        boolean useSystemIndex,
        String tenantId
    ) {
        // Validate first
        validateInputs(embeddingModelType, embeddingModelId, dimension, maxInferSize);

        // Assign values after validation
        this.indexPrefix = buildIndexPrefix(indexPrefix, useSystemIndex);
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
        this.parameters = new HashMap<>();
        if (parameters != null && !parameters.isEmpty()) {
            this.parameters.putAll(parameters);
        }
        this.disableHistory = disableHistory;
        this.disableSession = disableSession;
        this.useSystemIndex = useSystemIndex;
        this.tenantId = tenantId;
    }

    private String buildIndexPrefix(String indexPrefix, boolean useSystemIndex) {
        if (indexPrefix == null || indexPrefix.isBlank()) {
            // Generate a unique prefix upfront
            return useSystemIndex
                ? DEFAULT_MEMORY_INDEX_PREFIX
                : UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
        }
        return indexPrefix;
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
        if (input.readBoolean()) {
            this.parameters = input.readMap();
        }
        this.disableHistory = input.readBoolean();
        this.disableSession = input.readBoolean();
        this.useSystemIndex = input.readBoolean();
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
        if (!parameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(parameters);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(disableHistory);
        out.writeBoolean(disableSession);
        out.writeBoolean(useSystemIndex);
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
        if (!parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        builder.field(DISABLE_HISTORY_FIELD, disableHistory);
        builder.field(DISABLE_SESSION_FIELD, disableSession);
        builder.field(USE_SYSTEM_INDEX_FIELD, useSystemIndex);
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
        Map<String, Object> parameters = new HashMap<>();
        boolean disableHistory = false;
        boolean disableSession = true;
        boolean useSystemIndex = true;
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
                case PARAMETERS_FIELD:
                    parameters = parser.map();
                    break;
                case DISABLE_HISTORY_FIELD:
                    disableHistory = parser.booleanValue();
                    break;
                case DISABLE_SESSION_FIELD:
                    disableSession = parser.booleanValue();
                    break;
                case USE_SYSTEM_INDEX_FIELD:
                    useSystemIndex = parser.booleanValue();
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
            .parameters(parameters)
            .disableHistory(disableHistory)
            .disableSession(disableSession)
            .useSystemIndex(useSystemIndex)
            .tenantId(tenantId)
            .build();
    }

    public String getFinalMemoryIndexPrefix() {
        if (useSystemIndex) {
            return ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX + "-" + indexPrefix + "-memory-";
        } else {
            return indexPrefix + "-memory-";
        }
    }

    public String getIndexName(MemoryType memoryType) {
        if (memoryType == null) {
            return null;
        }
        // Check if disabled
        if (memoryType == MemoryType.SESSIONS && isDisableSession()) {
            return null;
        }
        if (memoryType == MemoryType.HISTORY && isDisableHistory()) {
            return null;
        }
        return getFinalMemoryIndexPrefix() + memoryType.getIndexSuffix();
    }

    public String getSessionIndexName() {
        return getIndexName(MemoryType.SESSIONS);
    }

    public String getWorkingMemoryIndexName() {
        return getIndexName(MemoryType.WORKING);
    }

    public String getLongMemoryIndexName() {
        return getIndexName(MemoryType.LONG_TERM);
    }

    public String getLongMemoryHistoryIndexName() {
        return getIndexName(MemoryType.HISTORY);
    }

    public Map<String, Object> getMemoryIndexMapping(String indexName) {
        Map<String, Map<String, Object>> indexSettings = this.getIndexSettings();
        if (indexSettings != null) {
            return indexSettings.get(indexName);
        }
        return null;
    }

    /**
     * Validates this configuration's state.
     * Ensures embedding configuration and max infer size satisfy all constraints.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        validateInputs(this.embeddingModelType, this.embeddingModelId, this.dimension, this.maxInferSize);
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

    /**
     * Validates that strategies have required AI models.
     * Strategies require both LLM (for fact extraction) and embedding model (for semantic search).
     *
     * @param config The memory configuration to validate
     * @throws IllegalArgumentException if strategies exist without required models
     */
    public static void validateStrategiesRequireModels(MemoryConfiguration config) {
        if (config == null || config.getStrategies() == null || config.getStrategies().isEmpty()) {
            return;
        }

        boolean hasLlm = config.getLlmId() != null;
        boolean hasEmbedding = config.getEmbeddingModelId() != null && config.getEmbeddingModelType() != null;

        if (!hasLlm || !hasEmbedding) {
            String missing = !hasLlm && !hasEmbedding ? "LLM model and embedding model"
                : !hasLlm ? "LLM model (llm_id)"
                : "embedding model (embedding_model_id, embedding_model_type, dimension)";

            throw new IllegalArgumentException(
                String
                    .format(
                        "Strategies require both an LLM model and embedding model to be configured. Missing: %s. "
                            + "Strategies use LLM for fact extraction and embedding model for semantic search.",
                        missing
                    )
            );
        }
    }

    /**
     * Updates this configuration with non-null values from the update content.
     * Follows the pattern from HttpConnector.update() for partial updates.
     * Note: Embedding fields (embeddingModelId, embeddingModelType, dimension) CAN be updated
     * to support gradual configuration, but changes to existing values are validated elsewhere
     * to prevent multiple embeddings in the same index.
     *
     * @param updateContent Configuration containing fields to update
     */
    public void update(MemoryConfiguration updateContent) {
        if (updateContent.getLlmId() != null) {
            this.llmId = updateContent.getLlmId();
        }
        if (updateContent.getStrategies() != null && !updateContent.getStrategies().isEmpty()) {
            this.strategies = updateContent.getStrategies();
        }
        if (updateContent.getMaxInferSize() != null) {
            this.maxInferSize = updateContent.getMaxInferSize();
        }
        // Allow embedding fields to be set (for gradual configuration)
        // Validation elsewhere ensures existing embeddings cannot be changed
        if (updateContent.getEmbeddingModelId() != null) {
            this.embeddingModelId = updateContent.getEmbeddingModelId();
        }
        if (updateContent.getEmbeddingModelType() != null) {
            this.embeddingModelType = updateContent.getEmbeddingModelType();
        }
        if (updateContent.getDimension() != null) {
            this.dimension = updateContent.getDimension();
        }
        // Note: indexPrefix and other structural fields are intentionally not updated
        // as they would require index recreation
    }

    /**
     * Configuration extracted from existing index/pipeline for validation.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmbeddingConfig {
        private FunctionName type;
        private Integer dimension;
    }

    /**
     * Extracts embedding configuration from index mapping properties.
     * Used to validate that new containers match existing shared index configuration.
     *
     * @param mappingProperties Index mapping properties map
     * @return EmbeddingConfig with type and dimension, or null if no embedding field
     */
    public static EmbeddingConfig extractEmbeddingConfigFromMapping(Map<String, Object> mappingProperties) {
        if (mappingProperties == null) {
            return null;
        }

        Map<String, Object> fieldMap = asMap(mappingProperties.get("memory_embedding"));
        if (fieldMap == null) {
            log.debug("Embedding field 'memory_embedding' is null or not a Map");
            return null;
        }

        String type = (String) fieldMap.get("type");

        if ("knn_vector".equals(type)) {
            Integer dimension = (Integer) fieldMap.get("dimension");
            return new EmbeddingConfig(FunctionName.TEXT_EMBEDDING, dimension);
        } else if ("rank_features".equals(type)) {
            return new EmbeddingConfig(FunctionName.SPARSE_ENCODING, null);
        }

        return null;
    }

    /**
     * Safely casts object to Map, returns null if not a Map.
     * Used to prevent ClassCastException when processing OpenSearch responses.
     *
     * @param obj Object to cast
     * @return Map if obj is a Map, null otherwise
     */
    public static Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : null;
    }

    /**
     * Safely casts object to List, returns null if not a List.
     * Used to prevent ClassCastException when processing OpenSearch responses.
     *
     * @param obj Object to cast
     * @return List if obj is a List, null otherwise
     */
    public static List<?> asList(Object obj) {
        return obj instanceof List ? (List<?>) obj : null;
    }

    /**
     * Extracts embedding model ID from ingest pipeline definition.
     * Used to validate that new containers match existing shared index configuration.
     *
     * @param pipelineSource Pipeline source map from GetPipelineResponse
     * @return model_id string, or null if not found
     */
    public static String extractModelIdFromPipeline(Map<String, Object> pipelineSource) {
        if (pipelineSource == null) {
            return null;
        }

        try {
            List<?> processors = asList(pipelineSource.get("processors"));
            if (processors == null || processors.isEmpty()) {
                log.debug("Pipeline processors list is {} - no embedding processor found", processors == null ? "null" : "empty");
                return null;
            }

            for (Object processorObj : processors) {
                Map<String, Object> processor = asMap(processorObj);
                if (processor == null)
                    continue; // Skip malformed entries

                // Check text_embedding processor
                if (processor.containsKey("text_embedding")) {
                    Map<String, Object> config = asMap(processor.get("text_embedding"));
                    if (config != null) {
                        Object modelId = config.get("model_id");
                        if (modelId instanceof String) {
                            return (String) modelId;
                        }
                        log
                            .warn(
                                "Pipeline text_embedding model_id is not a String: {}",
                                modelId != null ? modelId.getClass().getSimpleName() : "null"
                            );
                    }
                }
                // Check sparse_encoding processor
                if (processor.containsKey("sparse_encoding")) {
                    Map<String, Object> config = asMap(processor.get("sparse_encoding"));
                    if (config != null) {
                        Object modelId = config.get("model_id");
                        if (modelId instanceof String) {
                            return (String) modelId;
                        }
                        log
                            .warn(
                                "Pipeline sparse_encoding model_id is not a String: {}",
                                modelId != null ? modelId.getClass().getSimpleName() : "null"
                            );
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Unexpected error extracting model_id from pipeline: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Compares requested embedding configuration with existing configuration.
     * Throws detailed exception if there's a mismatch.
     *
     * @param requested The requested configuration
     * @param existingModelId The model ID from existing pipeline
     * @param existingConfig The embedding config extracted from existing index mapping
     * @throws IllegalArgumentException if configurations don't match
     */
    public static void compareEmbeddingConfig(MemoryConfiguration requested, String existingModelId, EmbeddingConfig existingConfig) {
        List<String> mismatches = new ArrayList<>();

        // Compare model ID (null-safe)
        String requestedModelId = requested.getEmbeddingModelId();
        if (requestedModelId == null || !requestedModelId.equals(existingModelId)) {
            mismatches
                .add(
                    String
                        .format(
                            "  • embedding_model_id: existing='%s', requested='%s'",
                            existingModelId,
                            requestedModelId != null ? requestedModelId : "null"
                        )
                );
        }

        // Compare model type (null-safe)
        FunctionName requestedType = requested.getEmbeddingModelType();
        FunctionName existingType = existingConfig.getType();
        if (requestedType != existingType) {
            mismatches
                .add(
                    String
                        .format(
                            "  • embedding_model_type: existing='%s', requested='%s'",
                            existingType != null ? existingType : "null",
                            requestedType != null ? requestedType : "null"
                        )
                );
        }

        // Compare dimension (TEXT_EMBEDDING only, null-safe)
        if (requested.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
            Integer requestedDim = requested.getDimension();
            Integer existingDim = existingConfig.getDimension();
            if (requestedDim == null || existingDim == null || !requestedDim.equals(existingDim)) {
                mismatches
                    .add(
                        String
                            .format(
                                "  • dimension: existing=%s, requested=%s",
                                existingDim != null ? existingDim.toString() : "null",
                                requestedDim != null ? requestedDim.toString() : "null"
                            )
                    );
            }
        }

        if (!mismatches.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot create memory container: Embedding configuration conflicts with existing shared index.\n\n"
                    + "Index prefix '"
                    + requested.getIndexPrefix()
                    + "' is already in use with different settings:\n"
                    + String.join("\n", mismatches)
                    + "\n\n"
                    + "This shared index was configured with:\n"
                    + "  embedding_model_id: \""
                    + existingModelId
                    + "\"\n"
                    + "  embedding_model_type: \""
                    + existingConfig.getType()
                    + "\""
                    + (existingConfig.getDimension() != null ? "\n  dimension: " + existingConfig.getDimension() : "")
                    + "\n\n"
                    + "To resolve this issue, you can either:\n"
                    + "1. Use a different index_prefix for this container\n"
                    + "2. Match the existing configuration in your request"
            );
        }
    }
}
