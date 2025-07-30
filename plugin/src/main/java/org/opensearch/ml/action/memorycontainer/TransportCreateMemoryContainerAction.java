/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACT_ENCODING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_CONSTRUCTION;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_SEARCH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_ENGINE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_M;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_METHOD_NAME;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_SPACE_TYPE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RAW_MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STATIC_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_ID_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.SemanticStorageConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for creating a memory container
 */
@Log4j2
public class TransportCreateMemoryContainerAction extends
    HandledTransportAction<MLCreateMemoryContainerRequest, MLCreateMemoryContainerResponse> {

    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final ClusterService clusterService;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportCreateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        MLIndicesHandler mlIndicesHandler,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLCreateMemoryContainerAction.NAME, transportService, actionFilters, MLCreateMemoryContainerRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLCreateMemoryContainerRequest request, ActionListener<MLCreateMemoryContainerResponse> listener) {
        MLCreateMemoryContainerInput input = request.getMlCreateMemoryContainerInput();

        // Validate tenant ID
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, input.getTenantId(), listener)) {
            return;
        }

        User user = parseUserFromThreadContext(client);
        String tenantId = input.getTenantId();

        // Check if memory container index exists, create if not
        ActionListener<Boolean> indexCheckListener = ActionListener.wrap(created -> {
            try {
                // Generate container ID
                String containerId = UUID.randomUUID().toString();

                // Create memory container document
                MLMemoryContainer memoryContainer = createMemoryContainer(input, containerId, user, tenantId);

                // Create memory data indices based on semantic storage config
                createMemoryDataIndices(memoryContainer, user, ActionListener.wrap(indicesCreated -> {
                    // Index the memory container document
                    indexMemoryContainer(memoryContainer, listener);
                }, listener::onFailure));

            } catch (Exception e) {
                log.error("Failed to create memory container", e);
                listener.onFailure(e);
            }
        }, listener::onFailure);

        // Initialize memory container index if it doesn't exist
        initMemoryContainerIndexIfAbsent(indexCheckListener);
    }

    private void initMemoryContainerIndexIfAbsent(ActionListener<Boolean> listener) {
        try {
            mlIndicesHandler.initMLIndexIfAbsent(MLIndex.MEMORY_CONTAINER, listener);
        } catch (Exception e) {
            log.error("Failed to init memory container index", e);
            listener.onFailure(e);
        }
    }

    private MLMemoryContainer createMemoryContainer(MLCreateMemoryContainerInput input, String containerId, User user, String tenantId) {
        Instant now = Instant.now();

        return MLMemoryContainer
            .builder()
            .containerId(containerId)
            .containerName(input.getContainerName())
            .description(input.getDescription())
            .owner(user)
            .tenantId(tenantId)
            .createdTime(now)
            .lastUpdatedTime(now)
            .indexName(input.getIndexName())
            .semanticStorage(input.getSemanticStorage())
            .build();
    }

    private void createMemoryDataIndices(MLMemoryContainer container, User user, ActionListener<Boolean> listener) {
        String userId = user != null ? user.getName() : "default";
        String baseIndexName = container.getIndexName();

        if (baseIndexName == null) {
            // Generate default index name based on semantic storage config
            SemanticStorageConfig semanticStorage = container.getSemanticStorage();
            if (semanticStorage == null || !semanticStorage.isSemanticStorageEnabled()) {
                baseIndexName = STATIC_MEMORY_INDEX_PREFIX + container.getContainerId() + "-" + userId;
            } else if (semanticStorage.getModelType() == FunctionName.TEXT_EMBEDDING) {
                baseIndexName = KNN_MEMORY_INDEX_PREFIX + container.getContainerId() + "-" + userId;
            } else if (semanticStorage.getModelType() == FunctionName.SPARSE_ENCODING) {
                baseIndexName = SPARSE_MEMORY_INDEX_PREFIX + container.getContainerId() + "-" + userId;
            }
        }

        // Create the memory data index with appropriate mapping
        createMemoryDataIndex(baseIndexName, container.getSemanticStorage(), listener);
    }

    private void createMemoryDataIndex(String indexName, SemanticStorageConfig semanticStorage, ActionListener<Boolean> listener) {
        try {
            Map<String, Object> indexSettings = new HashMap<>();
            Map<String, Object> indexMappings = new HashMap<>();

            // Build index mappings based on semantic storage config
            Map<String, Object> properties = new HashMap<>();

            // Common fields for all index types
            properties.put(USER_ID_FIELD, Map.of("type", "text"));
            properties.put(AGENT_ID_FIELD, Map.of("type", "text"));
            properties.put(SESSION_ID_FIELD, Map.of("type", "text"));
            properties.put(RAW_MESSAGES_FIELD, Map.of("type", "text"));
            properties.put(TAGS_FIELD, Map.of("type", "flat_object"));

            if (semanticStorage != null && semanticStorage.isSemanticStorageEnabled()) {
                properties.put(FACT_FIELD, Map.of("type", "text"));

                if (semanticStorage.getModelType() == FunctionName.TEXT_EMBEDDING) {
                    // KNN index configuration
                    indexSettings.put("index.knn", true);
                    indexSettings.put("index.knn.algo_param.ef_search", KNN_EF_SEARCH);

                    int dimension = semanticStorage.getDimension();

                    Map<String, Object> knnVector = new HashMap<>();
                    knnVector.put("type", "knn_vector");
                    knnVector.put("dimension", dimension);

                    Map<String, Object> method = new HashMap<>();
                    method.put("name", KNN_METHOD_NAME);
                    method.put("space_type", KNN_SPACE_TYPE);
                    method.put("engine", KNN_ENGINE);
                    method.put("parameters", Map.of("ef_construction", KNN_EF_CONSTRUCTION, "m", KNN_M));
                    knnVector.put("method", method);

                    properties.put(FACT_ENCODING_FIELD, knnVector);

                } else if (semanticStorage.getModelType() == FunctionName.SPARSE_ENCODING) {
                    // Sparse index configuration
                    properties.put(FACT_ENCODING_FIELD, Map.of("type", "rank_feature"));
                }
            }

            indexMappings.put("properties", properties);

            // Create the index using client directly
            client
                .admin()
                .indices()
                .create(
                    new org.opensearch.action.admin.indices.create.CreateIndexRequest(indexName)
                        .settings(indexSettings)
                        .mapping(indexMappings),
                    ActionListener.wrap(response -> {
                        if (response.isAcknowledged()) {
                            log.info("Successfully created memory data index: {}", indexName);
                            listener.onResponse(true);
                        } else {
                            listener.onFailure(new RuntimeException("Failed to create memory data index: " + indexName));
                        }
                    }, e -> {
                        if (e instanceof org.opensearch.ResourceAlreadyExistsException) {
                            log.info("Memory data index already exists: {}", indexName);
                            listener.onResponse(true);
                        } else {
                            log.error("Error creating memory data index: " + indexName, e);
                            listener.onFailure(e);
                        }
                    })
                );
        } catch (Exception e) {
            log.error("Failed to create memory data index", e);
            listener.onFailure(e);
        }
    }

    private void indexMemoryContainer(MLMemoryContainer container, ActionListener<MLCreateMemoryContainerResponse> listener) {
        try {
            XContentBuilder builder = XContentType.JSON.contentBuilder();
            container.toXContent(builder, ToXContent.EMPTY_PARAMS);

            IndexRequest indexRequest = new IndexRequest(ML_MEMORY_CONTAINER_INDEX)
                .id(container.getContainerId())
                .source(builder)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            client.index(indexRequest, ActionListener.wrap(response -> {
                if (response.getResult() == DocWriteResponse.Result.CREATED) {
                    log.info("Successfully created memory container with ID: {}", container.getContainerId());
                    listener.onResponse(new MLCreateMemoryContainerResponse(container.getContainerId(), "created"));
                } else {
                    listener.onFailure(new RuntimeException("Failed to create memory container"));
                }
            }, e -> {
                if (e instanceof VersionConflictEngineException) {
                    listener.onFailure(new IllegalArgumentException("Memory container with ID already exists"));
                } else {
                    log.error("Failed to index memory container", e);
                    listener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to build memory container document", e);
            listener.onFailure(e);
        }
    }

    private User parseUserFromThreadContext(Client client) {
        String userStr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        if (userStr != null && !userStr.trim().isEmpty()) {
            return User.parse(userStr);
        }
        return null;
    }
}
