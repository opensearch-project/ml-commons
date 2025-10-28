/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.CommonValue.CONNECTOR_ACTION_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_SESSION_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_WORKING_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_HISTORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEARCH_PIPELINE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.WORKING_MEMORY_INDEX;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.SKIP_VALIDATE_MISSING_PARAMETERS;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.opensearch.ml.engine.algorithms.remote.ConnectorUtils;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Helper class for creating memory indices in remote storage using connectors
 */
@Log4j2
public class RemoteStorageHelper {

    public static final String REGISTER_MODEL_ACTION = "register_model";
    public static final String CREATE_INGEST_PIPELINE_ACTION = "create_ingest_pipeline";
    public static final String CREATE_INDEX_ACTION = "create_index";
    public static final String WRITE_DOC_ACTION = "write_doc";
    public static final String BULK_LOAD_ACTION = "bulk_load";
    public static final String SEARCH_INDEX_ACTION = "search_index";
    public static final String GET_DOC_ACTION = "get_doc";
    public static final String DELETE_DOC_ACTION = "delete_doc";
    public static final String UPDATE_DOC_ACTION = "update_doc";
    public static final String INDEX_NAME_PARAM = "index_name";
    public static final String DOC_ID_PARAM = "doc_id";
    public static final String INPUT_PARAM = "input";
    public static final String HEADERS_FIELD = "headers";
    public static final String ACTIONS_FIELD = "actions";

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
        String searchPipeline,
        Client client,
        ActionListener<SearchResponse> listener
    ) {
        try {
            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INDEX_NAME_PARAM, indexName);
            if (searchPipeline != null) {
                parameters.put(SEARCH_PIPELINE_FIELD, "?search_pipeline=" + searchPipeline);
            }
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
                    .append(escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(memoryConfig.getEmbeddingModelId())
                    .append("\"}}}");
                boolQuery.must(QueryBuilders.wrapperQuery(neuralSearchQuery.toString()));
            } else if (memoryConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                StringBuilder neuralSparseQuery = new StringBuilder()
                    .append("{\"neural_sparse\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(escapeJson(fact))
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

    public static void createRemoteLongTermMemoryIndexWithIngestAndSearchPipeline(
        String connectorId,
        String indexName,
        String ingestPipelineName,
        String searchPipelineName,
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
            settings.put("index.default_pipeline", ingestPipelineName);
            if (searchPipelineName != null) {
                settings.put("index.search.default_pipeline", searchPipelineName);
            }
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

    /**
     * Creates an embedding model in remote AOSS collection
     *
     * @param connectorId The connector ID to use for remote storage
     * @param embeddingModel The embedding model configuration
     * @param remoteStoreCredential The remote store credentials (used if embedding model doesn't have its own)
     * @param client The OpenSearch client
     * @param listener The action listener that receives the created model ID
     */
    public static void createRemoteEmbeddingModel(
        String connectorId,
        org.opensearch.ml.common.memorycontainer.RemoteEmbeddingModel embeddingModel,
        Map<String, String> remoteStoreCredential,
        Client client,
        ActionListener<String> listener
    ) {
        try {
            // Build model registration request body
            String requestBody = buildEmbeddingModelRegistrationBody(embeddingModel, remoteStoreCredential);

            // Prepare parameters for connector execution
            Map<String, String> parameters = new HashMap<>();
            parameters.put(INPUT_PARAM, requestBody);
            parameters.put(NO_ESCAPE_PARAMS, INPUT_PARAM);
            parameters.put(SKIP_VALIDATE_MISSING_PARAMETERS, "true");

            // Execute the connector action with register_model action name
            executeConnectorAction(connectorId, REGISTER_MODEL_ACTION, parameters, client, ActionListener.wrap(response -> {
                // Parse model_id from response
                String modelId = extractModelIdFromResponse(response);
                log.info("Successfully created embedding model in remote store: {}", modelId);
                listener.onResponse(modelId);
            }, e -> {
                log.error("Failed to create embedding model in remote store", e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Error building embedding model registration request", e);
            listener.onFailure(e);
        }
    }

    /**
     * Builds the request body for embedding model registration in remote AOSS
     */
    private static String buildEmbeddingModelRegistrationBody(
        org.opensearch.ml.common.memorycontainer.RemoteEmbeddingModel embeddingModel,
        Map<String, String> remoteStoreCredential
    ) {
        // Build connector configuration based on provider
        // String connectorConfig = buildEmbeddingModelConnectorConfig(embeddingModel, remoteStoreCredential);

        String provider = embeddingModel.getProvider();
        String connectorConfig = buildBedrockEmbeddingConnectorConfig(provider, embeddingModel, remoteStoreCredential);

        // Build model name from provider and model ID (e.g., "bedrock/amazon.titan-embed-text-v2:0")
        String sanitizedProvider = provider.replace("/", "-"); // AOSS doesn't allow / in model name.
        String sanitizedModelId = embeddingModel.getModelId().replace("/", "-");
        String modelName = String.format("%s__%s", sanitizedProvider, sanitizedModelId);

        return String
            .format(
                "{ \"function_name\": \"remote\", \"name\": \"%s\", \"description\": \"Auto-generated model\", \"connector\": %s }",
                modelName,
                connectorConfig
            );
    }

    /**
     * Builds Bedrock embedding connector configuration from template
     */
    private static String buildBedrockEmbeddingConnectorConfig(
        String provider,
        org.opensearch.ml.common.memorycontainer.RemoteEmbeddingModel embeddingModel,
        Map<String, String> remoteStoreCredential
    ) {
        try {
            // Load template from resource file, sample provider "bedrock/text_embedding"
            String template = loadConnectorTemplate(provider, embeddingModel.getModelId());

            // Get parameters and credential from embedding model
            Map<String, String> parameters = embeddingModel.getParameters();
            Map<String, String> credential = embeddingModel.getCredential();

            // Use embedding model credentials if provided, otherwise use remote store credentials
            if (credential == null || credential.isEmpty()) {
                credential = remoteStoreCredential;
            }

            // Validate that parameters are provided
            if (parameters == null || parameters.isEmpty()) {
                throw new IllegalArgumentException("Bedrock embedding model requires parameters block");
            }

            // Parse the template as JSON and inject parameters and credential
            String connectorConfig = injectParametersAndCredential(template, parameters, credential);

            return connectorConfig;
        } catch (IOException e) {
            log.error("Failed to load connector template", e);
            throw new IllegalArgumentException("Failed to load connector template");
        }
    }

    /**
     * Injects parameters and credential into the connector template
     */
    private static String injectParametersAndCredential(String template, Map<String, String> parameters, Map<String, String> credential)
        throws IOException {
        // Parse template as JSON
        XContentParser parser = XContentHelper
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, new BytesArray(template), XContentType.JSON);
        Map<String, Object> connectorMap = parser.mapOrdered();

        // Inject parameters
        connectorMap.put("parameters", parameters);

        // Inject credential
        connectorMap.put("credential", credential);

        String protocol = ConnectorUtils.determineProtocol(parameters, credential);
        connectorMap.put("protocol", protocol);

        boolean isAwsSigv4 = AWS_SIGV4.equals(protocol);

        Map headersMap = new HashMap();
        if (parameters.containsKey(HEADERS_FIELD) && StringUtils.isJson(parameters.get(HEADERS_FIELD))) {
            headersMap.putAll(gson.fromJson(parameters.get(HEADERS_FIELD), Map.class));
        }
        if (connectorMap.containsKey(ACTIONS_FIELD)) {
            List actions = (List) connectorMap.get(ACTIONS_FIELD);
            for (Object actionObj : actions) {
                Map<String, Object> action = (Map<String, Object>) actionObj;
                if (action.containsKey(HEADERS_FIELD)) {
                    Map<String, Object> headers = (Map<String, Object>) action.get(HEADERS_FIELD);
                    if (isAwsSigv4) {
                        headers.put("x-amz-content-sha256", "required");
                    }
                    headers.putAll(headersMap);
                } else {
                    action.put(HEADERS_FIELD, headersMap);
                }
            }
        }

        // Convert back to JSON string
        return StringUtils.toJson(connectorMap);
    }

    /**
     * Loads connector template from resource file
     * Path format: model-connectors/<provider>/<function>/<model_id>.json
     * 
     * @param provider The model provider (e.g., "bedrock", "openai", "cohere")
     * @param modelId The model identifier (e.g., "amazon.titan-embed-text-v2")
     * @return The connector template as a string
     */
    private static String loadConnectorTemplate(String provider, String modelId) throws IOException {
        // Normalize model ID for file name (replace : with -)
        String normalizedModelId = modelId.replace(":", "-");
        String path = String.format("model-connectors/%s/%s.json", provider, normalizedModelId);

        try (java.io.InputStream is = RemoteStorageHelper.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Connector template not found: " + path);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts model ID from model registration response
     */
    private static String extractModelIdFromResponse(ModelTensorOutput response) {
        try {
            Map<String, ?> dataAsMap = response.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
            Object modelIdObj = dataAsMap.get("model_id");
            if (modelIdObj == null) {
                throw new IllegalArgumentException("model_id not found in response");
            }
            return modelIdObj.toString();
        } catch (Exception e) {
            log.error("Failed to parse model_id from response", e);
            throw new IllegalArgumentException("Failed to parse model_id from response", e);
        }
    }
}
