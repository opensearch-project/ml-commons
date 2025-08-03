/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryCharacteristic;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportAddMemoryAction extends HandledTransportAction<MLAddMemoryRequest, MLAddMemoryResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportAddMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAddMemoryAction.NAME, transportService, actionFilters, MLAddMemoryRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLAddMemoryRequest request, ActionListener<MLAddMemoryResponse> actionListener) {
        MLAddMemoryInput input = request.getMlAddMemoryInput();
        String memoryContainerId = input.getMemoryContainerId();

        // Get memory container first
        getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Check user has access to container
            User user = RestActionUtils.getUserContext(client);
            if (!checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have privilege to perform this operation on this memory container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Validate and determine infer value based on semantic storage
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            boolean isSemanticEnabled = storageConfig != null && storageConfig.isSemanticStorageEnabled();
            Boolean infer = input.getInfer();

            if (!isSemanticEnabled) {
                // Semantic storage disabled: infer must be false or null
                if (infer != null && infer) {
                    actionListener.onFailure(new IllegalArgumentException(INFER_NOT_ALLOWED_NON_SEMANTIC_ERROR));
                    return;
                }
                infer = false; // Default to false
            } else {
                // Semantic storage enabled: default to true if not specified
                if (infer == null) {
                    infer = true;
                }
            }

            // Auto-determine memory type and characteristic based on infer
            MemoryType memoryType = input.getMemoryType();
            MemoryCharacteristic memoryCharacteristic;

            if (!infer) {
                // When infer is false: only RAW_MESSAGE allowed, characteristic is LONG_TERM
                if (memoryType != null && memoryType != MemoryType.RAW_MESSAGE) {
                    actionListener.onFailure(new IllegalArgumentException("When infer is false, only RAW_MESSAGE memory type is allowed"));
                    return;
                }
                memoryType = MemoryType.RAW_MESSAGE;
                memoryCharacteristic = MemoryCharacteristic.LONG_TERM;
            } else {
                // When infer is true: default to RAW_MESSAGE, characteristic is SHORT_TERM
                if (memoryType == null) {
                    memoryType = MemoryType.RAW_MESSAGE;
                }
                memoryCharacteristic = MemoryCharacteristic.SHORT_TERM;
            }

            // Generate IDs - memory_id now serves as the unique message identifier
            String memoryId = input.getMemoryId();
            String sessionId = input.getSessionId();

            if (memoryId == null) {
                memoryId = UUID.randomUUID().toString();
            }

            // Make memoryId final for lambda usage
            final String finalMemoryId = memoryId;
            final MemoryType finalMemoryType = memoryType;
            final MemoryCharacteristic finalMemoryCharacteristic = memoryCharacteristic;

            // Get index name from container
            String indexName = getIndexName(container);
            if (indexName == null) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("Memory container does not have a valid index name", RestStatus.INTERNAL_SERVER_ERROR)
                    );
                return;
            }

            // If sessionId not provided but memoryId exists, look up its sessionId
            if (sessionId == null && input.getMemoryId() != null) {
                searchSessionId(indexName, finalMemoryId, ActionListener.wrap(existingSessionId -> {
                    String finalSessionId = existingSessionId != null ? existingSessionId : "sess_" + UUID.randomUUID().toString();
                    addMemoryWithSessionId(
                        input,
                        container,
                        indexName,
                        finalMemoryId,
                        finalSessionId,
                        user,
                        finalMemoryType,
                        finalMemoryCharacteristic,
                        actionListener
                    );
                }, e -> {
                    // If search fails, generate new session ID
                    String finalSessionId = "sess_" + UUID.randomUUID().toString();
                    addMemoryWithSessionId(
                        input,
                        container,
                        indexName,
                        finalMemoryId,
                        finalSessionId,
                        user,
                        finalMemoryType,
                        finalMemoryCharacteristic,
                        actionListener
                    );
                }));
            } else {
                if (sessionId == null) {
                    sessionId = "sess_" + UUID.randomUUID().toString();
                }
                addMemoryWithSessionId(
                    input,
                    container,
                    indexName,
                    finalMemoryId,
                    sessionId,
                    user,
                    finalMemoryType,
                    finalMemoryCharacteristic,
                    actionListener
                );
            }
        }, actionListener::onFailure));
    }

    private void addMemoryWithSessionId(
        MLAddMemoryInput input,
        MLMemoryContainer container,
        String indexName,
        String memoryId,
        String sessionId,
        User user,
        MemoryType memoryType,
        MemoryCharacteristic memoryCharacteristic,
        ActionListener<MLAddMemoryResponse> actionListener
    ) {
        try {
            // Check if we need to generate embeddings
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

            if (needsEmbedding) {
                // Generate embedding first, then save memory
                generateEmbedding(input.getMemory(), storageConfig, ActionListener.wrap(embedding -> {
                    saveMemoryWithEmbedding(
                        input,
                        container,
                        indexName,
                        memoryId,
                        sessionId,
                        user,
                        memoryType,
                        memoryCharacteristic,
                        embedding,
                        actionListener
                    );
                }, e -> {
                    log.error("Failed to generate embedding, saving memory without embedding", e);
                    // Save without embedding on failure
                    saveMemoryWithEmbedding(
                        input,
                        container,
                        indexName,
                        memoryId,
                        sessionId,
                        user,
                        memoryType,
                        memoryCharacteristic,
                        null,
                        actionListener
                    );
                }));
            } else {
                // No embedding needed, save directly
                saveMemoryWithEmbedding(
                    input,
                    container,
                    indexName,
                    memoryId,
                    sessionId,
                    user,
                    memoryType,
                    memoryCharacteristic,
                    null,
                    actionListener
                );
            }
        } catch (Exception e) {
            log.error("Failed to add memory", e);
            actionListener.onFailure(e);
        }
    }

    private void saveMemoryWithEmbedding(
        MLAddMemoryInput input,
        MLMemoryContainer container,
        String indexName,
        String memoryId,
        String sessionId,
        User user,
        MemoryType memoryType,
        MemoryCharacteristic memoryCharacteristic,
        Object embedding,
        ActionListener<MLAddMemoryResponse> actionListener
    ) {
        // Build memory object
        Instant now = Instant.now();
        MLMemory memory = MLMemory
            .builder()
            .memoryId(memoryId)
            .sessionId(sessionId)
            .memory(input.getMemory())
            .memoryType(memoryType)
            .memoryCharacteristic(memoryCharacteristic)
            .userId(user != null ? user.getName() : null)
            .agentId(input.getAgentId())
            .role(input.getRole())
            .tags(input.getTags())
            .createdTime(now)
            .lastUpdatedTime(now)
            .memoryEmbedding(embedding)
            .build();

        // Index the memory
        IndexRequest indexRequest = new IndexRequest(indexName)
            .id(memoryId)
            .source(memory.toIndexMap())
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
            log.info("Successfully indexed message {} in session {} to index {}", memoryId, sessionId, indexName);
            // Check if we need to delete old short-term memories
            int maxShortTermMemories = getMaxShortTermMemories(container);
            checkAndDeleteOldShortTermMemories(indexName, sessionId, maxShortTermMemories, ActionListener.wrap(deleted -> {
                MLAddMemoryResponse response = MLAddMemoryResponse
                    .builder()
                    .memoryId(memoryId)
                    .sessionId(sessionId)
                    .status("created")
                    .build();
                actionListener.onResponse(response);
            }, e -> {
                log.error("Failed to delete old messages but memory was added successfully", e);
                // Still return success as the memory was added
                MLAddMemoryResponse response = MLAddMemoryResponse
                    .builder()
                    .memoryId(memoryId)
                    .sessionId(sessionId)
                    .status("created")
                    .build();
                actionListener.onResponse(response);
            }));
        }, actionListener::onFailure));
    }

    private void searchSessionId(String indexName, String memoryId, ActionListener<String> listener) {
        // Use get request with document ID instead of search
        GetRequest getRequest = new GetRequest(indexName, memoryId)
            .fetchSourceContext(new FetchSourceContext(true, new String[] { SESSION_ID_FIELD }, null));

        client.get(getRequest, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                Map<String, Object> source = getResponse.getSourceAsMap();
                String sessionId = (String) source.get(SESSION_ID_FIELD);
                listener.onResponse(sessionId);
            } else {
                listener.onResponse(null);
            }
        }, listener::onFailure));
    }

    private void checkAndDeleteOldShortTermMemories(
        String indexName,
        String sessionId,
        int maxShortTermMemories,
        ActionListener<Boolean> listener
    ) {
        log.info("Checking short-term memories for session: {}, max allowed: {}", sessionId, maxShortTermMemories);

        // Count SHORT_TERM messages with this session_id
        SearchRequest countRequest = new SearchRequest(indexName)
            .source(
                new SearchSourceBuilder()
                    .query(
                        QueryBuilders
                            .boolQuery()
                            .must(QueryBuilders.termQuery(SESSION_ID_FIELD, sessionId))
                            .must(QueryBuilders.termQuery(MEMORY_CHARACTERISTIC_FIELD, MemoryCharacteristic.SHORT_TERM.getValue()))
                    )
                    .size(0)
            );

        log.debug("Executing count query for session_id: {} on index: {}", sessionId, indexName);

        client.search(countRequest, ActionListener.wrap(countResponse -> {
            long totalCount = countResponse.getHits().getTotalHits().value();
            log.info("Found {} short-term memories in session {}", totalCount, sessionId);

            // Delete if we have more short-term memories than allowed
            if (totalCount > maxShortTermMemories) {
                // Calculate how many to delete to keep exactly maxShortTermMemories
                int toDelete = (int) (totalCount - maxShortTermMemories);
                log.info("Deleting {} oldest short-term memories from session {}", toDelete, sessionId);
                SearchRequest searchOldest = new SearchRequest(indexName)
                    .source(
                        new SearchSourceBuilder()
                            .query(
                                QueryBuilders
                                    .boolQuery()
                                    .must(QueryBuilders.termQuery(SESSION_ID_FIELD, sessionId))
                                    .must(QueryBuilders.termQuery(MEMORY_CHARACTERISTIC_FIELD, MemoryCharacteristic.SHORT_TERM.getValue()))
                            )
                            .sort(CREATED_TIME_FIELD, SortOrder.ASC)
                            .size(toDelete)
                            .fetchSource(false)
                    );

                client.search(searchOldest, ActionListener.wrap(searchResponse -> {
                    BulkRequest bulkRequest = new BulkRequest();
                    bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                    for (SearchHit hit : searchResponse.getHits().getHits()) {
                        log.debug("Marking short-term memory {} for deletion from session {}", hit.getId(), sessionId);
                        bulkRequest.add(new DeleteRequest(indexName, hit.getId()));
                    }

                    if (bulkRequest.numberOfActions() > 0) {
                        client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
                            if (bulkResponse.hasFailures()) {
                                log.error("Some old short-term memories failed to delete: {}", bulkResponse.buildFailureMessage());
                            } else {
                                log
                                    .info(
                                        "Successfully deleted {} short-term memories from session {}",
                                        bulkRequest.numberOfActions(),
                                        sessionId
                                    );
                            }
                            listener.onResponse(true);
                        }, e -> {
                            log.error("Failed to delete old messages", e);
                            listener.onFailure(e);
                        }));
                    } else {
                        listener.onResponse(true);
                    }
                }, listener::onFailure));
            } else {
                log
                    .debug(
                        "No deletion needed for session {}: {} short-term memories <= {} max",
                        sessionId,
                        totalCount,
                        maxShortTermMemories
                    );
                listener.onResponse(true);
            }
        }, listener::onFailure));
    }

    private void generateEmbedding(String message, MemoryStorageConfig storageConfig, ActionListener<Object> listener) {
        if (storageConfig == null || !storageConfig.isSemanticStorageEnabled()) {
            listener.onResponse(null);
            return;
        }

        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        if (embeddingModelId == null || embeddingModelType == null) {
            log.error("Embedding model configuration is missing");
            listener.onResponse(null);
            return;
        }

        // Create MLInput for text embedding
        MLInput mlInput = MLInput
            .builder()
            .algorithm(embeddingModelType)
            .inputDataset(TextDocsInputDataSet.builder().docs(Arrays.asList(message)).build())
            .build();

        // Create prediction request
        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(embeddingModelId).mlInput(mlInput).build();

        // Execute prediction
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                MLOutput mlOutput = response.getOutput();
                if (mlOutput instanceof ModelTensorOutput) {
                    ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                    Object embedding = extractEmbedding(tensorOutput, embeddingModelType);
                    listener.onResponse(embedding);
                } else {
                    log.error("Unexpected ML output type: {}", mlOutput.getClass().getName());
                    listener.onResponse(null);
                }
            } catch (Exception e) {
                log.error("Failed to extract embedding from ML output", e);
                listener.onResponse(null);
            }
        }, e -> {
            log.error("Failed to generate embedding", e);
            listener.onResponse(null);
        }));
    }

    private Object extractEmbedding(ModelTensorOutput tensorOutput, FunctionName embeddingModelType) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

        if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
            // For dense embeddings, look for the sentence_embedding tensor
            for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
                if ("sentence_embedding".equals(tensor.getName()) && tensor.getData() != null) {
                    Number[] data = tensor.getData();
                    log.debug("Found sentence_embedding tensor with dimension: {}", data.length);

                    // Convert Number[] to float[] for proper storage
                    float[] floatData = new float[data.length];
                    for (int i = 0; i < data.length; i++) {
                        floatData[i] = data[i].floatValue();
                    }
                    return floatData;
                }
            }
            log.error("No sentence_embedding tensor found for dense embedding");
            return null;

        } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
            // For sparse embeddings, find the tensor with dataAsMap
            for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
                Map<String, ?> dataMap = tensor.getDataAsMap();
                if (dataMap != null) {
                    // Check if sparse embedding is nested in a response field
                    if (dataMap.containsKey("response") && dataMap.get("response") instanceof List) {
                        List<?> responseList = (List<?>) dataMap.get("response");
                        if (!responseList.isEmpty() && responseList.get(0) instanceof Map) {
                            Map<String, ?> sparseMap = (Map<String, ?>) responseList.get(0);
                            log.debug("Extracted sparse embedding from nested response with {} tokens", sparseMap.size());
                            return sparseMap;
                        }
                    }
                    // Otherwise return the direct map
                    log.debug("Using direct sparse embedding with {} tokens", dataMap.size());
                    return dataMap;
                }
            }
            log.error("No sparse embedding data found");
            return null;
        }

        return null;
    }

    private void getMemoryContainer(String memoryContainerId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryContainer> wrappedListener = ActionListener.runBefore(listener, context::restore);

            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                    } else {
                        wrappedListener.onFailure(cause);
                    }
                } else {
                    try {
                        if (r.getResponse() != null && r.getResponse().isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getResponse().getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLMemoryContainer container = MLMemoryContainer.parse(parser);
                                wrappedListener.onResponse(container);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }
            });
        }
    }

    private String getIndexName(MLMemoryContainer container) {
        MemoryStorageConfig config = container.getMemoryStorageConfig();
        if (config != null && config.getMemoryIndexName() != null) {
            return config.getMemoryIndexName();
        }
        return null;
    }

    private int getMaxShortTermMemories(MLMemoryContainer container) {
        MemoryStorageConfig config = container.getMemoryStorageConfig();
        if (config != null && config.getMaxShortTermMemories() != null) {
            return config.getMaxShortTermMemories();
        }
        return MAX_SHORT_TERM_MEMORIES_DEFAULT_VALUE;
    }

    private boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }
}
