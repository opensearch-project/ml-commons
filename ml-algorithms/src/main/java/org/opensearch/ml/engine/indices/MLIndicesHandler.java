/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.indices;

import static org.opensearch.ml.common.CommonValue.META;
import static org.opensearch.ml.common.CommonValue.ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_SESSION_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_WORKING_MEMORY_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.SCHEMA_VERSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_CONSTRUCTION;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_SEARCH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_ENGINE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_M;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_METHOD_NAME;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_SPACE_TYPE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_HISTORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LONG_TERM_MEMORY_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.WORKING_MEMORY_INDEX;
import static org.opensearch.ml.common.utils.IndexUtils.ALL_NODES_REPLICA_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.DEFAULT_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.UPDATED_DEFAULT_INDEX_SETTINGS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.utils.IndexUtils;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Log4j2
public class MLIndicesHandler {
    @NonNull
    ClusterService clusterService;
    @NonNull
    Client client;
    @NonNull
    MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private static final Map<String, AtomicBoolean> indexMappingUpdated = new HashMap<>();

    static {
        for (MLIndex mlIndex : MLIndex.values()) {
            indexMappingUpdated.put(mlIndex.getIndexName(), new AtomicBoolean(false));
        }
    }

    /**
     * Determines whether an index exists on non-multi tenancy enabled environments. Otherwise,
     * returns true when multiTenancy is Enabled
     *
     * @param clusterService the cluster service
     * @param isMultiTenancyEnabled whether multi-tenancy is enabled
     * @param indexName - the index to search
     * @return boolean indicating the existence of an index. Returns true if multitenancy is enabled.
     * @implNote This method assumes if your environment enables multi tenancy, then your plugin indices are
     * pre-populated. If this is incorrect, it will result in unwanted early returns without checking the clusterService.
     */
    public static boolean doesMultiTenantIndexExist(ClusterService clusterService, boolean isMultiTenancyEnabled, String indexName) {
        return (indexName.startsWith(".") && isMultiTenancyEnabled) || clusterService.state().metadata().hasIndex(indexName);
    }

    public void initModelGroupIndexIfAbsent(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MODEL_GROUP, listener);
    }

    public void initModelIndexIfAbsent(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MODEL, listener);
    }

    public void initMLTaskIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.TASK, listener);
    }

    public void initMLConnectorIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONNECTOR, listener);
    }

    public void initMemoryMetaIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MEMORY_META, listener);
    }

    public void initMemoryMessageIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MEMORY_MESSAGE, listener);
    }

    public void initMLConfigIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONFIG, listener);
    }

    public void initMLControllerIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONTROLLER, listener);
    }

    public void initMLMcpSessionManagementIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MCP_SESSION_MANAGEMENT, listener);
    }

    public void initMLMcpToolsIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MCP_TOOLS, listener);
    }

    public void initMLJobsIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.JOBS, listener);
    }

    public void initMLAgentIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.AGENT, listener);
    }

    public void initMemoryContainerIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MEMORY_CONTAINER, listener);
    }

    public void initMLIndexIfAbsent(MLIndex index, ActionListener<Boolean> listener) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();
        Integer version = index.getVersion();
        initIndexIfAbsent(indexName, mapping, version, listener, true);
    }

    private String getMapping(String mappingPath) {
        if (mappingPath == null) {
            throw new IllegalArgumentException("Mapping path cannot be null");
        }

        try {
            return IndexUtils.getMappingFromFile(mappingPath);
        } catch (IOException e) {
            // Unchecked exception is thrown since the method is being called within a constructor
            throw new UncheckedIOException("Failed to fetch index mapping from file: " + mappingPath, e);
        }
    }

    public void createSessionMemoryDataIndex(String indexName, MemoryConfiguration configuration, ActionListener<Boolean> listener) {
        String indexMappings = getMapping(ML_MEMORY_SESSION_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(SESSION_INDEX) == null
            || configuration.getMemoryIndexMapping(SESSION_INDEX).isEmpty()
                ? ALL_NODES_REPLICA_INDEX_SETTINGS
                : configuration.getMemoryIndexMapping(SESSION_INDEX);
        initIndexIfAbsent(indexName, StringUtils.toJson(indexMappings), indexSettings, 1, listener, configuration.isUseSystemIndex());
    }

    public void createWorkingMemoryDataIndex(String indexName, MemoryConfiguration configuration, ActionListener<Boolean> listener) {
        String indexMappings = getMapping(ML_WORKING_MEMORY_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(WORKING_MEMORY_INDEX) == null
            || configuration.getMemoryIndexMapping(WORKING_MEMORY_INDEX).isEmpty()
                ? ALL_NODES_REPLICA_INDEX_SETTINGS
                : configuration.getMemoryIndexMapping(WORKING_MEMORY_INDEX);
        initIndexIfAbsent(indexName, StringUtils.toJson(indexMappings), indexSettings, 1, listener, configuration.isUseSystemIndex());
    }

    public void createLongTermMemoryHistoryIndex(String indexName, MemoryConfiguration configuration, ActionListener<Boolean> listener) {
        String indexMappings = getMapping(ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH);
        Map<String, Object> indexSettings = configuration.getMemoryIndexMapping(LONG_TERM_MEMORY_HISTORY_INDEX) == null
            || configuration.getMemoryIndexMapping(LONG_TERM_MEMORY_HISTORY_INDEX).isEmpty()
                ? ALL_NODES_REPLICA_INDEX_SETTINGS
                : configuration.getMemoryIndexMapping(LONG_TERM_MEMORY_HISTORY_INDEX);
        initIndexIfAbsent(indexName, StringUtils.toJson(indexMappings), indexSettings, 1, listener, configuration.isUseSystemIndex());
    }

    /**
     * Builds the long-term memory index mapping dynamically based on configuration.
     * Loads base mapping from JSON and adds embedding field configuration if needed.
     */
    private String buildLongTermMemoryMapping(MemoryConfiguration memoryConfig) throws IOException {
        // Get base mapping from JSON file
        String baseMappingJson = getMapping(ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH);

        // Parse the JSON to extract properties
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        // Parse base mapping - we'll rebuild it with additional fields if needed
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

        // Add embedding field based on configuration
        if (memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
            // Build KNN vector configuration for text embeddings
            Map<String, Object> knnVector = new HashMap<>();
            knnVector.put("type", "knn_vector");
            knnVector.put("dimension", memoryConfig.getDimension());

            Map<String, Object> method = new HashMap<>();
            method.put("name", KNN_METHOD_NAME);
            method.put("space_type", KNN_SPACE_TYPE);
            method.put("engine", KNN_ENGINE);
            method.put("parameters", Map.of("ef_construction", KNN_EF_CONSTRUCTION, "m", KNN_M));
            knnVector.put("method", method);

            properties.put(MEMORY_EMBEDDING_FIELD, knnVector);
        } else if (memoryConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
            // Use rank_features for sparse embeddings
            properties.put(MEMORY_EMBEDDING_FIELD, Map.of("type", "rank_features"));
        }
        // If no embedding type, no embedding field is added

        mapping.put("properties", properties);
        return StringUtils.toJson(mapping);
    }

    public void createLongTermMemoryIndex(
        String pipelineName,
        String indexName,
        MemoryConfiguration memoryConfig,
        ActionListener<Boolean> listener
    ) {
        try {
            // Build the dynamic mapping based on configuration
            String indexMappings = buildLongTermMemoryMapping(memoryConfig);

            // Build index settings
            Map<String, Object> indexSettings = new HashMap<>();

            // Add KNN settings for text embeddings
            if (memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                indexSettings.put("index.knn", true);
                indexSettings.put("index.knn.algo_param.ef_search", KNN_EF_SEARCH);
            }

            // Add pipeline if specified
            if (pipelineName != null) {
                indexSettings.put("default_pipeline", pipelineName);
            }

            // Add custom settings from configuration
            if (!memoryConfig.getIndexSettings().isEmpty() && memoryConfig.getIndexSettings().containsKey(LONG_TERM_MEMORY_INDEX)) {
                Map<String, Object> configuredIndexSettings = memoryConfig.getMemoryIndexMapping(LONG_TERM_MEMORY_INDEX);
                indexSettings.putAll(configuredIndexSettings);
            } else {
                indexSettings.putAll(ALL_NODES_REPLICA_INDEX_SETTINGS);
            }

            // Initialize index with mapping and settings
            initIndexIfAbsent(indexName, StringUtils.toJson(indexMappings), indexSettings, 1, listener, memoryConfig.isUseSystemIndex());
        } catch (Exception e) {
            log.error("Failed to create long-term memory index", e);
            listener.onFailure(e);
        }
    }

    public void initIndexIfAbsent(
        String indexName,
        String mapping,
        Integer version,
        ActionListener<Boolean> listener,
        Boolean isSystemIndex
    ) {
        initIndexIfAbsent(indexName, mapping, null, version, listener, isSystemIndex);
    }

    public void initIndexIfAbsent(
        String indexName,
        String mapping,
        Map<String, Object> indexSettings,
        Integer version,
        ActionListener<Boolean> listener,
        Boolean isSystemIndex
    ) {
        if (isSystemIndex) {
            initIndexWithoutThreadContext(indexName, mapping, indexSettings, version, listener);
        } else {
            initIndexWithThreadContext(indexName, mapping, indexSettings, version, listener);
        }
    }

    public void initIndexWithThreadContext(
        String indexName,
        String mapping,
        Map<String, Object> indexSettings,
        Integer version,
        ActionListener<Boolean> listener
    ) {
        log.info("Using initIndexWithThreadContext method to create index: {}", indexName);
        try {
            ActionListener<CreateIndexResponse> actionListener = ActionListener.wrap(r -> {
                if (r.isAcknowledged()) {
                    log.info("create index:{}", indexName);
                    listener.onResponse(true);
                } else {
                    listener.onResponse(false);
                }
            }, e -> {
                if (e instanceof ResourceAlreadyExistsException
                    || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                    log.info("Skip creating the Index:{} that is already created by another parallel request", indexName);
                    listener.onResponse(true);
                } else {
                    log.error("Failed to create index {}", indexName, e);
                    listener.onFailure(e);
                }
            });
            CreateIndexRequest request = new CreateIndexRequest(indexName).mapping(mapping, XContentType.JSON);
            request.settings(Objects.requireNonNullElse(indexSettings, DEFAULT_INDEX_SETTINGS));
            client.admin().indices().create(request, actionListener);
        } catch (Exception e) {
            log.error("Failed to init index {}", indexName, e);
            listener.onFailure(e);
        }
    }

    public void initIndexWithoutThreadContext(
        String indexName,
        String mapping,
        Map<String, Object> indexSettings,
        Integer version,
        ActionListener<Boolean> listener
    ) {
        log.info("Using initIndexWithoutThreadContext method to create index: {}", indexName);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            if (!MLIndicesHandler.doesMultiTenantIndexExist(clusterService, mlFeatureEnabledSetting.isMultiTenancyEnabled(), indexName)) {
                ActionListener<CreateIndexResponse> actionListener = ActionListener.wrap(r -> {
                    if (r.isAcknowledged()) {
                        log.info("create index:{}", indexName);
                        internalListener.onResponse(true);
                    } else {
                        internalListener.onResponse(false);
                    }
                }, e -> {
                    if (e instanceof ResourceAlreadyExistsException
                        || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                        log.info("Skip creating the Index:{} that is already created by another parallel request", indexName);
                        internalListener.onResponse(true);
                    } else {
                        log.error("Failed to create index {}", indexName, e);
                        internalListener.onFailure(e);
                    }
                });
                CreateIndexRequest request = new CreateIndexRequest(indexName).mapping(mapping, XContentType.JSON);
                if (indexSettings != null) {
                    request.settings(indexSettings);
                } else {
                    request
                        .settings(
                            indexName.equals(MLIndex.CONFIG.getIndexName()) ? ALL_NODES_REPLICA_INDEX_SETTINGS : DEFAULT_INDEX_SETTINGS
                        );
                }
                client.admin().indices().create(request, actionListener);
            } else {
                log.debug("index:{} is already created", indexName);
                if (indexMappingUpdated.containsKey(indexName) && !indexMappingUpdated.get(indexName).get()) {
                    shouldUpdateIndex(indexName, version, ActionListener.wrap(r -> {
                        if (r) {
                            // return true if should update index
                            client
                                .admin()
                                .indices()
                                .putMapping(
                                    new PutMappingRequest().indices(indexName).source(mapping, XContentType.JSON),
                                    ActionListener.wrap(response -> {
                                        if (response.isAcknowledged()) {
                                            UpdateSettingsRequest updateSettingRequest = new UpdateSettingsRequest();
                                            updateSettingRequest.indices(indexName);
                                            if (indexSettings != null) {
                                                updateSettingRequest.settings(indexSettings);
                                            } else {
                                                updateSettingRequest
                                                    .settings(
                                                        indexName.equals(MLIndex.CONFIG.getIndexName())
                                                            ? UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS
                                                            : UPDATED_DEFAULT_INDEX_SETTINGS
                                                    );
                                            }
                                            client
                                                .admin()
                                                .indices()
                                                .updateSettings(updateSettingRequest, ActionListener.wrap(updateResponse -> {
                                                    if (response.isAcknowledged()) {
                                                        indexMappingUpdated.get(indexName).set(true);
                                                        internalListener.onResponse(true);
                                                    } else {
                                                        internalListener
                                                            .onFailure(new MLException("Failed to update index setting for: " + indexName));
                                                    }
                                                }, exception -> {
                                                    log.error("Failed to update index setting for: {}", indexName, exception);
                                                    internalListener.onFailure(exception);
                                                }));
                                        } else {
                                            internalListener.onFailure(new MLException("Failed to update index: " + indexName));
                                        }
                                    }, exception -> {
                                        log.error("Failed to update index {}", indexName, exception);
                                        internalListener.onFailure(exception);
                                    })
                                );
                        } else {
                            // no need to update index if it does not exist or the version is already
                            // up-to-date.
                            indexMappingUpdated.get(indexName).set(true);
                            internalListener.onResponse(true);
                        }
                    }, e -> {
                        log.error("Failed to update index mapping", e);
                        internalListener.onFailure(e);
                    }));
                } else {
                    // No need to update index if it's not ML system index or it's already updated.
                    internalListener.onResponse(true);
                }
            }
        } catch (Exception e) {
            log.error("Failed to init index {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Check if we should update index based on schema version.
     *
     * @param indexName  index name
     * @param newVersion new index mapping version
     * @param listener   action listener, if should update index, will pass true to
     *                   its onResponse method
     */
    public void shouldUpdateIndex(String indexName, Integer newVersion, ActionListener<Boolean> listener) {
        IndexMetadata indexMetaData = clusterService.state().getMetadata().indices().get(indexName);
        if (indexMetaData == null || indexMetaData.mapping() == null) {
            listener.onResponse(Boolean.FALSE);
            return;
        }
        Integer oldVersion = CommonValue.NO_SCHEMA_VERSION;
        Map<String, Object> indexMapping = indexMetaData.mapping().getSourceAsMap();
        Object meta = indexMapping.get(META);
        if (meta instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMapping = (Map<String, Object>) meta;
            Object schemaVersion = metaMapping.get(SCHEMA_VERSION_FIELD);
            if (schemaVersion instanceof Integer) {
                oldVersion = (Integer) schemaVersion;
            }
        }
        listener.onResponse(newVersion > oldVersion);
    }

    @VisibleForTesting
    public boolean doesIndexExists(String indexName) {
        return MLIndicesHandler.doesMultiTenantIndexExist(clusterService, mlFeatureEnabledSetting.isMultiTenancyEnabled(), indexName);
    }

}
