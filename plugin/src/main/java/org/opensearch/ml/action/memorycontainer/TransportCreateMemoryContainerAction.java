/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.determineProtocol;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.BULK_LOAD_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.CREATE_INDEX_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.CREATE_INGEST_PIPELINE_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.DELETE_DOC_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.GET_DOC_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.REGISTER_MODEL_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.SEARCH_INDEX_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.UPDATE_DOC_ACTION;
import static org.opensearch.ml.helper.RemoteMemoryStoreHelper.WRITE_DOC_ACTION;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.RemoteEmbeddingModel;
import org.opensearch.ml.common.memorycontainer.RemoteStore;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerModelValidator;
import org.opensearch.ml.helper.MemoryContainerPipelineHelper;
import org.opensearch.ml.helper.MemoryContainerSharedIndexValidator;
import org.opensearch.ml.helper.RemoteMemoryStoreHelper;
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
    private final ClusterService clusterService;
    private final Settings settings;
    private final SdkClient sdkClient;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLModelManager mlModelManager;
    private final MLEngine mlEngine;
    private final RemoteMemoryStoreHelper remoteMemoryStoreHelper;
    private final MemoryContainerPipelineHelper memoryContainerPipelineHelper;
    private volatile List<String> trustedConnectorEndpointsRegex;

    @Inject
    public TransportCreateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        Settings settings,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager,
        MLEngine mlEngine,
        RemoteMemoryStoreHelper remoteMemoryStoreHelper,
        MemoryContainerPipelineHelper memoryContainerPipelineHelper
    ) {
        super(MLCreateMemoryContainerAction.NAME, transportService, actionFilters, MLCreateMemoryContainerRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.settings = settings;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
        this.mlEngine = mlEngine;
        this.remoteMemoryStoreHelper = remoteMemoryStoreHelper;
        trustedConnectorEndpointsRegex = ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(settings);
        this.memoryContainerPipelineHelper = memoryContainerPipelineHelper;
        this.clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX, it -> trustedConnectorEndpointsRegex = it);
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

        // Validate configuration before creating memory container
        validateConfiguration(tenantId, input.getConfiguration(), ActionListener.wrap(isValid -> {
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

        RemoteStore remoteStore = configuration.getRemoteStore();
        // Check if remote store is configured
        boolean useRemoteStore = remoteStore != null && (remoteStore.getConnectorId() != null || remoteStore.getConnector() != null);

        // Convert to lowercase as OpenSearch doesn't support uppercase in index names
        final String sessionIndexName = configuration.getSessionIndexName();
        final String workingMemoryIndexName = configuration.getWorkingMemoryIndexName();
        final String longTermMemoryIndexName = configuration.getLongMemoryIndexName();
        final String longTermMemoryHistoryIndexName = configuration.getLongMemoryHistoryIndexName();

        // Decision: strategies present = 4 indices (session/working/long-term/history)
        // No strategies = 2 indices (session/working only)
        if (configuration.getStrategies() == null || configuration.getStrategies().isEmpty()) {
            if (configuration.isDisableSession()) {
                if (useRemoteStore) {
                    createRemoteWorkingMemoryIndex(configuration, workingMemoryIndexName, ActionListener.wrap(success -> {
                        listener.onResponse(workingMemoryIndexName);
                    }, listener::onFailure));
                } else {
                    mlIndicesHandler.createWorkingMemoryDataIndex(workingMemoryIndexName, configuration, ActionListener.wrap(success -> {
                        listener.onResponse(workingMemoryIndexName);
                    }, listener::onFailure));
                }
            } else {
                if (useRemoteStore) {
                    createRemoteSessionMemoryIndex(configuration, sessionIndexName, ActionListener.wrap(result -> {
                        createRemoteWorkingMemoryIndex(configuration, workingMemoryIndexName, ActionListener.wrap(success -> {
                            listener.onResponse(workingMemoryIndexName);
                        }, listener::onFailure));
                    }, listener::onFailure));
                } else {
                    mlIndicesHandler.createSessionMemoryDataIndex(sessionIndexName, configuration, ActionListener.wrap(result -> {
                        mlIndicesHandler
                            .createWorkingMemoryDataIndex(workingMemoryIndexName, configuration, ActionListener.wrap(success -> {
                                listener.onResponse(workingMemoryIndexName);
                            }, listener::onFailure));
                    }, listener::onFailure));
                }
            }
        } else {
            if (configuration.isDisableSession()) {
                if (useRemoteStore) {
                    createRemoteMemoryIndexes(
                        container,
                        listener,
                        configuration,
                        workingMemoryIndexName,
                        longTermMemoryIndexName,
                        longTermMemoryHistoryIndexName
                    );
                } else {
                    createMemoryIndexes(
                        container,
                        listener,
                        configuration,
                        workingMemoryIndexName,
                        longTermMemoryIndexName,
                        longTermMemoryHistoryIndexName
                    );
                }
            } else {
                if (useRemoteStore) {
                    createRemoteSessionMemoryIndex(configuration, sessionIndexName, ActionListener.wrap(result -> {
                        createRemoteMemoryIndexes(
                            container,
                            listener,
                            configuration,
                            workingMemoryIndexName,
                            longTermMemoryIndexName,
                            longTermMemoryHistoryIndexName
                        );
                    }, listener::onFailure));
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
                // Return the actual index name that was created
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
        memoryContainerPipelineHelper.createLongTermMemoryIngestPipeline(indexName, memoryConfig, listener);
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

    private void validateConfiguration(String tenantId, MemoryConfiguration config, ActionListener<Boolean> listener) {
        if (config.getRemoteStore() != null && config.getRemoteStore().getConnector() != null) {
            if (config.getRemoteStore().getEmbeddingModel() != null
                && (config.getRemoteStore().getIngestPipeline() == null || config.getRemoteStore().getIngestPipeline().isEmpty())) {
                remoteMemoryStoreHelper
                    .createRemoteEmbeddingModel(
                        config.getRemoteStore().getConnector(),
                        config.getRemoteStore().getEmbeddingModel(),
                        config.getRemoteStore().getCredential(),
                        ActionListener.wrap(modelId -> {
                            // Set the embedding model ID in the remote store config
                            config.getRemoteStore().setEmbeddingModelId(modelId);
                            // Also set type and dimension from embedding model config
                            RemoteEmbeddingModel embModel = config.getRemoteStore().getEmbeddingModel();
                            config.getRemoteStore().setEmbeddingModelType(embModel.getModelType());
                            config.getRemoteStore().setEmbeddingDimension(embModel.getDimension());
                            log.info("Auto-created embedding model with ID: {} in remote store", modelId);
                            // Continue with normal validation
                            validateConfigurationInternal(tenantId, config, listener);
                        }, listener::onFailure)
                    );
            } else {
                if (config.getRemoteStore().getIngestPipeline() != null && !config.getRemoteStore().getIngestPipeline().isEmpty()) {
                    log
                        .info(
                            "Skipping embedding model auto-creation, using pre-existing ingest pipeline: {}",
                            config.getRemoteStore().getIngestPipeline()
                        );
                }
                // Continue with normal validation
                validateConfigurationInternal(tenantId, config, listener);
            }
            return;
        }
        // Check if we need to auto-create a connector
        if (config.getRemoteStore() != null
            && config.getRemoteStore().getConnectorId() == null
            && config.getRemoteStore().getEndpoint() != null) {
            // Auto-create connector first
            createInternalConnectorForRemoteStore(tenantId, config.getRemoteStore(), ActionListener.wrap(connector -> {
                // Set the connector ID in the remote store config
                config.getRemoteStore().setConnector(connector);

                // Check if we need to auto-create embedding model
                // Skip if user provided a pre-existing ingest pipeline
                if (config.getRemoteStore().getEmbeddingModel() != null
                    && (config.getRemoteStore().getIngestPipeline() == null || config.getRemoteStore().getIngestPipeline().isEmpty())) {
                    remoteMemoryStoreHelper
                        .createRemoteEmbeddingModel(
                            connector,
                            config.getRemoteStore().getEmbeddingModel(),
                            config.getRemoteStore().getCredential(),
                            ActionListener.wrap(modelId -> {
                                // Set the embedding model ID in the remote store config
                                config.getRemoteStore().setEmbeddingModelId(modelId);
                                // Also set type and dimension from embedding model config
                                RemoteEmbeddingModel embModel = config.getRemoteStore().getEmbeddingModel();
                                config.getRemoteStore().setEmbeddingModelType(embModel.getModelType());
                                config.getRemoteStore().setEmbeddingDimension(embModel.getDimension());
                                log.info("Auto-created embedding model with ID: {} in remote store", modelId);
                                // Continue with normal validation
                                validateConfigurationInternal(tenantId, config, listener);
                            }, listener::onFailure)
                        );
                } else {
                    if (config.getRemoteStore().getIngestPipeline() != null && !config.getRemoteStore().getIngestPipeline().isEmpty()) {
                        log
                            .info(
                                "Skipping embedding model auto-creation, using pre-existing ingest pipeline: {}",
                                config.getRemoteStore().getIngestPipeline()
                            );
                    }
                    // Continue with normal validation
                    validateConfigurationInternal(tenantId, config, listener);
                }
            }, listener::onFailure));
        } else {
            // Normal validation flow
            validateConfigurationInternal(tenantId, config, listener);
        }
    }

    private void validateConfigurationInternal(String tenantId, MemoryConfiguration config, ActionListener<Boolean> listener) {
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
        MemoryContainerModelValidator.validateLlmModel(tenantId, config.getLlmId(), mlModelManager, client, ActionListener.wrap(isValid -> {
            // LLM model is valid, now validate embedding model
            MemoryContainerModelValidator
                .validateEmbeddingModel(
                    tenantId,
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

    private void createRemoteSessionMemoryIndex(MemoryConfiguration configuration, String indexName, ActionListener<Boolean> listener) {
        Connector connector = configuration.getRemoteStore().getConnector();
        remoteMemoryStoreHelper.createRemoteSessionMemoryIndex(connector, indexName, configuration, listener);
    }

    private void createRemoteWorkingMemoryIndex(MemoryConfiguration configuration, String indexName, ActionListener<Boolean> listener) {
        Connector connector = configuration.getRemoteStore().getConnector();
        remoteMemoryStoreHelper.createRemoteWorkingMemoryIndex(connector, indexName, configuration, listener);
    }

    private void createRemoteLongTermMemoryHistoryIndex(
        MemoryConfiguration configuration,
        String indexName,
        ActionListener<Boolean> listener
    ) {
        Connector connector = configuration.getRemoteStore().getConnector();
        remoteMemoryStoreHelper.createRemoteLongTermMemoryHistoryIndex(connector, indexName, configuration, listener);
    }

    private void createRemoteMemoryIndexes(
        MLMemoryContainer container,
        ActionListener<String> listener,
        MemoryConfiguration configuration,
        String workingMemoryIndexName,
        String longTermMemoryIndexName,
        String longTermMemoryHistoryIndexName
    ) {
        createRemoteWorkingMemoryIndex(configuration, workingMemoryIndexName, ActionListener.wrap(success -> {
            // Create long-term memory index with pipeline if embedding is configured
            createRemoteLongTermMemoryIngestPipeline(configuration, longTermMemoryIndexName, ActionListener.wrap(success1 -> {
                if (!configuration.isDisableHistory()) {
                    createRemoteLongTermMemoryHistoryIndex(configuration, longTermMemoryHistoryIndexName, ActionListener.wrap(success2 -> {
                        listener.onResponse(longTermMemoryIndexName);
                    }, listener::onFailure));
                } else {
                    listener.onResponse(longTermMemoryIndexName);
                }
            }, listener::onFailure));
        }, listener::onFailure));
    }

    private void createRemoteLongTermMemoryIngestPipeline(
        MemoryConfiguration configuration,
        String indexName,
        ActionListener<Boolean> listener
    ) {
        memoryContainerPipelineHelper.createRemoteLongTermMemoryIngestPipeline(indexName, configuration, listener);
    }

    /**
     * Auto-creates a connector for remote store based on provided endpoint and credentials
     */
    private void createConnectorForRemoteStore(RemoteStore remoteStore, ActionListener<String> listener) {
        try {
            String connectorName = "auto_"
                + remoteStore.getType().name().toLowerCase()
                + "_connector_"
                + UUID.randomUUID().toString().substring(0, 8);

            // Build connector actions based on remote store type
            List<ConnectorAction> actions = buildConnectorActions(remoteStore);

            // Get credential and parameters from remote store
            Map<String, String> credential = remoteStore.getCredential();
            Map<String, String> parameters = remoteStore.getParameters();

            // Determine protocol based on parameters or credential
            String protocol = determineProtocol(parameters, credential);

            // Create connector input
            MLCreateConnectorInput connectorInput = MLCreateConnectorInput
                .builder()
                .name(connectorName)
                .description("Auto-generated connector for " + remoteStore.getType() + " remote memory store")
                .version("1")
                .protocol(protocol)
                .parameters(parameters)
                .credential(credential)
                .actions(actions)
                .build();

            // Create connector request
            org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest request =
                new org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest(connectorInput);

            // Execute connector creation
            client
                .execute(
                    org.opensearch.ml.common.transport.connector.MLCreateConnectorAction.INSTANCE,
                    request,
                    ActionListener.wrap(response -> {
                        log.info("Successfully created connector: {}", response.getConnectorId());
                        // // Store the connector object in remote store configuration
                        // org.opensearch.ml.common.connector.Connector connector = connectorInput.toConnector();
                        // // Encrypt connector credentials before storing (similar to indexRemoteModel in MLModelManager)
                        // String tenantId = remoteStore.getParameters() != null
                        // ? remoteStore.getParameters().get("tenant_id")
                        // : null;
                        // connector.encrypt(mlEngine::encrypt, tenantId);
                        // remoteStore.setConnector(connector);
                        listener.onResponse(response.getConnectorId());
                    }, e -> {
                        log.error("Failed to create connector for remote store", e);
                        listener.onFailure(e);
                    })
                );
        } catch (Exception e) {
            log.error("Error building connector for remote store", e);
            listener.onFailure(e);
        }
    }

    private void createInternalConnectorForRemoteStore(String tenantId, RemoteStore remoteStore, ActionListener<Connector> listener) {
        try {
            String connectorName = "auto_"
                + remoteStore.getType().name().toLowerCase()
                + "_connector_"
                + UUID.randomUUID().toString().substring(0, 8);

            // Build connector actions based on remote store type
            List<ConnectorAction> actions = buildConnectorActions(remoteStore);

            // Get credential and parameters from remote store
            Map<String, String> credential = remoteStore.getCredential();
            Map<String, String> parameters = remoteStore.getParameters();

            // Determine protocol based on parameters or credential
            String protocol = determineProtocol(parameters, credential);

            // Create connector input
            MLCreateConnectorInput connectorInput = MLCreateConnectorInput
                .builder()
                .name(connectorName)
                .description("Auto-generated connector for " + remoteStore.getType() + " remote memory store")
                .version("1")
                .protocol(protocol)
                .parameters(parameters)
                .credential(credential)
                .actions(actions)
                .tenantId(tenantId)
                .build();

            XContentBuilder builder = XContentFactory.jsonBuilder();
            connectorInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
            Connector connector = Connector.createConnector(builder, connectorInput.getProtocol());
            connector.validateConnectorURL(trustedConnectorEndpointsRegex);
            connector.encrypt(mlEngine::encrypt, tenantId);
            listener.onResponse(connector);
        } catch (Exception e) {
            log.error("Error building connector for remote store", e);
            listener.onFailure(e);
        }
    }

    /**
     * Builds connector actions based on remote store type
     */
    private List<ConnectorAction> buildConnectorActions(RemoteStore remoteStore) {
        List<ConnectorAction> actions = new ArrayList<>();
        String endpoint = remoteStore.getEndpoint();
        Map<String, String> parameters = remoteStore.getParameters();
        Map<String, String> credential = remoteStore.getCredential();

        // Determine if AWS SigV4 or basic auth
        String protocol = determineProtocol(parameters, credential);

        // Common headers for JSON
        Map<String, String> jsonHeaders = new HashMap<>();
        jsonHeaders.put("content-type", "application/json");
        boolean isAwsSigV4 = "aws_sigv4".equals(protocol);
        if (isAwsSigV4) {
            jsonHeaders.put("x-amz-content-sha256", "required");
        } else { // TODO: add more auth options
            jsonHeaders.put("Authorization", "Basic ${credential.basic_auth_key}");
        }

        // Register model action - for creating embedding models in remote AOSS
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(REGISTER_MODEL_ACTION)
                    .method("POST")
                    .url(endpoint + "/_plugins/_ml/models/_register")
                    .headers(jsonHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Create ingest pipeline action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(CREATE_INGEST_PIPELINE_ACTION)
                    .method("PUT")
                    .url(endpoint + "/_ingest/pipeline/${parameters.pipeline_name}")
                    .headers(jsonHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Create index action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(CREATE_INDEX_ACTION)
                    .method("PUT")
                    .url(endpoint + "/${parameters.index_name}")
                    .headers(jsonHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Write doc action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(WRITE_DOC_ACTION)
                    .method("POST")
                    .url(endpoint + "/${parameters.index_name}/_doc")
                    .headers(jsonHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Bulk load action
        Map<String, String> bulkHeaders = new HashMap<>();
        bulkHeaders.put("content-type", "application/x-ndjson");
        if (isAwsSigV4) {
            bulkHeaders.put("x-amz-content-sha256", "required");
        } else {
            bulkHeaders.put("Authorization", "Basic ${credential.basic_auth_key}");
        }

        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(BULK_LOAD_ACTION)
                    .method("POST")
                    .url(endpoint + "/_bulk")
                    .headers(bulkHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Search index action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(SEARCH_INDEX_ACTION)
                    .method("GET")
                    .url(endpoint + "/${parameters.index_name}/_search${parameters.search_pipeline:-}")
                    .headers(jsonHeaders)
                    .requestBody("${parameters.input}")
                    .build()
            );

        // Get doc action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(GET_DOC_ACTION)
                    .method("GET")
                    .url(endpoint + "/${parameters.index_name}/_doc/${parameters.doc_id}")
                    .headers(jsonHeaders)
                    .build()
            );

        // Delete doc action
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(DELETE_DOC_ACTION)
                    .method("DELETE")
                    .url(endpoint + "/${parameters.index_name}/_doc/${parameters.doc_id}")
                    .headers(jsonHeaders)
                    .build()
            );

        // Update doc action - POST /_update/<doc_id> works on both OpenSearch and AOSS
        // Uses partial update with "doc" wrapper for flexibility
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.EXECUTE)
                    .name(UPDATE_DOC_ACTION)
                    .method("POST")
                    .url(endpoint + "/${parameters.index_name}/_update/${parameters.doc_id}")
                    .headers(jsonHeaders)
                    .requestBody("{ \"doc\": ${parameters.input:-} }")
                    .build()
            );

        return actions;
    }

}
