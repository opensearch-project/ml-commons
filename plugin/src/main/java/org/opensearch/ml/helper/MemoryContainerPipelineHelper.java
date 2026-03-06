/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;

import java.io.IOException;

import org.opensearch.action.ingest.GetPipelineRequest;
import org.opensearch.action.ingest.PutPipelineRequest;
import org.opensearch.action.search.GetSearchPipelineAction;
import org.opensearch.action.search.GetSearchPipelineRequest;
import org.opensearch.action.search.PutSearchPipelineAction;
import org.opensearch.action.search.PutSearchPipelineRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for creating and managing ingest pipelines for memory containers.
 * Provides reusable pipeline creation logic for long-term memory indices.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemoryContainerPipelineHelper {

    /**
     * Creates an ingest pipeline and long-term memory index.
     * <p>
     * If embedding is configured, creates a text embedding pipeline first,
     * then creates the long-term index with the pipeline attached.
     * If no embedding is configured, creates the index without a pipeline.
     *
     * @param indexName       The long-term memory index name
     * @param config          The memory configuration
     * @param indicesHandler  The ML indices handler
     * @param client          The OpenSearch client
     * @param listener        Action listener that receives true on success, or error on failure
     */
    public static void createLongTermMemoryIngestPipeline(
        String indexName,
        MemoryConfiguration config,
        MLIndicesHandler indicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        try {
            if (config.getEmbeddingModelType() != null) {
                String pipelineName = indexName + "-embedding";

                createTextEmbeddingPipeline(pipelineName, config, client, ActionListener.wrap(success -> {
                    log.info("Successfully created text embedding pipeline: {}", pipelineName);
                    indicesHandler.createLongTermMemoryIndex(pipelineName, indexName, config, listener);
                }, e -> {
                    log.error("Failed to create text embedding pipeline '{}'", pipelineName, e);
                    listener
                        .onFailure(
                            new org.opensearch.OpenSearchStatusException(
                                "Internal server error",
                                org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                }));
            } else {
                indicesHandler.createLongTermMemoryIndex(null, indexName, config, listener);
            }
        } catch (Exception e) {
            log.error("Failed to create text embedding pipeline for long term memory index: {}", indexName, e);
            listener
                .onFailure(
                    new org.opensearch.OpenSearchStatusException(
                        "Internal server error",
                        org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
        }
    }

    /**
     * Creates a text embedding pipeline for memory container.
     * <p>
     * Checks if the pipeline already exists (shared index scenario) and skips creation if it does.
     * Otherwise, creates a new pipeline with the appropriate embedding processor.
     *
     * @param pipelineName  The pipeline name
     * @param config        The memory configuration
     * @param client        The OpenSearch client
     * @param listener      Action listener that receives true on success, or error on failure
     */
    public static void createTextEmbeddingPipeline(
        String pipelineName,
        MemoryConfiguration config,
        Client client,
        ActionListener<Boolean> listener
    ) {
        // Check if pipeline already exists (shared index scenario)
        client.admin().cluster().getPipeline(new GetPipelineRequest(pipelineName), ActionListener.wrap(response -> {
            if (!response.pipelines().isEmpty()) {
                // Pipeline exists - skip creation
                log.info("Pipeline '{}' already exists (shared index scenario), skipping creation", pipelineName);
                listener.onResponse(true);
                return;
            }

            // Pipeline doesn't exist - create it
            try {
                createPipelineInternal(pipelineName, config, client, listener);
            } catch (IOException e) {
                log.error("Failed to build pipeline configuration for '{}'", pipelineName, e);
                listener
                    .onFailure(
                        new org.opensearch.OpenSearchStatusException(
                            "Internal server error",
                            org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
            }
        }, error -> {
            // Pipeline doesn't exist (404 error expected) - create it
            try {
                createPipelineInternal(pipelineName, config, client, listener);
            } catch (IOException e) {
                log.error("Failed to build pipeline configuration for '{}'", pipelineName, e);
                listener
                    .onFailure(
                        new org.opensearch.OpenSearchStatusException(
                            "Internal server error",
                            org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
            }
        }));
    }

    /**
     * Actually creates the ingest pipeline with embedding processor.
     *
     * @param pipelineName  The pipeline name
     * @param config        The memory configuration
     * @param client        The OpenSearch client
     * @param listener      Action listener that receives true on success, or error on failure
     * @throws IOException if XContentBuilder fails
     */
    private static void createPipelineInternal(
        String pipelineName,
        MemoryConfiguration config,
        Client client,
        ActionListener<Boolean> listener
    ) throws IOException {
        String processorName = config.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING ? "text_embedding" : "sparse_encoding";

        XContentBuilder builder = XContentFactory
            .jsonBuilder()
            .startObject()
            .field("description", "Agentic Memory Text embedding pipeline")
            .startArray("processors")
            .startObject()
            .startObject(processorName)
            .field("model_id", config.getEmbeddingModelId())
            .startObject("field_map")
            .field(MEMORY_FIELD, MEMORY_EMBEDDING_FIELD)
            .endObject()
            .endObject()
            .endObject()
            .endArray()
            .endObject();

        PutPipelineRequest putRequest = new PutPipelineRequest(pipelineName, BytesReference.bytes(builder), XContentType.JSON);

        client.admin().cluster().putPipeline(putRequest, ActionListener.wrap(response -> {
            if (response.isAcknowledged()) {
                log.info("Successfully created pipeline: {}", pipelineName);
                listener.onResponse(true);
            } else {
                log.error("Pipeline creation not acknowledged: {}", pipelineName);
                listener
                    .onFailure(
                        new org.opensearch.OpenSearchStatusException(
                            "Internal server error",
                            org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
            }
        }, e -> {
            log.error("Failed to create pipeline '{}'", pipelineName, e);
            listener
                .onFailure(
                    new org.opensearch.OpenSearchStatusException(
                        "Internal server error",
                        org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
        }));
    }

    /**
     * Creates history index only if it's enabled in the configuration.
     * <p>
     * Checks the disableHistory flag and creates the history index if it's not disabled.
     *
     * @param config             The memory configuration
     * @param historyIndexName   The history index name
     * @param indicesHandler     The ML indices handler
     * @param listener           Action listener that receives true on success, or error on failure
     */
    public static void createHistoryIndexIfEnabled(
        MemoryConfiguration config,
        String historyIndexName,
        MLIndicesHandler indicesHandler,
        ActionListener<Boolean> listener
    ) {
        if (!config.isDisableHistory()) {
            log.debug("Creating history index: {}", historyIndexName);
            indicesHandler.createLongTermMemoryHistoryIndex(historyIndexName, config, listener);
        } else {
            log.debug("History index disabled, skipping creation");
            listener.onResponse(true);
        }
    }

    /**
     * Creates a hybrid search pipeline for the memory container.
     * Uses normalization-processor with min_max normalization and arithmetic_mean combination (50/50 weights).
     * Only created when embedding model is configured.
     *
     * @param longTermIndexName The long-term memory index name (used to derive pipeline name)
     * @param client            The OpenSearch client
     * @param listener          Action listener that receives true on success, or error on failure
     */
    public static void createHybridSearchPipeline(String longTermIndexName, Client client, ActionListener<Boolean> listener) {
        String pipelineName = longTermIndexName + "-hybrid-search";

        // Check if pipeline already exists (shared index scenario)
        client.execute(GetSearchPipelineAction.INSTANCE, new GetSearchPipelineRequest(pipelineName), ActionListener.wrap(response -> {
            if (!response.pipelines().isEmpty()) {
                log.info("Hybrid search pipeline '{}' already exists, skipping creation", pipelineName);
                listener.onResponse(true);
                return;
            }
            createHybridSearchPipelineInternal(pipelineName, client, listener);
        }, error -> {
            // Only create on NOT_FOUND; for other errors (permission, cluster issues) log and continue
            // Pipeline creation is best-effort — container creation should not fail if neural-search
            // plugin is not installed (normalization-processor may be unavailable)
            if (error instanceof org.opensearch.OpenSearchStatusException
                && ((org.opensearch.OpenSearchStatusException) error).status() == org.opensearch.core.rest.RestStatus.NOT_FOUND) {
                createHybridSearchPipelineInternal(pipelineName, client, listener);
            } else {
                log
                    .warn(
                        "Could not check hybrid search pipeline '{}', skipping creation. "
                            + "Hybrid search may not work if neural-search plugin is not installed. Error: {}",
                        pipelineName,
                        error.getMessage()
                    );
                listener.onResponse(true); // best-effort: don't fail container creation
            }
        }));
    }

    private static void createHybridSearchPipelineInternal(String pipelineName, Client client, ActionListener<Boolean> listener) {
        try {
            XContentBuilder builder = XContentFactory
                .jsonBuilder()
                .startObject()
                .field("description", "Hybrid search pipeline for agentic memory")
                .startArray("phase_results_processors")
                .startObject()
                .startObject("normalization-processor")
                .startObject("normalization")
                .field("technique", "min_max")
                .endObject()
                .startObject("combination")
                .field("technique", "arithmetic_mean")
                .startObject("parameters")
                .field("weights", new float[] { 0.5f, 0.5f })
                .endObject()
                .endObject()
                .endObject()
                .endObject()
                .endArray()
                .endObject();

            PutSearchPipelineRequest putRequest = new PutSearchPipelineRequest(
                pipelineName,
                BytesReference.bytes(builder),
                XContentType.JSON
            );

            client.execute(PutSearchPipelineAction.INSTANCE, putRequest, ActionListener.wrap(response -> {
                if (response.isAcknowledged()) {
                    log.info("Successfully created hybrid search pipeline: {}", pipelineName);
                    listener.onResponse(true);
                } else {
                    log.warn("Hybrid search pipeline creation not acknowledged: {}, skipping", pipelineName);
                    listener.onResponse(true); // best-effort
                }
            }, e -> {
                // Best-effort: log warning but don't fail container creation
                // normalization-processor may not be available if neural-search plugin is not installed
                log
                    .warn(
                        "Could not create hybrid search pipeline '{}', skipping. " + "Hybrid search may not work. Error: {}",
                        pipelineName,
                        e.getMessage()
                    );
                listener.onResponse(true);
            }));
        } catch (IOException e) {
            log.warn("Failed to build hybrid search pipeline configuration for '{}', skipping. Error: {}", pipelineName, e.getMessage());
            listener.onResponse(true); // best-effort
        }
    }
}
