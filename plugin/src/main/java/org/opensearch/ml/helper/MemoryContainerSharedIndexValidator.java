/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import java.util.Map;

import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.ingest.GetPipelineRequest;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for validating shared index compatibility in memory containers.
 * Multiple containers can share the same long-term memory index (via same index_prefix).
 * This validator ensures that the embedding configuration is compatible across all containers
 * sharing an index.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemoryContainerSharedIndexValidator {

    /**
     * Result of shared index validation.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class ValidationResult {
        /** Whether the long-term index already exists */
        private final boolean indexExists;

        /** Whether the requested configuration is compatible with existing index */
        private final boolean compatible;

        /** Model ID extracted from existing pipeline (null if index doesn't exist) */
        private final String existingModelId;

        /** Embedding configuration extracted from existing mapping (null if index doesn't exist) */
        private final MemoryConfiguration.EmbeddingConfig existingConfig;
    }

    /**
     * Validates that the requested embedding configuration is compatible with an existing shared index.
     * <p>
     * This method performs the following validation chain:
     * 1. Check if long-term index exists (getMappings)
     * 2. If exists: extract embedding config from mapping
     * 3. If exists: retrieve and validate ingest pipeline
     * 4. If exists: extract model_id from pipeline
     * 5. Compare requested config with existing config
     * <p>
     * If the index doesn't exist, validation passes (safe to create).
     * If the index exists but configs don't match, validation fails.
     *
     * @param requestedConfig    The memory configuration being requested
     * @param longTermIndexName  The long-term memory index name
     * @param client             The OpenSearch client
     * @param listener           Action listener that receives ValidationResult on success, or error on failure
     */
    public static void validateSharedIndexCompatibility(
        MemoryConfiguration requestedConfig,
        String longTermIndexName,
        Client client,
        ActionListener<ValidationResult> listener
    ) {
        // Skip validation if no strategies (no long-term index will be created)
        if (requestedConfig.getStrategies() == null || requestedConfig.getStrategies().isEmpty()) {
            listener.onResponse(ValidationResult.builder().indexExists(false).compatible(true).build());
            return;
        }

        log.debug("Validating shared index compatibility for: {}", longTermIndexName);

        // Step 1: Check if index exists
        client.admin().indices().getMappings(new GetMappingsRequest().indices(longTermIndexName), ActionListener.wrap(mappingResponse -> {
            // Index exists - proceed with validation
            validateExistingIndex(requestedConfig, longTermIndexName, mappingResponse, client, listener);
        }, error -> {
            // Handle errors during mapping retrieval
            handleMappingError(longTermIndexName, error, listener);
        }));
    }

    /**
     * Validates the existing index by extracting and comparing embedding configuration.
     */
    private static void validateExistingIndex(
        MemoryConfiguration requestedConfig,
        String longTermIndexName,
        GetMappingsResponse mappingResponse,
        Client client,
        ActionListener<ValidationResult> listener
    ) {
        // Step 2: Extract mapping metadata
        MappingMetadata metadata = mappingResponse.getMappings().get(longTermIndexName);
        if (metadata == null) {
            log.error("Index '{}' exists but mapping metadata is null", longTermIndexName);
            listener
                .onFailure(
                    new IllegalStateException(
                        "Cannot validate memory container: Index '"
                            + longTermIndexName
                            + "' exists but mapping is unavailable. "
                            + "This indicates a system issue. Please contact your administrator."
                    )
                );
            return;
        }

        // Step 3: Extract embedding configuration from mapping
        Map<String, Object> mappingMap = metadata.getSourceAsMap();
        Map<String, Object> properties = MemoryConfiguration.asMap(mappingMap.get("properties"));

        if (properties == null) {
            log.error("Index '{}' mapping 'properties' field is null or not a Map", longTermIndexName);
            listener
                .onFailure(
                    new IllegalStateException(
                        "Cannot validate memory container: Index '"
                            + longTermIndexName
                            + "' has malformed mapping structure (properties field is missing or invalid)."
                    )
                );
            return;
        }

        MemoryConfiguration.EmbeddingConfig existingConfig = MemoryConfiguration.extractEmbeddingConfigFromMapping(properties);

        if (existingConfig == null) {
            log
                .error(
                    "Index '{}' exists but has no embedding field in mapping. "
                        + "This indicates a malformed or incompatible index structure.",
                    longTermIndexName
                );
            listener
                .onFailure(
                    new IllegalArgumentException(
                        "Cannot create memory container: Index '"
                            + longTermIndexName
                            + "' already exists "
                            + "but the mapping is malformed or missing embedding configuration. "
                            + "This index may have been created by another process or is corrupted. "
                            + "Please use a different index_prefix to create your memory container."
                    )
                );
            return;
        }

        // Step 4: Validate pipeline configuration
        validatePipeline(requestedConfig, longTermIndexName, existingConfig, client, listener);
    }

    /**
     * Validates the ingest pipeline configuration.
     */
    private static void validatePipeline(
        MemoryConfiguration requestedConfig,
        String longTermIndexName,
        MemoryConfiguration.EmbeddingConfig existingConfig,
        Client client,
        ActionListener<ValidationResult> listener
    ) {
        String pipelineName = longTermIndexName + "-embedding";

        client.admin().cluster().getPipeline(new GetPipelineRequest(pipelineName), ActionListener.wrap(pipelineResponse -> {
            // Step 5: Extract model_id from pipeline
            if (pipelineResponse.pipelines().isEmpty()) {
                log
                    .error(
                        "Index '{}' exists but pipeline '{}' not found. " + "Index and pipeline are out of sync.",
                        longTermIndexName,
                        pipelineName
                    );
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "Cannot create memory container: Index '"
                                + longTermIndexName
                                + "' exists "
                                + "but the required ingest pipeline '"
                                + pipelineName
                                + "' is missing. "
                                + "This indicates an incomplete or corrupted setup. "
                                + "Please use a different index_prefix to create your memory container."
                        )
                    );
                return;
            }

            Map<String, Object> pipelineSource = pipelineResponse.pipelines().get(0).getConfigAsMap();
            String existingModelId = MemoryConfiguration.extractModelIdFromPipeline(pipelineSource);

            if (existingModelId == null) {
                log
                    .error(
                        "Pipeline '{}' exists but has no model_id configured. " + "Pipeline definition: {}",
                        pipelineName,
                        pipelineSource
                    );
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "Cannot create memory container: Ingest pipeline '"
                                + pipelineName
                                + "' exists "
                                + "but is not configured correctly (missing model_id in processor). "
                                + "This indicates a malformed pipeline configuration. "
                                + "Please use a different index_prefix to create your memory container."
                        )
                    );
                return;
            }

            // Step 6: Compare configurations
            compareConfigurations(requestedConfig, longTermIndexName, existingModelId, existingConfig, listener);
        }, error -> { handlePipelineError(pipelineName, error, listener); }));
    }

    /**
     * Compares requested configuration with existing configuration.
     */
    private static void compareConfigurations(
        MemoryConfiguration requestedConfig,
        String longTermIndexName,
        String existingModelId,
        MemoryConfiguration.EmbeddingConfig existingConfig,
        ActionListener<ValidationResult> listener
    ) {
        try {
            MemoryConfiguration.compareEmbeddingConfig(requestedConfig, existingModelId, existingConfig);

            log
                .info(
                    "Validated compatibility with existing shared index '{}' (model: {}, type: {}, dimension: {})",
                    longTermIndexName,
                    existingModelId,
                    existingConfig.getType(),
                    existingConfig.getDimension()
                );

            listener
                .onResponse(
                    ValidationResult
                        .builder()
                        .indexExists(true)
                        .compatible(true)
                        .existingModelId(existingModelId)
                        .existingConfig(existingConfig)
                        .build()
                );
        } catch (IllegalArgumentException e) {
            log
                .error(
                    "Container creation failed: embedding configuration mismatch. "
                        + "Index: {}, Existing: [{}, {}, {}], Requested: [{}, {}, {}]. Error: {}",
                    longTermIndexName,
                    existingModelId,
                    existingConfig.getType(),
                    existingConfig.getDimension(),
                    requestedConfig.getEmbeddingModelId(),
                    requestedConfig.getEmbeddingModelType(),
                    requestedConfig.getDimension(),
                    e.getMessage()
                );

            listener.onFailure(e);
        }
    }

    /**
     * Handles errors during mapping retrieval.
     */
    private static void handleMappingError(String indexName, Exception error, ActionListener<ValidationResult> listener) {
        // Index doesn't exist - safe to create
        if (error instanceof IndexNotFoundException) {
            log.debug("Long-term memory index '{}' does not exist, proceeding with creation", indexName);
            listener.onResponse(ValidationResult.builder().indexExists(false).compatible(true).build());
            return;
        }

        // Real error - log and fail
        log
            .error(
                "Failed to retrieve mapping for index '{}'. Exception type: {}, Message: {}",
                indexName,
                error.getClass().getSimpleName(),
                error.getMessage(),
                error
            );

        listener
            .onFailure(
                new IllegalStateException(
                    "Cannot validate memory container: Failed to retrieve index mapping for '"
                        + indexName
                        + "'. Error: "
                        + error.getMessage()
                )
            );
    }

    /**
     * Handles errors during pipeline retrieval.
     */
    private static void handlePipelineError(String pipelineName, Exception error, ActionListener<ValidationResult> listener) {
        log.error("Failed to retrieve pipeline '{}' for validation: {}", pipelineName, error.getMessage(), error);

        listener
            .onFailure(
                new IllegalStateException(
                    "Cannot validate memory container: Failed to retrieve ingest pipeline '"
                        + pipelineName
                        + "' for validation. Error: "
                        + error.getMessage()
                )
            );
    }
}
