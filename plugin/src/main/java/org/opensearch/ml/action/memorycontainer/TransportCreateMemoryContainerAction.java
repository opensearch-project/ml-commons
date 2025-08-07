/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_MISMATCH_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_CONSTRUCTION;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_EF_SEARCH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_ENGINE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_M;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_METHOD_NAME;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.KNN_SPACE_TYPE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_REMOTE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STATIC_MEMORY_INDEX_PREFIX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
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
    private final SdkClient sdkClient;
    private final ClusterService clusterService;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLModelManager mlModelManager;

    @Inject
    public TransportCreateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        MLIndicesHandler mlIndicesHandler,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager
    ) {
        super(MLCreateMemoryContainerAction.NAME, transportService, actionFilters, MLCreateMemoryContainerRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected void doExecute(Task task, MLCreateMemoryContainerRequest request, ActionListener<MLCreateMemoryContainerResponse> listener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            listener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLCreateMemoryContainerInput input = request.getMlCreateMemoryContainerInput();

        // Validate tenant ID
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, input.getTenantId(), listener)) {
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String tenantId = input.getTenantId();

        // Validate models before creating memory container
        validateModels(input.getMemoryStorageConfig(), ActionListener.wrap(isValid -> {
            // Check if memory container index exists, create if not
            ActionListener<Boolean> indexCheckListener = ActionListener.wrap(created -> {
                try {
                    // Create memory container document without ID (will be auto-generated)
                    MLMemoryContainer memoryContainer = buildMemoryContainer(input, user, tenantId);

                    // Index the memory container document first to get the generated ID
                    indexMemoryContainer(memoryContainer, ActionListener.wrap(memoryContainerId -> {
                        // Create memory data indices based on semantic storage config
                        createMemoryDataIndices(memoryContainerId, memoryContainer, user, ActionListener.wrap(actualIndexName -> {
                            // Update the memory container with the actual index name
                            MemoryStorageConfig config = memoryContainer.getMemoryStorageConfig();
                            if (config == null) {
                                config = MemoryStorageConfig.builder().memoryIndexName(actualIndexName).build();
                            } else {
                                config.setMemoryIndexName(actualIndexName);
                            }
                            memoryContainer.setMemoryStorageConfig(config);

                            // Update the container document with the index name
                            updateMemoryContainer(memoryContainerId, memoryContainer, ActionListener.wrap(updated -> {
                                listener.onResponse(new MLCreateMemoryContainerResponse(memoryContainerId, "created"));
                            }, listener::onFailure));
                        }, listener::onFailure));
                    }, listener::onFailure));

                } catch (Exception e) {
                    log.error("Failed to create memory container", e);
                    listener.onFailure(e);
                }
            }, listener::onFailure);

            // Initialize memory container index if it doesn't exist
            initMemoryContainerIndexIfAbsent(indexCheckListener);
        }, listener::onFailure));
    }

    private void initMemoryContainerIndexIfAbsent(ActionListener<Boolean> listener) {
        try {
            mlIndicesHandler.initMLIndexIfAbsent(MLIndex.MEMORY_CONTAINER, listener);
        } catch (Exception e) {
            log.error("Failed to init memory container index", e);
            listener.onFailure(e);
        }
    }

    private MLMemoryContainer buildMemoryContainer(MLCreateMemoryContainerInput input, User user, String tenantId) {
        Instant now = Instant.now();

        return MLMemoryContainer
            .builder()
            .name(input.getName())
            .description(input.getDescription())
            .owner(user)
            .tenantId(tenantId)
            .createdTime(now)
            .lastUpdatedTime(now)
            .memoryStorageConfig(input.getMemoryStorageConfig())
            .build();
    }

    private void createMemoryDataIndices(
        String memoryContainerId,
        MLMemoryContainer container,
        User user,
        ActionListener<String> listener
    ) {
        String userId = user != null ? user.getName() : "default";
        MemoryStorageConfig memoryStorageConfig = container.getMemoryStorageConfig();
        String baseIndexName = memoryStorageConfig != null ? memoryStorageConfig.getMemoryIndexName() : null;

        if (baseIndexName == null) {
            // Generate default index name based on semantic storage config
            if (memoryStorageConfig == null || !memoryStorageConfig.isSemanticStorageEnabled()) {
                baseIndexName = STATIC_MEMORY_INDEX_PREFIX + memoryContainerId + "-" + userId;
            } else if (memoryStorageConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                baseIndexName = KNN_MEMORY_INDEX_PREFIX + memoryContainerId + "-" + userId;
            } else if (memoryStorageConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                baseIndexName = SPARSE_MEMORY_INDEX_PREFIX + memoryContainerId + "-" + userId;
            }
        }

        // Convert to lowercase as OpenSearch doesn't support uppercase in index names
        final String finalIndexName = baseIndexName.toLowerCase(Locale.ROOT);
        // Create the memory data index with appropriate mapping
        createMemoryDataIndex(finalIndexName, container.getMemoryStorageConfig(), ActionListener.wrap(success -> {
            // Return the actual index name that was created
            listener.onResponse(finalIndexName);
        }, listener::onFailure));
    }

    private void createMemoryDataIndex(String indexName, MemoryStorageConfig memoryStorageConfig, ActionListener<Boolean> listener) {
        try {
            Map<String, Object> indexSettings = new HashMap<>();
            Map<String, Object> indexMappings = new HashMap<>();

            // Build index mappings based on semantic storage config
            Map<String, Object> properties = new HashMap<>();

            // Common fields for all index types
            // Use keyword type for ID fields that need exact matching
            properties.put(USER_ID_FIELD, Map.of("type", "keyword"));
            properties.put(AGENT_ID_FIELD, Map.of("type", "keyword"));
            properties.put(SESSION_ID_FIELD, Map.of("type", "keyword"));
            properties.put(MEMORY_FIELD, Map.of("type", "text")); // Keep as text for full-text search
            properties.put(TAGS_FIELD, Map.of("type", "flat_object"));
            properties.put(MEMORY_TYPE_FIELD, Map.of("type", "keyword"));
            properties.put(ROLE_FIELD, Map.of("type", "text")); // Text field for role (human/llm)
            properties.put(CREATED_TIME_FIELD, Map.of("type", "date", "format", "strict_date_time||epoch_millis"));
            properties.put(LAST_UPDATED_TIME_FIELD, Map.of("type", "date", "format", "strict_date_time||epoch_millis"));

            if (memoryStorageConfig != null && memoryStorageConfig.isSemanticStorageEnabled()) {

                if (memoryStorageConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                    // KNN index configuration
                    indexSettings.put("index.knn", true);
                    indexSettings.put("index.knn.algo_param.ef_search", KNN_EF_SEARCH);

                    int dimension = memoryStorageConfig.getDimension();

                    Map<String, Object> knnVector = new HashMap<>();
                    knnVector.put("type", "knn_vector");
                    knnVector.put("dimension", dimension);

                    Map<String, Object> method = new HashMap<>();
                    method.put("name", KNN_METHOD_NAME);
                    method.put("space_type", KNN_SPACE_TYPE);
                    method.put("engine", KNN_ENGINE);
                    method.put("parameters", Map.of("ef_construction", KNN_EF_CONSTRUCTION, "m", KNN_M));
                    knnVector.put("method", method);
                    properties.put(MEMORY_EMBEDDING_FIELD, knnVector);

                } else if (memoryStorageConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                    // Sparse index configuration - use rank_features for sparse embeddings
                    properties.put(MEMORY_EMBEDDING_FIELD, Map.of("type", "rank_features"));
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
                            log.error("Error creating memory data index: {}", indexName, e);
                            listener.onFailure(e);
                        }
                    })
                );
        } catch (Exception e) {
            log.error("Failed to create memory data index", e);
            listener.onFailure(e);
        }
    }

    private void updateMemoryContainer(String memoryContainerId, MLMemoryContainer container, ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .putDataObjectAsync(
                    PutDataObjectRequest
                        .builder()
                        .tenantId(container.getTenantId())
                        .index(ML_MEMORY_CONTAINER_INDEX)
                        .id(memoryContainerId)
                        .dataObject(container)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to update memory container", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            log.info("Successfully updated memory container with ID: {}", memoryContainerId);
                            listener.onResponse(true);
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to update memory container", e);
            listener.onFailure(e);
        }
    }

    private void indexMemoryContainer(MLMemoryContainer container, ActionListener<String> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .putDataObjectAsync(
                    PutDataObjectRequest
                        .builder()
                        .tenantId(container.getTenantId())
                        .index(ML_MEMORY_CONTAINER_INDEX)
                        .dataObject(container)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to index memory container", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            assert indexResponse != null;
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                                String generatedId = indexResponse.getId();
                                log.info("Successfully created memory container with ID: {}", generatedId);
                                listener.onResponse(generatedId);
                            } else {
                                listener.onFailure(new RuntimeException("Failed to create memory container"));
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to save memory container", e);
            listener.onFailure(e);
        }
    }

    private void validateModels(MemoryStorageConfig config, ActionListener<Boolean> listener) {
        if (config == null || !config.isSemanticStorageEnabled()) {
            listener.onResponse(true);
            return;
        }

        // Validate LLM model first
        if (config.getLlmModelId() != null) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(llmModel -> {
                    if (llmModel.getAlgorithm() != FunctionName.REMOTE) {
                        listener
                            .onFailure(new IllegalArgumentException(String.format(LLM_MODEL_NOT_REMOTE_ERROR, llmModel.getAlgorithm())));
                        return;
                    }
                    // LLM model is valid, now validate embedding model
                    validateEmbeddingModel(config, listener);
                }, e -> {
                    log.error("Failed to get LLM model: {}", config.getLlmModelId(), e);
                    listener.onFailure(new IllegalArgumentException(String.format(LLM_MODEL_NOT_FOUND_ERROR, config.getLlmModelId())));
                }), context::restore);

                mlModelManager.getModel(config.getLlmModelId(), wrappedListener);
            }
        } else {
            // No LLM model specified, just validate embedding model
            validateEmbeddingModel(config, listener);
        }
    }

    private void validateEmbeddingModel(MemoryStorageConfig config, ActionListener<Boolean> listener) {
        if (config.getEmbeddingModelId() != null) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(embeddingModel -> {
                    FunctionName modelAlgorithm = embeddingModel.getAlgorithm();
                    FunctionName expectedType = config.getEmbeddingModelType();

                    // Model must be either the expected type or REMOTE
                    if (modelAlgorithm != expectedType && modelAlgorithm != FunctionName.REMOTE) {
                        listener
                            .onFailure(
                                new IllegalArgumentException(
                                    String.format(EMBEDDING_MODEL_TYPE_MISMATCH_ERROR, expectedType, modelAlgorithm)
                                )
                            );
                        return;
                    }

                    // Both models are valid
                    listener.onResponse(true);
                }, e -> {
                    log.error("Failed to get embedding model: {}", config.getEmbeddingModelId(), e);
                    listener
                        .onFailure(
                            new IllegalArgumentException(String.format(EMBEDDING_MODEL_NOT_FOUND_ERROR, config.getEmbeddingModelId()))
                        );
                }), context::restore);

                mlModelManager.getModel(config.getEmbeddingModelId(), wrappedListener);
            }
        } else {
            // No embedding model specified, validation passes
            listener.onResponse(true);
        }
    }
}
