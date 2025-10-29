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
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.RemoteStore;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Helper class for creating and managing ingest pipelines for memory containers.
 * Provides reusable pipeline creation logic for long-term memory indices.
 */
@Log4j2
public class MemoryContainerPipelineHelper {

    private final Client client;
    private final MLIndicesHandler mlIndicesHandler;
    private final RemoteMemoryStoreHelper remoteMemoryStoreHelper;

    public MemoryContainerPipelineHelper(
        Client client,
        MLIndicesHandler mlIndicesHandler,
        RemoteMemoryStoreHelper remoteMemoryStoreHelper
    ) {
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.remoteMemoryStoreHelper = remoteMemoryStoreHelper;
    }

    /**
     * Creates an ingest pipeline and long-term memory index.
     * <p>
     * If a pre-existing ingest pipeline is configured, uses that pipeline directly.
     * Otherwise, if embedding is configured, creates a text embedding pipeline first,
     * then creates the long-term index with the pipeline attached.
     * If no embedding is configured, creates the index without a pipeline.
     *
     * @param indexName       The long-term memory index name
     * @param config          The memory configuration
     * @param listener        Action listener that receives true on success, or error on failure
     */
    public void createLongTermMemoryIngestPipeline(String indexName, MemoryConfiguration config, ActionListener<Boolean> listener) {
        try {
            // Check if user provided a pre-existing ingest pipeline at configuration level
            if (config.getIngestPipeline() != null && !config.getIngestPipeline().isEmpty()) {
                log.info("Using pre-existing ingest pipeline from configuration: {}", config.getIngestPipeline());
                // Use the user-provided pipeline directly
                mlIndicesHandler.createLongTermMemoryIndex(config.getIngestPipeline(), indexName, config, listener);
            } else if (config.getEmbeddingModelType() != null) {
                // Auto-create pipeline if embedding model is configured
                String pipelineName = indexName + "-embedding";

                createTextEmbeddingPipeline(pipelineName, config, ActionListener.wrap(success -> {
                    log.info("Successfully created text embedding pipeline: {}", pipelineName);
                    mlIndicesHandler.createLongTermMemoryIndex(pipelineName, indexName, config, listener);
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
                mlIndicesHandler.createLongTermMemoryIndex(null, indexName, config, listener);
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
     * @param listener      Action listener that receives true on success, or error on failure
     */
    public void createTextEmbeddingPipeline(String pipelineName, MemoryConfiguration config, ActionListener<Boolean> listener) {
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
                createPipelineInternal(pipelineName, config, listener);
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
                createPipelineInternal(pipelineName, config, listener);
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
     * @param listener      Action listener that receives true on success, or error on failure
     * @throws IOException if XContentBuilder fails
     */
    private void createPipelineInternal(String pipelineName, MemoryConfiguration config, ActionListener<Boolean> listener)
        throws IOException {
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
     * @param listener           Action listener that receives true on success, or error on failure
     */
    public void createHistoryIndexIfEnabled(MemoryConfiguration config, String historyIndexName, ActionListener<Boolean> listener) {
        if (!config.isDisableHistory()) {
            log.debug("Creating history index: {}", historyIndexName);
            mlIndicesHandler.createLongTermMemoryHistoryIndex(historyIndexName, config, listener);
        } else {
            log.debug("History index disabled, skipping creation");
            listener.onResponse(true);
        }
    }

    /**
     * Creates an ingest pipeline in remote storage and long-term memory index.
     * <p>
     * If a pre-existing ingest pipeline is configured in remote_store, uses that pipeline directly.
     * Otherwise, if embedding is configured, creates a text embedding pipeline in the remote cluster first,
     * then creates the long-term index with the pipeline attached.
     * If no embedding is configured, creates the index without a pipeline.
     *
     * @param indexName       The long-term memory index name
     * @param config          The memory configuration
     * @param listener        Action listener that receives true on success, or error on failure
     */
    public void createRemoteLongTermMemoryIngestPipeline(String indexName, MemoryConfiguration config, ActionListener<Boolean> listener) {
        try {
            RemoteStore remoteStore = config.getRemoteStore();
            Connector connector = remoteStore.getConnector();
            if (connector != null) {
                remoteMemoryStoreHelper
                    .createRemoteLongTermMemoryIndexWithIngestAndSearchPipeline(
                        connector,
                        indexName,
                        remoteStore.getIngestPipeline(),
                        remoteStore.getSearchPipeline(),
                        config,
                        listener
                    );
                return;
            }

            String connectorId = remoteStore.getConnectorId();
            // Check if user provided a pre-existing ingest pipeline in remote_store
            if (remoteStore.getIngestPipeline() != null && !remoteStore.getIngestPipeline().isEmpty()) {
                log.info("Using pre-existing ingest pipeline from remote_store: {}", remoteStore.getIngestPipeline());
                // Use the user-provided pipeline directly
                remoteMemoryStoreHelper
                    .createRemoteLongTermMemoryIndexWithIngestAndSearchPipeline(
                        connectorId,
                        indexName,
                        remoteStore.getIngestPipeline(),
                        remoteStore.getSearchPipeline(),
                        config,
                        listener
                    );
            } else if (remoteStore.getEmbeddingModelType() != null) {
                // Auto-create pipeline if embedding model is configured
                String pipelineName = indexName + "-embedding";

                createRemoteTextEmbeddingPipeline(connectorId, pipelineName, config, ActionListener.wrap(success -> {
                    log.info("Successfully created remote text embedding pipeline: {}", pipelineName);
                    // Now create the remote long-term memory index with the pipeline
                    remoteMemoryStoreHelper
                        .createRemoteLongTermMemoryIndexWithPipeline(connectorId, indexName, pipelineName, config, listener);
                }, e -> {
                    log.error("Failed to create remote text embedding pipeline '{}'", pipelineName, e);
                    listener.onFailure(e);
                }));
            } else {
                // No embedding configured, create index without pipeline
                remoteMemoryStoreHelper.createRemoteLongTermMemoryIndex(connectorId, indexName, config, listener);
            }
        } catch (Exception e) {
            log.error("Failed to create remote long-term memory infrastructure for index: {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Creates a text embedding pipeline in remote storage for memory container.
     * <p>
     * Creates a pipeline with the appropriate embedding processor in the remote cluster.
     * Uses the remote embedding model ID if specified in remote_store configuration,
     * otherwise falls back to the local embedding model ID.
     *
     * @param connectorId   The connector ID for remote storage
     * @param pipelineName  The pipeline name
     * @param config        The memory configuration
     * @param listener      Action listener that receives true on success, or error on failure
     */
    public void createRemoteTextEmbeddingPipeline(
        String connectorId,
        String pipelineName,
        MemoryConfiguration config,
        ActionListener<Boolean> listener
    ) {
        try {
            RemoteStore remoteStore = config.getRemoteStore();
            String processorName = remoteStore.getEmbeddingModelType() == org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING
                ? "text_embedding"
                : "sparse_encoding";

            String embeddingModelId = remoteStore.getEmbeddingModelId();

            XContentBuilder builder = XContentFactory
                .jsonBuilder()
                .startObject()
                .field("description", "Agentic Memory Text embedding pipeline")
                .startArray("processors")
                .startObject()
                .startObject(processorName)
                .field("model_id", embeddingModelId)
                .startObject("field_map")
                .field(MEMORY_FIELD, MEMORY_EMBEDDING_FIELD)
                .endObject()
                .endObject()
                .endObject()
                .endArray()
                .endObject();

            String pipelineBody = builder.toString();

            // Use RemoteStorageHelper to create the pipeline in remote storage
            remoteMemoryStoreHelper.createRemotePipeline(connectorId, pipelineName, pipelineBody, listener);
        } catch (IOException e) {
            log.error("Failed to build remote pipeline configuration for '{}'", pipelineName, e);
            listener.onFailure(e);
        }
    }
}
