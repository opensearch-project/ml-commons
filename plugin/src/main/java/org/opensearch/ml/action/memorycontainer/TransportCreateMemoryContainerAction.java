/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_MISMATCH_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_REMOTE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.ingest.PutPipelineRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
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
        validateModels(input.getConfiguration(), ActionListener.wrap(isValid -> {
            // Check if memory container index exists, create if not
            ActionListener<Boolean> indexCheckListener = ActionListener.wrap(created -> {
                try {
                    // Create memory container document without ID (will be auto-generated)
                    MLMemoryContainer memoryContainer = buildMemoryContainer(input, user, tenantId);

                    // Index the memory container document first to get the generated ID
                    indexMemoryContainer(memoryContainer, ActionListener.wrap(memoryContainerId -> {
                        // Create memory data indices based on semantic storage config
                        createMemoryDataIndices(memoryContainer, user, ActionListener.wrap(actualIndexName -> {
                            listener.onResponse(new MLCreateMemoryContainerResponse(memoryContainerId, "created"));
                        }, listener::onFailure));
                    }, listener::onFailure));

                } catch (Exception e) {
                    log.error("Failed to create memory container", e);
                    listener.onFailure(e);
                }
            }, listener::onFailure);

            // Initialize memory container index if it doesn't exist
            mlIndicesHandler.initMemoryContainerIndex(indexCheckListener);
        }, listener::onFailure));
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
            .configuration(input.getConfiguration())
            .build();
    }

    private void createMemoryDataIndices(
        MLMemoryContainer container,
        User user,
        ActionListener<String> listener
    ) {
        String userId = user != null ? user.getName() : "default";
        MemoryConfiguration configuration = container.getConfiguration();
        String indexPrefix = configuration != null ? configuration.getIndexPrefix() : null;

        // Convert to lowercase as OpenSearch doesn't support uppercase in index names
        final String sessionIndexName = indexPrefix.toLowerCase(Locale.ROOT) + "-session";
        final String shortTermMemoryIndexName = indexPrefix.toLowerCase(Locale.ROOT) + "-short-term-memory";
        final String longTermMemoryIndexName = indexPrefix.toLowerCase(Locale.ROOT) + "-long-term-memory";
        final String longTermMemoryHistoryIndexName = configuration.getLongMemoryHistoryIndexName();

        mlIndicesHandler.createSessionMemoryDataIndex(sessionIndexName, ActionListener.wrap(result -> {
            mlIndicesHandler.createShortTermMemoryDataIndex(shortTermMemoryIndexName, ActionListener.wrap(success -> {
                // Return the actual index name that was created
                // Create the memory data index with appropriate mapping
                createLongTermMemoryIngestPipeline(longTermMemoryIndexName, container.getConfiguration(), ActionListener.wrap(success1 -> {
                    // Return the actual index name that was created
                    mlIndicesHandler.createLongTermMemoryHistoryIndex(longTermMemoryHistoryIndexName, ActionListener.wrap(success2 -> {
                        listener.onResponse(longTermMemoryIndexName);
                    }, listener::onFailure));
                }, listener::onFailure));
            }, listener::onFailure));
        }, listener::onFailure));
    }

    private void createLongTermMemoryIngestPipeline(String indexName, MemoryConfiguration memoryConfig, ActionListener<Boolean> listener) {
        try {
            if (memoryConfig.getEmbeddingModelType() != null) {
                String pipelineName = indexName + "-embedding";
                createTextEmbeddingPipeline(pipelineName, memoryConfig, ActionListener.wrap(success -> {
                    log.info("Successfully created text embedding pipeline: {}", indexName + "-embedding");
                    mlIndicesHandler.createLongTermMemoryIndex(pipelineName, indexName, memoryConfig, listener);
                }, e -> {
                    log.error("Failed to create text embedding pipeline", e);
                }));
            } else {
                mlIndicesHandler.createLongTermMemoryIndex(null, indexName, memoryConfig, listener);
            }
        } catch (Exception e) {
            log.error("Failed to create memory data index", e);
            listener.onFailure(e);
        }
    }

    private void createTextEmbeddingPipeline(String pipelineName, MemoryConfiguration memoryConfig,
                                             ActionListener<Boolean> listener) throws IOException {
        String processorName = memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING? "text_embedding":"sparse_encoding";
        XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("description", "Agentic Memory Text embedding pipeline")
                .startArray("processors")
                .startObject()
                .startObject(processorName)
                .field("model_id", memoryConfig.getEmbeddingModelId())
                .startObject("field_map")
                .field(MEMORY_FIELD, MEMORY_EMBEDDING_FIELD)
                .endObject()
                .endObject()
                .endObject()
                .endArray()
                .endObject();

        BytesReference source = BytesReference.bytes(builder);
        PutPipelineRequest request = new PutPipelineRequest(pipelineName, source, XContentType.JSON);

        client.admin().cluster().putPipeline(request, ActionListener.wrap(
                response -> {
                    log.info("Pipeline created: {}", pipelineName);
                    listener.onResponse(true);
                },
                error -> {
                    log.error("Failed to create pipeline", error);
                    listener.onFailure(error);
                }
        ));
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

    private void validateModels(MemoryConfiguration config, ActionListener<Boolean> listener) {
        if (config == null || !config.isSemanticStorageEnabled()) {
            listener.onResponse(true);
            return;
        }

        // Validate LLM model first
        if (config.getLlmId() != null) {
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
                    log.error("Failed to get LLM model: {}", config.getLlmId(), e);
                    listener.onFailure(new IllegalArgumentException(String.format(LLM_MODEL_NOT_FOUND_ERROR, config.getLlmId())));
                }), context::restore);

                mlModelManager.getModel(config.getLlmId(), wrappedListener);
            }
        } else {
            // No LLM model specified, just validate embedding model
            validateEmbeddingModel(config, listener);
        }
    }

    private void validateEmbeddingModel(MemoryConfiguration config, ActionListener<Boolean> listener) {
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
