/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_STORAGE_CONFIG_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAME_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.helper.MemoryContainerModelValidator;
import org.opensearch.ml.helper.MemoryContainerPipelineHelper;
import org.opensearch.ml.helper.MemoryContainerSharedIndexValidator;
import org.opensearch.ml.helper.StrategyMergeHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportUpdateMemoryContainerAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;
    final MemoryContainerHelper memoryContainerHelper;
    final MLIndicesHandler mlIndicesHandler;

    @Inject
    public TransportUpdateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager,
        MemoryContainerHelper memoryContainerHelper,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(MLUpdateMemoryContainerAction.NAME, transportService, actionFilters, MLUpdateMemoryContainerRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
        this.memoryContainerHelper = memoryContainerHelper;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN)
                );
            return;
        }

        MLUpdateMemoryContainerRequest mlUpdateMemoryContainerRequest = MLUpdateMemoryContainerRequest.fromActionRequest(request);
        String memoryContainerId = mlUpdateMemoryContainerRequest.getMemoryContainerId();
        String newName = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getName();
        String newDescription = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getDescription();
        List<String> allowedBackendRoles = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getBackendRoles();
        MemoryConfiguration updateConfiguration = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getConfiguration();

        // Get memory container to validate access
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate access permissions
            User user = RestActionUtils.getUserContext(client);
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to update memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Prepare the update
            Map<String, Object> updateFields = new HashMap<>();
            if (newName != null) {
                updateFields.put(NAME_FIELD, newName);
            }
            if (newDescription != null) {
                updateFields.put(DESCRIPTION_FIELD, newDescription);
            }
            if (allowedBackendRoles != null) {
                updateFields.put(BACKEND_ROLES_FIELD, allowedBackendRoles);
            }

            // Handle configuration updates
            if (updateConfiguration != null) {
                try {
                    MemoryConfiguration currentConfig = container.getConfiguration();

                    // Validate embedding update (prevent changing existing embedding configuration)
                    validateEmbeddingUpdate(currentConfig, updateConfiguration);

                    // Capture original state BEFORE any updates (to detect transition)
                    boolean hadNoStrategies = currentConfig.getStrategies() == null || currentConfig.getStrategies().isEmpty();

                    // Merge strategies if provided
                    final MemoryConfiguration finalUpdateConfig;
                    if (updateConfiguration.getStrategies() != null && !updateConfiguration.getStrategies().isEmpty()) {
                        List<MemoryStrategy> mergedStrategies = StrategyMergeHelper
                            .mergeStrategies(currentConfig.getStrategies(), updateConfiguration.getStrategies());
                        // Create a new configuration with merged strategies for update
                        // IMPORTANT: Preserve embedding fields from update request
                        finalUpdateConfig = MemoryConfiguration
                            .builder()
                            .llmId(updateConfiguration.getLlmId())
                            .maxInferSize(updateConfiguration.getMaxInferSize())
                            .strategies(mergedStrategies)
                            .embeddingModelId(updateConfiguration.getEmbeddingModelId())
                            .embeddingModelType(updateConfiguration.getEmbeddingModelType())
                            .dimension(updateConfiguration.getDimension())
                            .build();
                    } else {
                        finalUpdateConfig = updateConfiguration;
                    }

                    // Update current configuration with non-null values from finalUpdateConfig
                    currentConfig.update(finalUpdateConfig);

                    // Validate the merged configuration satisfies all input constraints
                    currentConfig.validate();

                    // Validate the merged configuration has required AI models for strategies
                    MemoryConfiguration.validateStrategiesRequireModels(currentConfig);

                    // Detect no-strategies → has-strategies transition (hadNoStrategies captured before update)
                    boolean nowHasStrategies = currentConfig.getStrategies() != null && !currentConfig.getStrategies().isEmpty();

                    if (hadNoStrategies && nowHasStrategies) {
                        // Transition detected - must create long-term and history indices
                        validateAndCreateIndices(container, currentConfig, updateFields, memoryContainerId, actionListener);
                        return; // Early return - validateAndCreateIndices will call performUpdate
                    }

                    // No transition - proceed with normal update
                    updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, currentConfig);
                } catch (Exception e) {
                    log.error("Failed to update configuration for container {}", memoryContainerId, e);
                    actionListener.onFailure(e);
                    return;
                }
            }

            updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
            performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, actionListener);
        }, actionListener::onFailure));
    }

    /**
     * Validates that embedding configuration updates are allowed.
     *
     * Rules:
     * - If container has NO strategies: Embedding updates are allowed (no long-term index exists)
     * - If container HAS strategies: Embedding cannot be changed (long-term index exists with locked mapping)
     * - Idempotent updates (same values) are always allowed
     *
     * @param currentConfig Current memory configuration
     * @param updateConfig Update request configuration
     * @throws IllegalArgumentException if attempting to change existing embedding when strategies exist
     */
    private void validateEmbeddingUpdate(MemoryConfiguration currentConfig, MemoryConfiguration updateConfig) {
        // If current container has no strategies, allow embedding config updates
        // No long-term memory index exists yet, so no conflict
        if (currentConfig.getStrategies() == null || currentConfig.getStrategies().isEmpty()) {
            log.debug("Container has no strategies - allowing embedding configuration update");
            return;
        }

        // Container HAS strategies - long-term index exists
        // Apply strict validation to prevent changing existing embedding config
        boolean currentHasEmbedding = currentConfig.getEmbeddingModelId() != null;
        boolean updateHasEmbedding = updateConfig.getEmbeddingModelId() != null
            || updateConfig.getEmbeddingModelType() != null
            || updateConfig.getDimension() != null;

        if (currentHasEmbedding && updateHasEmbedding) {
            // Check if trying to change to different values
            boolean idChanged = updateConfig.getEmbeddingModelId() != null
                && !updateConfig.getEmbeddingModelId().equals(currentConfig.getEmbeddingModelId());
            boolean typeChanged = updateConfig.getEmbeddingModelType() != null
                && !updateConfig.getEmbeddingModelType().equals(currentConfig.getEmbeddingModelType());
            boolean dimensionChanged = updateConfig.getDimension() != null
                && !updateConfig.getDimension().equals(currentConfig.getDimension());

            if (idChanged || typeChanged || dimensionChanged) {
                throw new IllegalArgumentException(
                    "Cannot change embedding configuration once strategies are configured. "
                        + "The long-term memory index already exists with specific embedding mappings. "
                        + "Current: {embedding_model_id="
                        + currentConfig.getEmbeddingModelId()
                        + ", embedding_model_type="
                        + currentConfig.getEmbeddingModelType()
                        + ", dimension="
                        + currentConfig.getDimension()
                        + "}. "
                        + "Create a new memory container if you need different embedding configuration."
                );
            }
        }
    }

    private void performUpdate(
        String indexName,
        String memoryContainerId,
        Map<String, Object> updateFields,
        ActionListener<UpdateResponse> listener
    ) {
        UpdateRequest updateRequest = new UpdateRequest(indexName, memoryContainerId).doc(updateFields);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
        } catch (Exception e) {
            log.error("Failed to update memory container {}", memoryContainerId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Validates models and creates long-term/history indices when adding strategies.
     * Entry point for validation chain - validates LLM and embedding models, then creates indices.
     */
    private void validateAndCreateIndices(
        MLMemoryContainer container,
        MemoryConfiguration config,
        Map<String, Object> updateFields,
        String memoryContainerId,
        ActionListener<UpdateResponse> listener
    ) {
        // Validate LLM model using helper
        MemoryContainerModelValidator.validateLlmModel(config.getLlmId(), mlModelManager, client, ActionListener.wrap(llmValid -> {
            // LLM validated, now validate embedding model
            MemoryContainerModelValidator
                .validateEmbeddingModel(
                    config.getEmbeddingModelId(),
                    config.getEmbeddingModelType(),
                    mlModelManager,
                    client,
                    ActionListener.wrap(embeddingValid -> {
                        // Both models validated, proceed to shared index validation and creation
                        validateSharedIndexAndCreateIndices(container, config, updateFields, memoryContainerId, listener);
                    }, listener::onFailure)
                );
        }, listener::onFailure));
    }

    /**
     * Validates shared index compatibility and creates indices.
     * Checks if long-term index already exists (shared scenario).
     */
    private void validateSharedIndexAndCreateIndices(
        MLMemoryContainer container,
        MemoryConfiguration config,
        Map<String, Object> updateFields,
        String memoryContainerId,
        ActionListener<UpdateResponse> listener
    ) {
        String longTermIndexName = config.getLongMemoryIndexName();

        // Use helper to validate shared index compatibility
        MemoryContainerSharedIndexValidator
            .validateSharedIndexCompatibility(config, longTermIndexName, client, ActionListener.wrap(result -> {
                if (result.isIndexExists() && result.isCompatible()) {
                    // Index exists and is compatible - only create history index
                    createHistoryIndexOnly(config, updateFields, memoryContainerId, listener);
                } else if (!result.isIndexExists()) {
                    // Index doesn't exist - create both long-term and history indices
                    createLongTermAndHistoryIndices(container, config, updateFields, memoryContainerId, listener);
                } else {
                    // Index exists but is incompatible (should not reach here as validator throws exception)
                    listener.onFailure(new IllegalStateException("Unexpected validation state: index exists but compatibility is false"));
                }
            }, listener::onFailure));
    }

    /**
     * Creates only history index (long-term index already exists in shared scenario).
     */
    private void createHistoryIndexOnly(
        MemoryConfiguration config,
        Map<String, Object> updateFields,
        String memoryContainerId,
        ActionListener<UpdateResponse> listener
    ) {
        if (!config.isDisableHistory()) {
            String historyIndexName = config.getLongMemoryHistoryIndexName();

            mlIndicesHandler.createLongTermMemoryHistoryIndex(historyIndexName, config, ActionListener.wrap(success -> {
                updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, config);
                updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
                performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, listener);
            }, e -> {
                log.error("Failed to create history index '{}': {}", historyIndexName, e.getMessage(), e);
                listener.onFailure(new IllegalStateException("Failed to create history index: " + e.getMessage()));
            }));
        } else {
            updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, config);
            updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
            performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, listener);
        }
    }

    /**
     * Creates both long-term and history indices (fresh creation scenario).
     */
    private void createLongTermAndHistoryIndices(
        MLMemoryContainer container,
        MemoryConfiguration config,
        Map<String, Object> updateFields,
        String memoryContainerId,
        ActionListener<UpdateResponse> listener
    ) {
        String longTermIndexName = config.getLongMemoryIndexName();
        String historyIndexName = config.getLongMemoryHistoryIndexName();

        // Create ingest pipeline and long-term index
        createLongTermMemoryIngestPipeline(longTermIndexName, config, ActionListener.wrap(success -> {
            // Create history index if not disabled
            if (!config.isDisableHistory()) {
                mlIndicesHandler.createLongTermMemoryHistoryIndex(historyIndexName, config, ActionListener.wrap(historySuccess -> {
                    updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, config);
                    updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
                    performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, listener);
                }, e -> {
                    log.error("Failed to create history index '{}': {}", historyIndexName, e.getMessage(), e);
                    listener.onFailure(new IllegalStateException("Failed to create history index: " + e.getMessage()));
                }));
            } else {
                updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, config);
                updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
                performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, listener);
            }
        }, e -> {
            log.error("Failed to create long-term index '{}': {}", longTermIndexName, e.getMessage(), e);
            listener.onFailure(new IllegalStateException("Failed to create long-term index: " + e.getMessage()));
        }));
    }

    /**
     * Creates ingest pipeline and long-term index.
     */
    private void createLongTermMemoryIngestPipeline(String indexName, MemoryConfiguration config, ActionListener<Boolean> listener) {
        MemoryContainerPipelineHelper.createLongTermMemoryIngestPipeline(indexName, config, mlIndicesHandler, client, listener);
    }
}
