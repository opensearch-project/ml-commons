/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryModelService;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerModelValidator;
import org.opensearch.ml.helper.MemoryContainerPipelineHelper;
import org.opensearch.ml.helper.MemoryContainerSharedIndexValidator;
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
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLModelManager mlModelManager;

    @Inject
    public TransportCreateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager
    ) {
        super(MLCreateMemoryContainerAction.NAME, transportService, actionFilters, MLCreateMemoryContainerRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected void doExecute(Task task, MLCreateMemoryContainerRequest request, ActionListener<MLCreateMemoryContainerResponse> listener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            log.warn("Agentic memory feature is disabled. Request denied.");
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

        // Pre-process: if inline model specs are present, create models first
        MemoryConfiguration config = input.getConfiguration();
        if (config != null && (config.hasInlineEmbeddingModel() || config.hasInlineLlm())) {
            // Run cheap validations before creating models to avoid orphans
            try {
                config.validateNonModelInputs();
            } catch (Exception e) {
                listener.onFailure(e);
                return;
            }

            createInlineModels(
                config,
                tenantId,
                ActionListener.wrap(v -> { proceedWithContainerCreation(input, user, tenantId, listener); }, e -> {
                    // Best-effort cleanup of any models created before failure
                    cleanupOrphanModels(config);
                    listener.onFailure(e);
                })
            );
        } else {
            proceedWithContainerCreation(input, user, tenantId, listener);
        }
    }

    /**
     * Pre-processing step: create models from inline specs, then set the resulting IDs
     * on the configuration. After this, the config looks identical to one created the old way.
     */
    private void createInlineModels(MemoryConfiguration config, String tenantId, ActionListener<Void> listener) {
        // Step 1: Create embedding model if inline spec provided
        if (config.hasInlineEmbeddingModel()) {
            createModelFromSpec(config.getEmbeddingModelSpec(), false, ActionListener.wrap(embeddingModelId -> {
                config.setEmbeddingModelId(embeddingModelId);

                // Auto-detect type and dimension from known models
                FunctionName detectedType = MemoryModelService.detectEmbeddingType(config.getEmbeddingModelSpec().getModelId());
                Integer detectedDimension = MemoryModelService.detectEmbeddingDimension(config.getEmbeddingModelSpec().getModelId());
                if (detectedType != null) {
                    config.setEmbeddingModelType(detectedType);
                }
                if (detectedDimension != null) {
                    config.setDimension(detectedDimension);
                }

                log.info("Auto-created embedding model: {}, type: {}, dimension: {}", embeddingModelId, detectedType, detectedDimension);

                // Step 2: Create LLM model if inline spec provided
                if (config.hasInlineLlm()) {
                    createModelFromSpec(config.getLlmSpec(), true, ActionListener.wrap(llmModelId -> {
                        config.setLlmId(llmModelId);
                        setLlmResultPathFromProvider(config);
                        log.info("Auto-created LLM model: {}", llmModelId);
                        listener.onResponse(null);
                    }, listener::onFailure));
                } else {
                    listener.onResponse(null);
                }
            }, listener::onFailure));
        } else if (config.hasInlineLlm()) {
            // Only LLM inline, no embedding
            createModelFromSpec(config.getLlmSpec(), true, ActionListener.wrap(llmModelId -> {
                config.setLlmId(llmModelId);
                setLlmResultPathFromProvider(config);
                log.info("Auto-created LLM model: {}", llmModelId);
                listener.onResponse(null);
            }, listener::onFailure));
        } else {
            listener.onResponse(null);
        }
    }

    private void createModelFromSpec(MLAgentModelSpec modelSpec, boolean isMemoryLlm, ActionListener<String> listener) {
        try {
            MLRegisterModelInput modelInput = MemoryModelService.createModelFromSpec(modelSpec, isMemoryLlm);
            MLRegisterModelRequest modelRequest = new MLRegisterModelRequest(modelInput);
            client
                .execute(
                    MLRegisterModelAction.INSTANCE,
                    modelRequest,
                    ActionListener.wrap(response -> { listener.onResponse(response.getModelId()); }, e -> {
                        log.error("Failed to auto-create model for memory container: {}", modelSpec.getModelId(), e);
                        listener
                            .onFailure(
                                e instanceof IllegalArgumentException
                                    ? e
                                    : new OpenSearchStatusException("Failed to create model", RestStatus.INTERNAL_SERVER_ERROR)
                            );
                    })
                );
        } catch (Exception e) {
            log.error("Failed to build model registration input: {}", modelSpec.getModelId(), e);
            listener.onFailure(new IllegalArgumentException("Invalid model specification: " + e.getMessage()));
        }
    }

    private void cleanupOrphanModels(MemoryConfiguration config) {
        if (config.getEmbeddingModelId() != null && config.hasInlineEmbeddingModel()) {
            log.info("Cleaning up orphan embedding model: {}", config.getEmbeddingModelId());
            try {
                client
                    .execute(
                        org.opensearch.ml.common.transport.model.MLModelDeleteAction.INSTANCE,
                        new org.opensearch.ml.common.transport.model.MLModelDeleteRequest(config.getEmbeddingModelId(), null),
                        ActionListener
                            .wrap(
                                r -> log.info("Cleaned up orphan embedding model"),
                                e -> log.warn("Failed to cleanup orphan embedding model", e)
                            )
                    );
            } catch (Exception e) {
                log.warn("Failed to cleanup orphan embedding model: {}", config.getEmbeddingModelId(), e);
            }
        }
        if (config.getLlmId() != null && config.hasInlineLlm()) {
            log.info("Cleaning up orphan LLM model: {}", config.getLlmId());
            try {
                client
                    .execute(
                        org.opensearch.ml.common.transport.model.MLModelDeleteAction.INSTANCE,
                        new org.opensearch.ml.common.transport.model.MLModelDeleteRequest(config.getLlmId(), null),
                        ActionListener
                            .wrap(r -> log.info("Cleaned up orphan LLM model"), e -> log.warn("Failed to cleanup orphan LLM model", e))
                    );
            } catch (Exception e) {
                log.warn("Failed to cleanup orphan LLM model: {}", config.getLlmId(), e);
            }
        }
    }

    private void setLlmResultPathFromProvider(MemoryConfiguration config) {
        String resultPath = MemoryModelService.getLlmResultPath(config.getLlmSpec().getModelProvider());
        if (resultPath != null) {
            config.getParameters().put(org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_RESULT_PATH_FIELD, resultPath);
        }
    }

    private void proceedWithContainerCreation(
        MLCreateMemoryContainerInput input,
        User user,
        String tenantId,
        ActionListener<MLCreateMemoryContainerResponse> listener
    ) {

        // Validate configuration before creating memory container
        validateConfiguration(input.getConfiguration(), ActionListener.wrap(isValid -> {
            // Check if memory container index exists, create if not
            ActionListener<Boolean> indexCheckListener = ActionListener.wrap(created -> {
                try {
                    // Create memory container document with potentially updated configuration
                    MLMemoryContainer memoryContainer = buildMemoryContainer(input, user, tenantId);

                    // Index the memory container document (now includes auto-generated prefix if applicable)
                    indexMemoryContainer(memoryContainer, ActionListener.wrap(memoryContainerId -> {
                        // Create memory data indices based on semantic storage config
                        createMemoryDataIndices(memoryContainer, user, ActionListener.wrap(actualIndexName -> {
                            listener.onResponse(new MLCreateMemoryContainerResponse(memoryContainerId, "created"));
                        }, listener::onFailure));
                    }, listener::onFailure));

                } catch (Exception e) {
                    log.error("Failed to create memory container", e);
                    listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                }
            }, e -> {
                log.error("Failed to initialize memory container index", e);
                listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
            });

            // Initialize memory container index if it doesn't exist
            mlIndicesHandler.initMemoryContainerIndex(indexCheckListener);
        }, listener::onFailure));
    }

    private MLMemoryContainer buildMemoryContainer(MLCreateMemoryContainerInput input, User user, String tenantId) {
        Instant now = Instant.now();

        // Generate IDs for strategies that don't have them (new container creation)
        MemoryConfiguration configuration = input.getConfiguration();
        if (configuration != null && configuration.getStrategies() != null) {
            for (MemoryStrategy strategy : configuration.getStrategies()) {
                if (strategy.getId() == null || strategy.getId().isBlank()) {
                    strategy.setId(MemoryStrategy.generateStrategyId(strategy.getType()));
                }
                // Set enabled to true if not specified (default for new strategies)
                if (strategy.getEnabled() == null) {
                    strategy.setEnabled(true);
                }
            }
        }

        return MLMemoryContainer
            .builder()
            .name(input.getName())
            .description(input.getDescription())
            .owner(user)
            .tenantId(tenantId)
            .createdTime(now)
            .lastUpdatedTime(now)
            .configuration(configuration)
            .backendRoles(input.getBackendRoles())
            .build();
    }

    private void createMemoryDataIndices(MLMemoryContainer container, User user, ActionListener<String> listener) {
        String userId = user != null ? user.getName() : "default";
        MemoryConfiguration configuration = container.getConfiguration();
        String indexPrefix = configuration != null ? configuration.getIndexPrefix() : null;

        // Convert to lowercase as OpenSearch doesn't support uppercase in index names
        final String sessionIndexName = configuration.getSessionIndexName();
        final String workingMemoryIndexName = configuration.getWorkingMemoryIndexName();
        final String longTermMemoryIndexName = configuration.getLongMemoryIndexName();
        final String longTermMemoryHistoryIndexName = configuration.getLongMemoryHistoryIndexName();

        // Decision: strategies present = 4 indices (session/working/long-term/history)
        // No strategies = 2 indices (session/working only)
        if (configuration.getStrategies() == null || configuration.getStrategies().isEmpty()) {
            if (configuration.isDisableSession()) {
                mlIndicesHandler.createWorkingMemoryDataIndex(workingMemoryIndexName, configuration, ActionListener.wrap(success -> {
                    // Return the actual index name that was created
                    // Create the memory data index with appropriate mapping
                    listener.onResponse(workingMemoryIndexName);
                }, listener::onFailure));
            } else {
                mlIndicesHandler.createSessionMemoryDataIndex(sessionIndexName, configuration, ActionListener.wrap(result -> {
                    mlIndicesHandler.createWorkingMemoryDataIndex(workingMemoryIndexName, configuration, ActionListener.wrap(success -> {
                        // Return the actual index name that was created
                        // Create the memory data index with appropriate mapping
                        listener.onResponse(workingMemoryIndexName);
                    }, listener::onFailure));
                }, listener::onFailure));
            }
        } else {
            if (configuration.isDisableSession()) {
                createMemoryIndexes(
                    container,
                    listener,
                    configuration,
                    workingMemoryIndexName,
                    longTermMemoryIndexName,
                    longTermMemoryHistoryIndexName
                );
            } else {
                mlIndicesHandler.createSessionMemoryDataIndex(sessionIndexName, configuration, ActionListener.wrap(result -> {
                    createMemoryIndexes(
                        container,
                        listener,
                        configuration,
                        workingMemoryIndexName,
                        longTermMemoryIndexName,
                        longTermMemoryHistoryIndexName
                    );
                }, listener::onFailure));
            }

        }
    }

    private void createMemoryIndexes(
        MLMemoryContainer container,
        ActionListener<String> listener,
        MemoryConfiguration configuration,
        String workingMemoryIndexName,
        String longTermMemoryIndexName,
        String longTermMemoryHistoryIndexName
    ) {
        mlIndicesHandler.createWorkingMemoryDataIndex(workingMemoryIndexName, configuration, ActionListener.wrap(success -> {
            // Return the actual index name that was created
            // Create the memory data index with appropriate mapping
            createLongTermMemoryIngestPipeline(longTermMemoryIndexName, container.getConfiguration(), ActionListener.wrap(success1 -> {
                if (!configuration.isDisableHistory()) {
                    mlIndicesHandler
                        .createLongTermMemoryHistoryIndex(longTermMemoryHistoryIndexName, configuration, ActionListener.wrap(success2 -> {
                            listener.onResponse(longTermMemoryIndexName);
                        }, listener::onFailure));
                } else {
                    listener.onResponse(longTermMemoryIndexName);
                }
            }, listener::onFailure));
        }, listener::onFailure));
    }

    private void createLongTermMemoryIngestPipeline(String indexName, MemoryConfiguration memoryConfig, ActionListener<Boolean> listener) {
        MemoryContainerPipelineHelper.createLongTermMemoryIngestPipeline(indexName, memoryConfig, mlIndicesHandler, client, listener);
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
                        .refreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to index memory container", cause);
                        listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            assert indexResponse != null;
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                                String generatedId = indexResponse.getId();
                                log.info("Successfully created memory container with ID: {}", generatedId);
                                listener.onResponse(generatedId);
                            } else {
                                log
                                    .error(
                                        "Failed to create memory container - unexpected index response result: {}",
                                        indexResponse.getResult()
                                    );
                                listener
                                    .onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                            }
                        } catch (Exception e) {
                            log.error("Failed to process index response", e);
                            listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to save memory container", e);
            listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void validateConfiguration(MemoryConfiguration config, ActionListener<Boolean> listener) {
        // Validate that strategies have required AI models
        try {
            MemoryConfiguration.validateStrategiesRequireModels(config);
        } catch (IllegalArgumentException e) {
            log.error("Strategy validation failed: {}", e.getMessage());
            listener.onFailure(e);
            return;
        }

        // Validate strategy types and namespace using centralized validator
        if (config.getStrategies() != null) {
            for (MemoryStrategy strategy : config.getStrategies()) {
                try {
                    MemoryStrategy.validate(strategy);
                } catch (IllegalArgumentException e) {
                    log.error("Strategy validation failed: {}", e.getMessage());
                    listener.onFailure(e);
                    return;
                }
            }
        }

        // Validate LLM model using helper
        MemoryContainerModelValidator.validateLlmModel(config.getLlmId(), mlModelManager, client, ActionListener.wrap(isValid -> {
            // LLM model is valid, now validate embedding model
            MemoryContainerModelValidator
                .validateEmbeddingModel(
                    config.getEmbeddingModelId(),
                    config.getEmbeddingModelType(),
                    mlModelManager,
                    client,
                    ActionListener.wrap(embeddingValid -> {
                        // Both models are valid, now validate shared index compatibility
                        MemoryContainerSharedIndexValidator
                            .validateSharedIndexCompatibility(
                                config,
                                config.getLongMemoryIndexName(),
                                client,
                                ActionListener.wrap(result -> {
                                    // Validation successful
                                    listener.onResponse(true);
                                }, listener::onFailure)
                            );
                    }, listener::onFailure)
                );
        }, listener::onFailure));
    }

}
