/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.CommonValue.CONNECTOR_ACTION_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_SESSION_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_WORKING_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_HISTORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.WORKING_MEMORY_INDEX;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.RemoteStore;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorAction;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Helper class for creating memory indices in remote storage using connectors
 */
@Log4j2
public class RemoteStorageHelper {

    private static final String CREATE_INGEST_PIPELINE_ACTION = "create_ingest_pipeline";
    private static final String CREATE_INDEX_ACTION = "create_index";
    private static final String WRITE_DOC_ACTION = "write_doc";
    private static final String BULK_LOAD_ACTION = "bulk_load";
    private static final String SEARCH_INDEX_ACTION = "search_index";
    private static final String UPDATE_DOC_ACTION = "update_doc";
    private static final String GET_DOC_ACTION = "get_doc";
    private static final String DELETE_DOC_ACTION = "delete_doc";
    private static final String INDEX_NAME_PARAM = "index_name";
    private static final String DOC_ID_PARAM = "doc_id";
    private static final String INPUT_PARAM = "input";

    /**
     * Creates a memory index in remote storage using a connector
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index to create
     * @param indexMapping The index mapping as a JSON string
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void createRemoteIndex(
        String connectorId,
        String indexName,
        String indexMapping,
        Client client,
        ActionListener<Boolean> listener
    ) {
        createRemoteIndex(connectorId, indexName, indexMapping, null, client, listener);
    }

    /**
     * Creates a memory index in remote storage using a connector with custom settings
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index to create
     * @param indexMapping The index mapping as a JSON string
     * @param indexSettings The index settings as a Map (can be null)
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void createRemoteIndex(
        String connectorId,
        String indexName,
        String indexMapping,
        Map<String, Object> indexSettings,
        Client client,
        ActionListener<Boolean> listener
    ) {
        try {
            // Parse the mapping string to a Map
            Map<String, Object> mappingMap = parseMappingToMap(indexMapping);

            // Build the request body for creating the index
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mappings", mappingMap);

            // Add settings if provided (settings should already have "index." prefix)
            if (indexSettings != null && !indexSettings.isEmpty()) {
                requestBody.put("settings", indexSettings);
            }

            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(INPUT_PARAM, StringUtils.toJson(requestBody));
            parameters.put(CONNECTOR_ACTION_FIELD, CREATE_INDEX_ACTION);

            // Execute the connector action
            executeConnectorAction(connectorId, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully created remote index: {}", indexName);
                listener.onResponse(true);
            }, e -> {
                log.error("Failed to create remote index: {}", indexName, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote index creation for: {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Creates session memory index in remote storage
     */
    public static void createRemoteSessionMemoryIndex(
        String connectorId,
        String indexName,
        MemoryConfiguration configuration,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        String indexMappings = mlIndicesHandler.getMapping(ML_MEMORY_SESSION_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(SESSION_INDEX);
        createRemoteIndex(connectorId, indexName, indexMappings, indexSettings, client, listener);
    }

    /**
     * Creates working memory index in remote storage
     */
    public static void createRemoteWorkingMemoryIndex(
        String connectorId,
        String indexName,
        MemoryConfiguration configuration,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        String indexMappings = mlIndicesHandler.getMapping(ML_WORKING_MEMORY_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(WORKING_MEMORY_INDEX);
        createRemoteIndex(connectorId, indexName, indexMappings, indexSettings, client, listener);
    }

    /**
     * Creates long-term memory history index in remote storage
     */
    public static void createRemoteLongTermMemoryHistoryIndex(
        String connectorId,
        String indexName,
        MemoryConfiguration configuration,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        String indexMappings = mlIndicesHandler.getMapping(ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(LONG_TERM_MEMORY_HISTORY_INDEX);
        createRemoteIndex(connectorId, indexName, indexMappings, indexSettings, client, listener);
    }

    /**
     * Creates long-term memory index in remote storage with dynamic embedding configuration
     */
    public static void createRemoteLongTermMemoryIndex(
        String connectorId,
        String indexName,
        MemoryConfiguration memoryConfig,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        try {
            String indexMapping = buildLongTermMemoryMapping(memoryConfig, mlIndicesHandler);
            Map<String, Object> indexSettings = buildLongTermMemorySettings(memoryConfig);
            createRemoteIndex(connectorId, indexName, indexMapping, indexSettings, client, listener);
        } catch (Exception e) {
            log.error("Failed to build long-term memory mapping for remote index: {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Builds the long-term memory index mapping dynamically based on configuration
     */
    private static String buildLongTermMemoryMapping(MemoryConfiguration memoryConfig, MLIndicesHandler mlIndicesHandler)
        throws IOException {
        String baseMappingJson = mlIndicesHandler.getMapping(ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH);

        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        XContentParser parser = XContentHelper
            .createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                new BytesArray(baseMappingJson),
                XContentType.JSON
            );

        Map<String, Object> baseMapping = parser.mapOrdered();
        mapping.put("_meta", baseMapping.get("_meta"));
        properties.putAll((Map<String, Object>) baseMapping.get("properties"));

        RemoteStore remoteStore = memoryConfig.getRemoteStore();
        // Add embedding field based on configuration
        if (remoteStore.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
            Map<String, Object> knnVector = new HashMap<>();
            knnVector.put("type", "knn_vector");
            knnVector.put("dimension", remoteStore.getEmbeddingDimension());
            properties.put(MEMORY_EMBEDDING_FIELD, knnVector);
        } else if (remoteStore.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
            properties.put(MEMORY_EMBEDDING_FIELD, Map.of("type", "rank_features"));
        }

        mapping.put("properties", properties);
        return StringUtils.toJson(mapping);
    }

    /**
     * Builds the long-term memory index settings dynamically based on configuration
     * Returns settings with "index." prefix as required by OpenSearch/AOSS
     */
    private static Map<String, Object> buildLongTermMemorySettings(MemoryConfiguration memoryConfig) {
        Map<String, Object> indexSettings = new HashMap<>();

        RemoteStore remoteStore = memoryConfig.getRemoteStore();
        if (remoteStore.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
            indexSettings.put("index.knn", true);
        }

        // Add custom settings from configuration
        if (!memoryConfig.getIndexSettings().isEmpty() && memoryConfig.getIndexSettings().containsKey(LONG_TERM_MEMORY_INDEX)) {
            Map<String, Object> configuredIndexSettings = memoryConfig.getMemoryIndexMapping(LONG_TERM_MEMORY_INDEX);
            indexSettings.putAll(configuredIndexSettings);
        }

        return indexSettings;
    }

    /**
     * Executes a connector action with a specific action name
     */
    private static void executeConnectorAction(
        String connectorId,
        String actionName,
        Map<String, String> parameters,
        Client client,
        ActionListener<ModelTensorOutput> listener
    ) {
        // Add connector_action parameter to specify which action to execute
        Map<String, String> allParameters = new HashMap<>(parameters);
        allParameters.put(CONNECTOR_ACTION_FIELD, actionName);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(allParameters).build();
        MLInput mlInput = RemoteInferenceMLInput.builder().algorithm(FunctionName.CONNECTOR).inputDataset(inputDataSet).build();
        MLExecuteConnectorRequest request = new MLExecuteConnectorRequest(connectorId, mlInput);

        client.execute(MLExecuteConnectorAction.INSTANCE, request, ActionListener.wrap(r -> {
            ModelTensorOutput output = (ModelTensorOutput) r.getOutput();
            listener.onResponse(output);
        }, e -> {
            log.error("Failed to execute connector action {} for connector: {}", actionName, connectorId, e);
            listener.onFailure(e);
        }));
    }

    /**
     * Executes a connector action (backward compatibility - defaults to create_index)
     */
    private static void executeConnectorAction(
        String connectorId,
        Map<String, String> parameters,
        Client client,
        ActionListener<ModelTensorOutput> listener
    ) {
        executeConnectorAction(connectorId, CREATE_INDEX_ACTION, parameters, client, listener);
    }

    /**
     * Writes a single document to remote storage
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index
     * @param documentSource The document source as a Map
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void writeDocument(
        String connectorId,
        String indexName,
        Map<String, Object> documentSource,
        Client client,
        ActionListener<IndexResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(INPUT_PARAM, StringUtils.toJsonWithPlainNumbers(documentSource));

            // Execute the connector action with write_doc action name
            executeConnectorAction(connectorId, WRITE_DOC_ACTION, parameters, client, ActionListener.wrap(response -> {
                // Extract document ID from response
                XContentParser parser = createParserFromTensorOutput(response);
                IndexResponse indexResponse = IndexResponse.fromXContent(parser);
                listener.onResponse(indexResponse);
            }, e -> {
                log.error("Failed to write document to remote index: {}", indexName, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote document write for index: {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Performs bulk write operations to remote storage
     *
     * @param connectorId The connector ID to use for remote storage
     * @param bulkBody The bulk request body in NDJSON format
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void bulkWrite(String connectorId, String bulkBody, Client client, ActionListener<BulkResponse> listener) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INPUT_PARAM, bulkBody);
            parameters.put(NO_ESCAPE_PARAMS, INPUT_PARAM);

            // Execute the connector action with bulk_load action name
            executeConnectorAction(connectorId, BULK_LOAD_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully executed bulk write to remote storage");
                XContentParser parser = createParserFromTensorOutput(response);
                BulkResponse bulkResponse = BulkResponse.fromXContent(parser);
                listener.onResponse(bulkResponse);
            }, e -> {
                log.error("Failed to execute bulk write to remote storage", e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote bulk write", e);
            listener.onFailure(e);
        }
    }

    public static void searchDocuments(
        String connectorId,
        String indexName,
        String query,
        Client client,
        ActionListener<SearchResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(INPUT_PARAM, query);

            // Execute the connector action with search_index action name
            executeConnectorAction(connectorId, SEARCH_INDEX_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully searched documents in remote index: {}", indexName);
                XContentParser parser = createParserFromTensorOutput(response);
                SearchResponse searchResponse = SearchResponse.fromXContent(parser);
                listener.onResponse(searchResponse);
            }, e -> {
                log.error("Failed to search documents in remote index: {}", indexName, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote search for index: {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Updates a document in remote storage
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index
     * @param docId The document ID to update
     * @param documentSource The document source as a Map
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void updateDocument(
        String connectorId,
        String indexName,
        String docId,
        Map<String, Object> documentSource,
        Client client,
        ActionListener<UpdateResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(DOC_ID_PARAM, docId);
            parameters.put(INPUT_PARAM, StringUtils.toJsonWithPlainNumbers(documentSource));

            // Execute the connector action with update_doc action name
            executeConnectorAction(connectorId, UPDATE_DOC_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully updated document in remote index: {}, doc_id: {}", indexName, docId);
                XContentParser parser = createParserFromTensorOutput(response);
                UpdateResponse updateResponse = UpdateResponse.fromXContent(parser);
                listener.onResponse(updateResponse);
            }, e -> {
                log.error("Failed to update document in remote index: {}, doc_id: {}", indexName, docId, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote document update for index: {}, doc_id: {}", indexName, docId, e);
            listener.onFailure(e);
        }
    }

    public static void getDocument(
        String connectorId,
        String indexName,
        String docId,
        Client client,
        ActionListener<GetResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(DOC_ID_PARAM, docId);
            // input parameter is optional for delete, use empty string as default
            parameters.put(INPUT_PARAM, "");

            // Execute the connector action with delete_doc action name
            executeConnectorAction(connectorId, GET_DOC_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully deleted document from remote index: {}, doc_id: {}", indexName, docId);
                XContentParser parser = createParserFromTensorOutput(response);
                GetResponse getResponse = GetResponse.fromXContent(parser);
                listener.onResponse(getResponse);
            }, e -> {
                log.error("Failed to delete document from remote index: {}, doc_id: {}", indexName, docId, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote document delete for index: {}, doc_id: {}", indexName, docId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Deletes a document from remote storage
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index
     * @param docId The document ID to delete
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void deleteDocument(
        String connectorId,
        String indexName,
        String docId,
        Client client,
        ActionListener<DeleteResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(DOC_ID_PARAM, docId);
            // input parameter is optional for delete, use empty string as default
            parameters.put(INPUT_PARAM, "");

            // Execute the connector action with delete_doc action name
            executeConnectorAction(connectorId, DELETE_DOC_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully deleted document from remote index: {}, doc_id: {}", indexName, docId);
                XContentParser parser = createParserFromTensorOutput(response);
                DeleteResponse deleteResponse = DeleteResponse.fromXContent(parser);
                listener.onResponse(deleteResponse);
            }, e -> {
                log.error("Failed to delete document from remote index: {}, doc_id: {}", indexName, docId, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote document delete for index: {}, doc_id: {}", indexName, docId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Parses a JSON mapping string to a Map
     */
    private static Map<String, Object> parseMappingToMap(String mappingJson) throws IOException {
        XContentParser parser = XContentHelper
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, new BytesArray(mappingJson), XContentType.JSON);
        return parser.mapOrdered();
    }

    public static XContentParser createParserFromTensorOutput(ModelTensorOutput output) throws IOException {
        Map<String, ?> dataAsMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String json = StringUtils.toJson(dataAsMap);
        XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        return parser;
    }

    public static QueryBuilder buildFactSearchQuery(
        MemoryStrategy strategy,
        String fact,
        Map<String, String> namespace,
        String ownerId,
        MemoryConfiguration memoryConfig,
        String memoryContainerId
    ) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Add filter conditions
        for (String key : strategy.getNamespace()) {
            if (!namespace.containsKey(key)) {
                throw new IllegalArgumentException("Namespace does not contain key: " + key);
            }
            boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_FIELD + "." + key, namespace.get(key)));
        }
        if (ownerId != null) {
            boolQuery.filter(QueryBuilders.termQuery(OWNER_ID_FIELD, ownerId));
        }
        boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_SIZE_FIELD, strategy.getNamespace().size()));
        // Filter by strategy_id to prevent cross-strategy interference (sufficient for uniqueness)
        boolQuery.filter(QueryBuilders.termQuery(STRATEGY_ID_FIELD, strategy.getId()));
        // Filter by memory_container_id to prevent cross-container access when containers share the same index prefix
        if (memoryContainerId != null && !memoryContainerId.isBlank()) {
            boolQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, memoryContainerId));
        }

        // Add the search query
        if (memoryConfig != null) {
            if (memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                StringBuilder neuralSearchQuery = new StringBuilder()
                    .append("{\"neural\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(memoryConfig.getEmbeddingModelId())
                    .append("\"}}}");
                boolQuery.must(QueryBuilders.wrapperQuery(neuralSearchQuery.toString()));
            } else if (memoryConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                StringBuilder neuralSparseQuery = new StringBuilder()
                    .append("{\"neural_sparse\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(memoryConfig.getEmbeddingModelId())
                    .append("\"}}}");
                boolQuery.must(QueryBuilders.wrapperQuery(neuralSparseQuery.toString()));
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + memoryConfig.getEmbeddingModelType());
            }
        } else {
            boolQuery.must(QueryBuilders.matchQuery(MEMORY_FIELD, fact));
        }

        return boolQuery;
    }

    /**
     * Creates an ingest pipeline in remote storage
     *
     * @param connectorId The connector ID to use for remote storage
     * @param pipelineName The name of the pipeline to create
     * @param pipelineBody The pipeline configuration as a JSON string
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void createRemotePipeline(
        String connectorId,
        String pipelineName,
        String pipelineBody,
        Client client,
        ActionListener<Boolean> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put("pipeline_name", pipelineName);
            parameters.put(INPUT_PARAM, pipelineBody);
            parameters.put(CONNECTOR_ACTION_FIELD, "create_ingest_pipeline");

            // Execute the connector action
            executeConnectorAction(connectorId, CREATE_INGEST_PIPELINE_ACTION, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully created remote pipeline: {}", pipelineName);
                listener.onResponse(true);
            }, e -> {
                log.error("Failed to create remote pipeline: {}", pipelineName, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote pipeline creation for: {}", pipelineName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Creates long-term memory index in remote storage with a pipeline attached
     *
     * @param connectorId The connector ID to use for remote storage
     * @param indexName The name of the index to create
     * @param pipelineName The name of the pipeline to attach
     * @param memoryConfig The memory configuration
     * @param mlIndicesHandler The ML indices handler
     * @param client The OpenSearch client
     * @param listener The action listener
     */
    public static void createRemoteLongTermMemoryIndexWithPipeline(
        String connectorId,
        String indexName,
        String pipelineName,
        MemoryConfiguration memoryConfig,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ActionListener<Boolean> listener
    ) {
        try {
            String indexMapping = buildLongTermMemoryMapping(memoryConfig, mlIndicesHandler);
            Map<String, Object> indexSettings = buildLongTermMemorySettings(memoryConfig);

            // Parse the mapping string to a Map
            Map<String, Object> mappingMap = parseMappingToMap(indexMapping);

            // Build the request body for creating the index with pipeline
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mappings", mappingMap);

            // Add settings with default pipeline (settings already have "index." prefix)
            Map<String, Object> settings = new HashMap<>(indexSettings);
            settings.put("index.default_pipeline", pipelineName);
            requestBody.put("settings", settings);

            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            parameters.put(INPUT_PARAM, StringUtils.toJson(requestBody));
            parameters.put(CONNECTOR_ACTION_FIELD, CREATE_INDEX_ACTION);

            // Execute the connector action
            executeConnectorAction(connectorId, parameters, client, ActionListener.wrap(response -> {
                log.info("Successfully created remote long-term memory index with pipeline: {}", indexName);
                listener.onResponse(true);
            }, e -> {
                log.error("Failed to create remote long-term memory index with pipeline: {}", indexName, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error preparing remote long-term memory index creation with pipeline for: {}", indexName, e);
            listener.onFailure(e);
        }
    }
}
