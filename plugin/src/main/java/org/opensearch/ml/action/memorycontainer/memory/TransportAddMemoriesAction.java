/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_DECISION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryDecisionRequest;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportAddMemoriesAction extends HandledTransportAction<MLAddMemoriesRequest, MLAddMemoriesResponse> {

    // Helper class to track memory info for response building
    private static class MemoryInfo {
        String memoryId;
        final String content;
        final MemoryType type;
        final boolean includeInResponse;

        MemoryInfo(String memoryId, String content, MemoryType type, boolean includeInResponse) {
            this.memoryId = memoryId;
            this.content = content;
            this.type = type;
            this.includeInResponse = includeInResponse;
        }
    }

    // Helper class to store fact search results
    private static class FactSearchResult {
        final String id;
        final String text;
        final float score;

        FactSearchResult(String id, String text, float score) {
            this.id = id;
            this.text = text;
            this.score = score;
        }

        String getId() {
            return id;
        }

        String getText() {
            return text;
        }

        float getScore() {
            return score;
        }
    }

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;
    final MemoryContainerHelper memoryContainerHelper;
    final MemoryEmbeddingHelper memoryEmbeddingHelper;

    @Inject
    public TransportAddMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager,
        MemoryContainerHelper memoryContainerHelper,
        MemoryEmbeddingHelper memoryEmbeddingHelper
    ) {
        super(MLAddMemoriesAction.NAME, transportService, actionFilters, MLAddMemoriesRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
        this.memoryContainerHelper = memoryContainerHelper;
        this.memoryEmbeddingHelper = memoryEmbeddingHelper;
    }

    @Override
    protected void doExecute(Task task, MLAddMemoriesRequest request, ActionListener<MLAddMemoriesResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        MLAddMemoriesInput input = request.getMlAddMemoryInput();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Memory input is required"));
            return;
        }

        String memoryContainerId = input.getMemoryContainerId();

        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        // Get memory container
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Check access
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }

            // Get memory index name
            String indexName = memoryContainerHelper.getMemoryIndexName(container);
            if (indexName == null) {
                actionListener.onFailure(new IllegalStateException("Memory index not created for this container"));
                return;
            }

            processAndIndexMemory(input, container, indexName, user, actionListener);
        }, actionListener::onFailure));
    }

    private void processAndIndexMemory(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        User user,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        try {
            List<MessageInput> messages = input.getMessages();

            // Check if session ID was provided by user
            boolean userProvidedSessionId = input.getSessionId() != null && !input.getSessionId().isEmpty();

            // Generate session ID if not provided
            final String sessionId;
            if (!userProvidedSessionId) {
                sessionId = "sess_" + UUID.randomUUID().toString();
                log.debug("Auto-generated session ID: {}", sessionId);
            } else {
                sessionId = input.getSessionId();
                log.debug("User provided session ID: {}", sessionId);
            }

            // Determine memory type (currently only RAW_MESSAGE)
            MemoryType memoryType = MemoryType.RAW_MESSAGE;

            // Validate infer flag
            Boolean infer = input.getInfer();
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            boolean hasLlmModel = storageConfig != null && storageConfig.getLlmModelId() != null;

            if (infer != null && infer && !hasLlmModel) {
                actionListener.onFailure(new IllegalArgumentException(INFER_REQUIRES_LLM_MODEL_ERROR));
                return;
            }

            // Set default infer value based on LLM model presence if not specified
            if (infer == null) {
                infer = hasLlmModel;
            }

            // Validate all messages have roles when infer=false
            if (!infer) {
                for (MessageInput message : messages) {
                    if (message.getRole() == null) {
                        actionListener.onFailure(new IllegalArgumentException("Role is required for all messages when infer=false"));
                        return;
                    }
                }
            }

            // Process messages based on infer flag
            if (infer) {
                // Process all messages with LLM for fact extraction
                processMessagesWithLLM(input, container, indexName, sessionId, userProvidedSessionId, user, storageConfig, actionListener);
            } else {
                // Process messages without LLM
                processMessagesWithoutLLM(input, container, indexName, sessionId, user, storageConfig, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to add memory", e);
            actionListener.onFailure(e);
        }
    }

    private void processMessagesWithLLM(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        String sessionId,
        boolean userProvidedSessionId,
        User user,
        MemoryStorageConfig storageConfig,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        List<MessageInput> messages = input.getMessages();

        log.debug("Processing {} messages for fact extraction", messages.size());

        // Extract facts from provided messages with single LLM call
        extractFactsFromConversation(messages, storageConfig, ActionListener.wrap(facts -> {
            // Store raw messages and facts
            storeMessagesAndFacts(
                input,
                container,
                indexName,
                messages,
                sessionId,
                userProvidedSessionId,
                user,
                facts,
                storageConfig,
                actionListener
            );
        }, e -> {
            log.error("Failed to extract facts with LLM", e);
            actionListener.onFailure(new OpenSearchException("Failed to extract facts: " + e.getMessage(), e));
        }));
    }

    private void storeMessagesAndFacts(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        List<MessageInput> messages,
        String sessionId,
        boolean userProvidedSessionId,
        User user,
        List<String> facts,
        MemoryStorageConfig storageConfig,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        Instant now = Instant.now();
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        // 1. Create RAW_MESSAGE entries for each message
        for (MessageInput message : messages) {
            MLMemory rawMemory = MLMemory
                .builder()
                .sessionId(sessionId)
                .memory(message.getContent())
                .memoryType(MemoryType.RAW_MESSAGE)
                .userId(user != null ? user.getName() : null)
                .agentId(input.getAgentId())
                .role(message.getRole() != null ? message.getRole() : "user")
                .tags(input.getTags())
                .createdTime(now)
                .lastUpdatedTime(now)
                .build();

            IndexRequest request = new IndexRequest(indexName).source(rawMemory.toIndexMap());
            indexRequests.add(request);

            // RAW_MESSAGE not included in response when infer=true
            memoryInfos.add(new MemoryInfo(null, rawMemory.getMemory(), rawMemory.getMemoryType(), false));
        }

        // 2. Search for similar facts and make memory decisions
        if (userProvidedSessionId && facts.size() > 0 && storageConfig != null && storageConfig.getLlmModelId() != null) {
            log.debug("Searching for similar facts in session to make memory decisions");
            searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, ActionListener.wrap(allSearchResults -> {
                // Log the consolidated search results
                log.debug("Found {} total similar facts across all {} new facts", allSearchResults.size(), facts.size());
                for (FactSearchResult similar : allSearchResults) {
                    log.debug("  Similar fact: id={}, text='{}', score={}", similar.getId(), similar.getText(), similar.getScore());
                }

                // Make memory decisions with LLM
                consolidateAndMakeMemoryDecisions(facts, allSearchResults, storageConfig, ActionListener.wrap(decisions -> {
                    // Execute memory operations based on decisions
                    executeMemoryOperations(
                        decisions,
                        indexName,
                        sessionId,
                        user,
                        input,
                        storageConfig,
                        ActionListener.wrap(operationResults -> {
                            // Combine operation results with raw message results
                            List<MemoryResult> allResults = new ArrayList<>();
                            allResults.addAll(operationResults);

                            // Build and send response
                            MLAddMemoriesResponse response = MLAddMemoriesResponse
                                .builder()
                                .results(allResults)
                                .sessionId(sessionId)
                                .build();
                            actionListener.onResponse(response);
                        }, actionListener::onFailure)
                    );
                }, e -> {
                    log.error("Failed to make memory decisions", e);
                    actionListener.onFailure(new OpenSearchException("Failed to make memory decisions: " + e.getMessage(), e));
                }));
            }, e -> {
                log.error("Failed to search similar facts", e);
                actionListener.onFailure(new OpenSearchException("Failed to search similar facts: " + e.getMessage(), e));
            }));
        } else {
            // No memory decisions needed
            if (!userProvidedSessionId) {
                log.debug("Skipping memory decisions - session ID was auto-generated");
            } else if (facts.isEmpty()) {
                log.debug("Skipping memory decisions - no facts extracted");
            } else if (storageConfig == null || storageConfig.getLlmModelId() == null) {
                log.debug("Skipping memory decisions - LLM model not configured");
            }
            createFactMemoriesFromList(facts, input, indexName, sessionId, user, now, indexRequests, memoryInfos);
            processEmbeddingsAndIndex(messages, facts, storageConfig, indexRequests, memoryInfos, sessionId, indexName, actionListener);
        }
    }

    private void createFactMemoriesFromList(
        List<String> facts,
        MLAddMemoriesInput input,
        String indexName,
        String sessionId,
        User user,
        Instant now,
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos
    ) {
        for (String fact : facts) {
            MLMemory factMemory = MLMemory
                .builder()
                .sessionId(sessionId)
                .memory(fact)
                .memoryType(MemoryType.FACT)
                .userId(user != null ? user.getName() : null)
                .agentId(input.getAgentId())
                .role("assistant")
                .tags(input.getTags())
                .createdTime(now)
                .lastUpdatedTime(now)
                .build();

            IndexRequest request = new IndexRequest(indexName).source(factMemory.toIndexMap());
            indexRequests.add(request);

            // FACT memories ARE included in response
            memoryInfos.add(new MemoryInfo(null, factMemory.getMemory(), factMemory.getMemoryType(), true));
        }
    }

    private void processEmbeddingsAndIndex(
        List<MessageInput> messages,
        List<String> facts,
        MemoryStorageConfig storageConfig,
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        String sessionId,
        String indexName,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        // If semantic storage is enabled, generate embeddings for all memories
        boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

        if (needsEmbedding) {
            // Generate embeddings for all memories
            List<String> textsToEmbed = new ArrayList<>();
            for (MessageInput message : messages) {
                textsToEmbed.add(message.getContent());
            }
            textsToEmbed.addAll(facts);

            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
                // Update index requests with embeddings
                if (embeddings != null && embeddings.size() == indexRequests.size()) {
                    for (int i = 0; i < indexRequests.size(); i++) {
                        Map<String, Object> sourceMap = indexRequests.get(i).sourceAsMap();
                        sourceMap.put("memory_embedding", embeddings.get(i));
                        indexRequests.get(i).source(sourceMap);
                    }
                }

                // Execute bulk index with memory infos
                bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }, e -> {
                log.error("Failed to generate embeddings for memories", e);
                // Propagate the error for infer=true
                actionListener.onFailure(new OpenSearchException("Failed to generate embeddings for memories: " + e.getMessage(), e));
            }));
        } else {
            // No embeddings needed, execute bulk index directly
            bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
        }
    }

    private void processMessagesWithoutLLM(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        String sessionId,
        User user,
        MemoryStorageConfig storageConfig,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        List<MessageInput> messages = input.getMessages();
        Instant now = Instant.now();
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        // Create RAW_MESSAGE for each message
        for (MessageInput message : messages) {
            MLMemory memory = MLMemory
                .builder()
                .sessionId(sessionId)
                .memory(message.getContent())
                .memoryType(MemoryType.RAW_MESSAGE)
                .userId(user != null ? user.getName() : null)
                .agentId(input.getAgentId())
                .role(message.getRole())
                .tags(input.getTags())
                .createdTime(now)
                .lastUpdatedTime(now)
                .build();

            IndexRequest request = new IndexRequest(indexName).source(memory.toIndexMap());
            indexRequests.add(request);

            // All messages included in response when infer=false
            memoryInfos.add(new MemoryInfo(null, memory.getMemory(), memory.getMemoryType(), true));
        }

        // Handle embeddings if needed
        if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
            List<String> texts = new ArrayList<>();
            for (MessageInput message : messages) {
                texts.add(message.getContent());
            }

            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(texts, storageConfig, ActionListener.wrap(embeddings -> {
                // Update requests with embeddings
                if (embeddings != null && embeddings.size() == indexRequests.size()) {
                    for (int i = 0; i < indexRequests.size(); i++) {
                        Map<String, Object> sourceMap = indexRequests.get(i).sourceAsMap();
                        sourceMap.put("memory_embedding", embeddings.get(i));
                        indexRequests.get(i).source(sourceMap);
                    }
                }
                bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }, e -> {
                log.error("Failed to generate embeddings, storing without", e);
                bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }));
        } else {
            bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
        }
    }

    private void saveMemoryWithEmbedding(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        MessageInput message,
        String sessionId,
        User user,
        MemoryType memoryType,
        Object embedding,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        // Build memory object
        Instant now = Instant.now();
        MLMemory memory = MLMemory
            .builder()
            .sessionId(sessionId)
            .memory(message.getContent())
            .memoryType(memoryType)
            .userId(user != null ? user.getName() : null)
            .agentId(input.getAgentId())
            .role(message.getRole())
            .tags(input.getTags())
            .createdTime(now)
            .lastUpdatedTime(now)
            .memoryEmbedding(embedding)
            .build();

        // Index the memory without ID (auto-generate)
        IndexRequest indexRequest = new IndexRequest(indexName)
            .source(memory.toIndexMap())
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
            String generatedId = indexResponse.getId();
            log.debug("Successfully indexed message {} to index {}", generatedId, indexName);

            // Build response with single result
            List<MemoryResult> results = new ArrayList<>();
            results
                .add(
                    MemoryResult.builder().memoryId(generatedId).memory(message.getContent()).event(MemoryEvent.ADD).oldMemory(null).build()
                );

            MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(results).sessionId(sessionId).build();
            actionListener.onResponse(response);
        }, actionListener::onFailure));
    }

    private void extractFactsFromConversation(
        List<MessageInput> messages,
        MemoryStorageConfig storageConfig,
        ActionListener<List<String>> listener
    ) {
        if (storageConfig == null || storageConfig.getLlmModelId() == null) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String llmModelId = storageConfig.getLlmModelId();

        // Build the LLM request parameters as strings
        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.put("system_prompt", PERSONAL_INFORMATION_ORGANIZER_PROMPT);

        // Build messages array and serialize to JSON string
        try {
            XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
            messagesBuilder.startArray();

            // Build array with all messages
            for (MessageInput message : messages) {
                messagesBuilder.startObject();
                messagesBuilder.field("role", message.getRole() != null ? message.getRole() : "user");
                messagesBuilder.startArray("content");
                messagesBuilder.startObject();
                messagesBuilder.field("type", "text");
                messagesBuilder.field("text", message.getContent());
                messagesBuilder.endObject();
                messagesBuilder.endArray();
                messagesBuilder.endObject();
            }

            messagesBuilder.endArray();

            String messagesJson = messagesBuilder.toString();
            stringParameters.put("messages", messagesJson);

            log.debug("LLM request - processing {} messages", messages.size());
            log.debug("LLM request - system_prompt length: {}", PERSONAL_INFORMATION_ORGANIZER_PROMPT.length());
        } catch (Exception e) {
            log.error("Failed to build messages JSON", e);
            listener.onResponse(new ArrayList<>());
            return;
        }

        // Create MLInput for remote inference
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(stringParameters).build())
            .build();

        log.debug("Created MLInput for LLM model: {}", llmModelId);

        // Create prediction request
        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

        // Execute LLM call
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                log.debug("Received LLM response, parsing facts...");
                MLOutput mlOutput = response.getOutput();

                List<String> facts = parseFactsFromLLMResponse(mlOutput);
                log.debug("Extracted {} facts from LLM response", facts.size());
                listener.onResponse(facts);
            } catch (Exception e) {
                log.error("Failed to parse facts from LLM response", e);
                listener.onFailure(new IllegalArgumentException("Failed to parse facts from LLM response", e));
            }
        }, e -> {
            log.error("Failed to call LLM for fact extraction", e);
            listener.onFailure(new OpenSearchException("Failed to extract facts using LLM model: " + e.getMessage(), e));
        }));
    }

    private List<String> parseFactsFromLLMResponse(MLOutput mlOutput) {
        List<String> facts = new ArrayList<>();

        if (!(mlOutput instanceof ModelTensorOutput)) {
            log.warn("Unexpected ML output type for LLM response: {}", mlOutput != null ? mlOutput.getClass().getName() : "null");
            return facts;
        }

        ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
        log
            .debug(
                "ModelTensorOutput - outputs count: {}",
                tensorOutput.getMlModelOutputs() != null ? tensorOutput.getMlModelOutputs().size() : 0
            );

        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.warn("No model outputs found in LLM response");
            return facts;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        log
            .debug(
                "ModelTensors - tensors count: {}",
                modelTensors.getMlModelTensors() != null ? modelTensors.getMlModelTensors().size() : 0
            );

        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.warn("No model tensors found in LLM response");
            return facts;
        }

        // Look for the response tensor
        for (int i = 0; i < modelTensors.getMlModelTensors().size(); i++) {
            ModelTensor tensor = modelTensors.getMlModelTensors().get(i);
            log.debug("Tensor[{}] - name: {}, dataType: {}", i, tensor.getName(), tensor.getDataType());

            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                log.debug("Tensor[{}] - dataMap keys: {}", i, dataMap.keySet());

                // Check for content field which contains the actual response
                if (dataMap.containsKey("content")) {
                    log.debug("Found content field in tensor dataMap");

                    try {
                        List<?> contentList = (List<?>) dataMap.get("content");
                        if (contentList != null && !contentList.isEmpty()) {
                            log.debug("Content list size: {}", contentList.size());

                            // Get the first content item
                            Map<String, ?> contentItem = (Map<String, ?>) contentList.get(0);
                            if (contentItem != null && contentItem.containsKey("text")) {
                                String responseStr = String.valueOf(contentItem.get("text"));
                                log.debug("Found text in content item, length: {}", responseStr.length());

                                // Parse JSON response to extract facts
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseStr)
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

                                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                                        String fieldName = parser.currentName();
                                        log.debug("Parsing field: {}", fieldName);

                                        if ("facts".equals(fieldName)) {
                                            ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                                            while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                                                String fact = parser.text();
                                                log.debug("Found fact: {}", fact);
                                                facts.add(fact);
                                            }
                                        } else {
                                            parser.skipChildren();
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse facts from LLM JSON response: {}", responseStr, e);
                                    throw new IllegalArgumentException(
                                        "Failed to parse JSON facts from LLM response. Response: " + responseStr,
                                        e
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to extract content from dataMap", e);
                        throw new IllegalArgumentException("Failed to extract content from LLM response", e);
                    }
                    break;
                }
            } else {
                log.debug("Tensor[{}] - dataMap is null", i);
            }
        }

        log.debug("Total facts extracted from LLM response: {}", facts.size());
        return facts;
    }

    private void saveMemoriesWithFacts(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        MessageInput message,
        String sessionId,
        boolean userProvidedSessionId,
        User user,
        List<String> facts,
        MemoryStorageConfig storageConfig,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        Instant now = Instant.now();
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        // First, create the RAW_MESSAGE memory
        MLMemory rawMemory = MLMemory
            .builder()
            .sessionId(sessionId)
            .memory(message.getContent())
            .memoryType(MemoryType.RAW_MESSAGE)
            .userId(user != null ? user.getName() : null)
            .agentId(input.getAgentId())
            .role(message.getRole())
            .tags(input.getTags())
            .createdTime(now)
            .lastUpdatedTime(now)
            .build();

        IndexRequest rawMemoryRequest = new IndexRequest(indexName).source(rawMemory.toIndexMap());
        indexRequests.add(rawMemoryRequest);
        // RAW_MESSAGE is not included in response when infer=true
        memoryInfos.add(new MemoryInfo(null, rawMemory.getMemory(), rawMemory.getMemoryType(), false));

        // Search for similar facts only if user provided session ID and LLM model is configured
        if (userProvidedSessionId && storageConfig != null && storageConfig.getLlmModelId() != null) {
            log.debug("Searching for similar facts in session (user provided)");
            searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, ActionListener.wrap(similarFacts -> {
                // Log the search results
                log.debug("Found {} similar facts across all {} new facts", similarFacts.size(), facts.size());
                for (FactSearchResult similar : similarFacts) {
                    log.debug("  Similar fact: id={}, text='{}', score={}", similar.getId(), similar.getText(), similar.getScore());
                }

                // Continue with creating FACT memories
                createFactMemoriesAndIndex(
                    input,
                    container,
                    indexName,
                    message,
                    sessionId,
                    user,
                    facts,
                    storageConfig,
                    indexRequests,
                    memoryInfos,
                    now,
                    actionListener
                );
            }, e -> {
                log.error("Failed to search for similar facts, continuing with indexing", e);
                // Continue even if search fails
                createFactMemoriesAndIndex(
                    input,
                    container,
                    indexName,
                    message,
                    sessionId,
                    user,
                    facts,
                    storageConfig,
                    indexRequests,
                    memoryInfos,
                    now,
                    actionListener
                );
            }));
        } else {
            // Skip search if no session ID or LLM model not configured
            if (!userProvidedSessionId) {
                log.debug("Skipping fact search - session ID was auto-generated");
            } else if (storageConfig == null || storageConfig.getLlmModelId() == null) {
                log.debug("Skipping fact search - LLM model not configured");
            }
            createFactMemoriesAndIndex(
                input,
                container,
                indexName,
                message,
                sessionId,
                user,
                facts,
                storageConfig,
                indexRequests,
                memoryInfos,
                now,
                actionListener
            );
        }
    }

    private void createFactMemoriesAndIndex(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        MessageInput message,
        String sessionId,
        User user,
        List<String> facts,
        MemoryStorageConfig storageConfig,
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        Instant now,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        // Create FACT memories for each extracted fact
        for (String fact : facts) {
            MLMemory factMemory = MLMemory
                .builder()
                .sessionId(sessionId)
                .memory(fact)
                .memoryType(MemoryType.FACT)
                .userId(user != null ? user.getName() : null)
                .agentId(input.getAgentId())
                .role("assistant") // Facts are generated by assistant
                .tags(input.getTags())
                .createdTime(now)
                .lastUpdatedTime(now)
                .build();

            IndexRequest factRequest = new IndexRequest(indexName).source(factMemory.toIndexMap());
            indexRequests.add(factRequest);
            // FACT memories are included in response
            memoryInfos.add(new MemoryInfo(null, factMemory.getMemory(), factMemory.getMemoryType(), true));
        }

        // If semantic storage is enabled, generate embeddings for all memories
        boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

        if (needsEmbedding) {
            // Generate embeddings for all memories
            List<String> textsToEmbed = new ArrayList<>();
            textsToEmbed.add(message.getContent()); // Raw message
            textsToEmbed.addAll(facts); // All facts

            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
                // Update index requests with embeddings
                if (embeddings != null && embeddings.size() == indexRequests.size()) {
                    for (int i = 0; i < indexRequests.size(); i++) {
                        Map<String, Object> sourceMap = indexRequests.get(i).sourceAsMap();
                        sourceMap.put("memory_embedding", embeddings.get(i));
                        indexRequests.get(i).source(sourceMap);
                    }
                }

                // Execute bulk index with memory infos
                bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }, e -> {
                log.error("Failed to generate embeddings for memories", e);
                // Propagate the error for infer=true
                actionListener.onFailure(new OpenSearchException("Failed to generate embeddings for memories: " + e.getMessage(), e));
            }));
        } else {
            // No embeddings needed, execute bulk index directly
            bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
        }
    }

    private void bulkIndexMemoriesWithResults(
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        String sessionId,
        String indexName,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        if (indexRequests.isEmpty()) {
            log.warn("No memories to index");
            actionListener.onFailure(new IllegalStateException("No memories to index"));
            return;
        }

        // Index them sequentially and collect IDs
        indexMemoriesSequentiallyWithResults(indexRequests, memoryInfos, 0, sessionId, indexName, new ArrayList<>(), actionListener);
    }

    private void indexMemoriesSequentiallyWithResults(
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        int currentIndex,
        String sessionId,
        String indexName,
        List<MemoryResult> results,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        if (currentIndex >= indexRequests.size()) {
            // All memories indexed successfully
            log.debug("Successfully indexed {} memories in index {}", indexRequests.size(), indexName);
            MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(results).sessionId(sessionId).build();
            actionListener.onResponse(response);
            return;
        }

        IndexRequest currentRequest = indexRequests.get(currentIndex);
        client.index(currentRequest, ActionListener.wrap(indexResponse -> {
            String memoryId = indexResponse.getId();

            // Update memory info with generated ID
            MemoryInfo info = memoryInfos.get(currentIndex);
            info.memoryId = memoryId;

            // Add to results if this memory should be included in response
            if (info.includeInResponse) {
                results.add(MemoryResult.builder().memoryId(memoryId).memory(info.content).event(MemoryEvent.ADD).oldMemory(null).build());
            }

            // Continue with next memory
            indexMemoriesSequentiallyWithResults(
                indexRequests,
                memoryInfos,
                currentIndex + 1,
                sessionId,
                indexName,
                results,
                actionListener
            );
        }, actionListener::onFailure));
    }

    private void validateEmbeddingModelState(String modelId, FunctionName modelType, ActionListener<Boolean> listener) {
        // If model type is REMOTE, no need to check state
        if (modelType == FunctionName.REMOTE) {
            listener.onResponse(true);
            return;
        }

        // For TEXT_EMBEDDING or SPARSE_ENCODING, check if model is DEPLOYED
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(model -> {
                MLModelState modelState = model.getModelState();
                if (modelState != MLModelState.DEPLOYED) {
                    listener
                        .onFailure(
                            new IllegalStateException(
                                String.format("Embedding model must be in DEPLOYED state, current state: %s", modelState)
                            )
                        );
                } else {
                    listener.onResponse(true);
                }
            }, e -> {
                log.error("Failed to get embedding model: " + modelId, e);
                listener.onFailure(new IllegalStateException("Failed to validate embedding model state", e));
            }), context::restore);

            mlModelManager.getModel(modelId, wrappedListener);
        }
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

        // Validate model state before generating embedding
        validateEmbeddingModelState(embeddingModelId, embeddingModelType, ActionListener.wrap(isValid -> {
            // Create MLInput for text embedding
            MLInput mlInput = MLInput
                .builder()
                .algorithm(embeddingModelType)
                .inputDataset(TextDocsInputDataSet.builder().docs(Arrays.asList(message)).build())
                .build();

            // Create prediction request
            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
                .builder()
                .modelId(embeddingModelId)
                .mlInput(mlInput)
                .build();

            // Execute prediction
            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                try {
                    MLOutput mlOutput = response.getOutput();
                    if (mlOutput instanceof ModelTensorOutput) {
                        ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                        Object embedding = null;

                        if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                            embedding = buildDenseEmbeddingFromResponse(tensorOutput);
                        } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                            embedding = buildSparseEmbeddingFromResponse(tensorOutput);
                        }

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
        }, e -> {
            log.error("Failed to validate embedding model state", e);
            listener.onResponse(null);
        }));
    }

    private float[] buildDenseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

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
    }

    private Map<String, ?> buildSparseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

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

    private void searchSimilarFactsForSession(
        List<String> facts,
        String sessionId,
        String indexName,
        MemoryStorageConfig storageConfig,
        ActionListener<List<FactSearchResult>> listener
    ) {
        // Skip search if no session ID provided
        if (sessionId == null || facts.isEmpty()) {
            log.debug("Skipping fact search: facts count={}", facts.size());
            listener.onResponse(new ArrayList<>());
            return;
        }

        List<FactSearchResult> allResults = new ArrayList<>();
        int maxInferSize = storageConfig != null && storageConfig.getMaxInferSize() != null ? storageConfig.getMaxInferSize() : 5;

        // Search for each fact
        searchFactsSequentially(facts, 0, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
    }

    private void searchFactsSequentially(
        List<String> facts,
        int currentIndex,
        String sessionId,
        String indexName,
        MemoryStorageConfig storageConfig,
        int maxInferSize,
        List<FactSearchResult> allResults,
        ActionListener<List<FactSearchResult>> listener
    ) {
        if (currentIndex >= facts.size()) {
            listener.onResponse(allResults);
            return;
        }

        String fact = facts.get(currentIndex);

        try {
            // Build the search query using the utility class
            XContentBuilder queryBuilder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, storageConfig);
            String queryJson = queryBuilder.toString();

            // Log the query for debugging
            log.debug("Searching for similar facts with query: {}", queryJson);

            // Build search request
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.wrapperQuery(queryJson));
            searchSourceBuilder.size(maxInferSize);
            searchSourceBuilder.fetchSource(new String[] { MEMORY_FIELD }, null);

            SearchRequest searchRequest = new SearchRequest().indices(indexName).source(searchSourceBuilder);

            // Execute search
            client.search(searchRequest, ActionListener.wrap(response -> {
                // Add all results to the consolidated list
                for (SearchHit hit : response.getHits().getHits()) {
                    Map<String, Object> sourceMap = hit.getSourceAsMap();
                    String memory = (String) sourceMap.get(MEMORY_FIELD);
                    if (memory != null) {
                        allResults.add(new FactSearchResult(hit.getId(), memory, hit.getScore()));
                    }
                }

                log.debug("Found {} similar facts for: {}", response.getHits().getHits().length, fact);

                // Continue with next fact
                searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
            }, e -> {
                log.error("Failed to search for similar facts for: {}", fact, e);
                // Continue with next fact even if this one fails
                searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
            }));
        } catch (Exception e) {
            log.error("Failed to build search query for fact: {}", fact, e);
            searchFactsSequentially(facts, currentIndex + 1, sessionId, indexName, storageConfig, maxInferSize, allResults, listener);
        }
    }

    private void consolidateAndMakeMemoryDecisions(
        List<String> extractedFacts,
        List<FactSearchResult> allSearchResults,
        MemoryStorageConfig storageConfig,
        ActionListener<List<MemoryDecision>> listener
    ) {
        if (storageConfig == null || storageConfig.getLlmModelId() == null) {
            listener.onFailure(new IllegalStateException("LLM model is required for memory decisions"));
            return;
        }

        String llmModelId = storageConfig.getLlmModelId();

        // Build the memory decision request
        List<MemoryDecisionRequest.OldMemory> oldMemories = new ArrayList<>();
        for (FactSearchResult result : allSearchResults) {
            oldMemories
                .add(MemoryDecisionRequest.OldMemory.builder().id(result.getId()).text(result.getText()).score(result.getScore()).build());
        }

        MemoryDecisionRequest decisionRequest = MemoryDecisionRequest
            .builder()
            .oldMemory(oldMemories)
            .retrievedFacts(extractedFacts)
            .build();

        // Build LLM parameters
        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.put("system_prompt", DEFAULT_UPDATE_MEMORY_PROMPT);

        // Convert decision request to JSON string for messages
        String decisionRequestJson = decisionRequest.toJsonString();

        try {
            XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
            messagesBuilder.startArray();
            messagesBuilder.startObject();
            messagesBuilder.field("role", "user");
            messagesBuilder.startArray("content");
            messagesBuilder.startObject();
            messagesBuilder.field("type", "text");
            messagesBuilder.field("text", decisionRequestJson);
            messagesBuilder.endObject();
            messagesBuilder.endArray();
            messagesBuilder.endObject();
            messagesBuilder.endArray();

            String messagesJson = messagesBuilder.toString();
            stringParameters.put("messages", messagesJson);

            log
                .debug(
                    "Making memory decisions for {} extracted facts and {} existing memories",
                    extractedFacts.size(),
                    allSearchResults.size()
                );

            // Create remote inference request
            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(stringParameters).build();
            MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                try {
                    List<MemoryDecision> decisions = parseMemoryDecisions(response);
                    log.debug("LLM made {} memory decisions", decisions.size());
                    listener.onResponse(decisions);
                } catch (Exception e) {
                    log.error("Failed to parse memory decisions from LLM response", e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to get memory decisions from LLM", e);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to build memory decision request", e);
            listener.onFailure(e);
        }
    }

    private List<MemoryDecision> parseMemoryDecisions(MLTaskResponse response) {
        try {
            MLOutput mlOutput = response.getOutput();
            if (!(mlOutput instanceof ModelTensorOutput)) {
                throw new IllegalStateException("Expected ModelTensorOutput but got: " + mlOutput.getClass().getSimpleName());
            }

            ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
            List<ModelTensors> tensors = tensorOutput.getMlModelOutputs();
            if (tensors == null || tensors.isEmpty()) {
                throw new IllegalStateException("No model output tensors found");
            }

            // Extract the response from dataAsMap
            Map<String, ?> dataMap = tensors.get(0).getMlModelTensors().get(0).getDataAsMap();

            // Parse response based on the expected structure
            String responseContent = null;
            if (dataMap.containsKey("response")) {
                responseContent = (String) dataMap.get("response");
            } else if (dataMap.containsKey("content")) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) dataMap.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    responseContent = (String) firstContent.get("text");
                }
            }

            if (responseContent == null) {
                throw new IllegalStateException("No response content found in LLM output");
            }

            log.debug("Memory decisions response: {}", responseContent);

            // Clean response content - remove markdown code blocks if present
            if (responseContent.startsWith("```json") && responseContent.endsWith("```")) {
                responseContent = responseContent.substring(7, responseContent.length() - 3).trim();
            } else if (responseContent.startsWith("```") && responseContent.endsWith("```")) {
                responseContent = responseContent.substring(3, responseContent.length() - 3).trim();
            }

            // Parse the JSON response
            List<MemoryDecision> decisions = new ArrayList<>();
            try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseContent)) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();

                    if (MEMORY_DECISION_FIELD.equals(fieldName)) {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            decisions.add(MemoryDecision.parse(parser));
                        }
                    } else {
                        parser.skipChildren();
                    }
                }
            }

            return decisions;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse memory decisions", e);
        }
    }

    private void executeMemoryOperations(
        List<MemoryDecision> decisions,
        String indexName,
        String sessionId,
        User user,
        MLAddMemoriesInput input,
        MemoryStorageConfig storageConfig,
        ActionListener<List<MemoryResult>> listener
    ) {
        List<MemoryResult> results = new ArrayList<>();
        List<IndexRequest> addRequests = new ArrayList<>();
        List<UpdateRequest> updateRequests = new ArrayList<>();
        List<DeleteRequest> deleteRequests = new ArrayList<>();

        Instant now = Instant.now();

        // Group operations by type
        for (MemoryDecision decision : decisions) {
            switch (decision.getEvent()) {
                case ADD:
                    MLMemory newMemory = MLMemory
                        .builder()
                        .sessionId(sessionId)
                        .memory(decision.getText())
                        .memoryType(MemoryType.FACT)
                        .userId(user != null ? user.getName() : null)
                        .agentId(input.getAgentId())
                        .tags(input.getTags())
                        .createdTime(now)
                        .lastUpdatedTime(now)
                        .build();

                    // Don't set ID - let OpenSearch auto-generate
                    IndexRequest addRequest = new IndexRequest(indexName).source(newMemory.toIndexMap());
                    addRequests.add(addRequest);

                    // Will update results after bulk response with actual ID
                    results
                        .add(
                            MemoryResult.builder().memoryId(null).memory(decision.getText()).event(MemoryEvent.ADD).oldMemory(null).build()
                        );
                    break;

                case UPDATE:
                    Map<String, Object> updateDoc = new HashMap<>();
                    updateDoc.put(MEMORY_FIELD, decision.getText());
                    updateDoc.put(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());

                    UpdateRequest updateRequest = new UpdateRequest(indexName, decision.getId()).doc(updateDoc);
                    updateRequests.add(updateRequest);

                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.UPDATE)
                                .oldMemory(decision.getOldMemory())
                                .build()
                        );
                    break;

                case DELETE:
                    DeleteRequest deleteRequest = new DeleteRequest(indexName, decision.getId());
                    deleteRequests.add(deleteRequest);

                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.DELETE)
                                .oldMemory(null)
                                .build()
                        );
                    break;

                case NONE:
                    // Add NONE events to results
                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.NONE)
                                .oldMemory(null)
                                .build()
                        );
                    break;
            }
        }

        // Execute operations
        BulkRequest bulkRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        // Add all requests to bulk
        for (IndexRequest request : addRequests) {
            bulkRequest.add(request);
        }
        for (UpdateRequest request : updateRequests) {
            bulkRequest.add(request);
        }
        for (DeleteRequest request : deleteRequests) {
            bulkRequest.add(request);
        }

        if (bulkRequest.requests().isEmpty()) {
            log.debug("No memory operations to execute");
            listener.onResponse(results);
            return;
        }

        // Execute bulk request
        client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                log.error("Bulk memory operations had failures: {}", bulkResponse.buildFailureMessage());
                // Still return partial results
            }

            log.debug("Executed {} memory operations successfully", bulkResponse.getItems().length);

            // Process bulk response to get actual IDs for ADD operations
            BulkItemResponse[] items = bulkResponse.getItems();
            int itemIndex = 0;
            int addResultIndex = 0;

            // Update results with actual IDs from bulk response
            for (int i = 0; i < results.size(); i++) {
                MemoryResult result = results.get(i);
                if (result.getEvent() == MemoryEvent.ADD && itemIndex < items.length) {
                    // Find the corresponding bulk item for this ADD operation
                    while (itemIndex < items.length && items[itemIndex].getOpType() != DocWriteRequest.OpType.INDEX) {
                        itemIndex++;
                    }

                    if (itemIndex < items.length && !items[itemIndex].isFailed()) {
                        // Update the result with the actual generated ID
                        String actualId = items[itemIndex].getId();
                        results
                            .set(
                                i,
                                MemoryResult
                                    .builder()
                                    .memoryId(actualId)
                                    .memory(result.getMemory())
                                    .event(MemoryEvent.ADD)
                                    .oldMemory(null)
                                    .build()
                            );
                    }
                    itemIndex++;
                }
            }

            // If semantic storage is enabled, generate embeddings for ADD and UPDATE operations
            if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
                List<String> textsToEmbed = new ArrayList<>();
                List<String> memoryIdsToUpdate = new ArrayList<>();

                // Collect texts and IDs for embedding generation
                for (MemoryResult result : results) {
                    if ((result.getEvent() == MemoryEvent.ADD || result.getEvent() == MemoryEvent.UPDATE) && result.getMemoryId() != null) {
                        textsToEmbed.add(result.getMemory());
                        memoryIdsToUpdate.add(result.getMemoryId());
                    }
                }

                if (!textsToEmbed.isEmpty()) {
                    memoryEmbeddingHelper
                        .generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
                            // Update memories with embeddings
                            List<UpdateRequest> embeddingUpdates = new ArrayList<>();
                            for (int i = 0; i < memoryIdsToUpdate.size() && i < embeddings.size(); i++) {
                                Map<String, Object> embeddingUpdate = new HashMap<>();
                                embeddingUpdate.put(MEMORY_EMBEDDING_FIELD, embeddings.get(i));

                                UpdateRequest updateRequest = new UpdateRequest(indexName, memoryIdsToUpdate.get(i)).doc(embeddingUpdate);
                                embeddingUpdates.add(updateRequest);
                            }

                            if (!embeddingUpdates.isEmpty()) {
                                BulkRequest embeddingBulk = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                                for (UpdateRequest request : embeddingUpdates) {
                                    embeddingBulk.add(request);
                                }

                                client.bulk(embeddingBulk, ActionListener.wrap(embeddingResponse -> {
                                    if (embeddingResponse.hasFailures()) {
                                        log.error("Failed to update embeddings: {}", embeddingResponse.buildFailureMessage());
                                    }
                                    listener.onResponse(results);
                                }, e -> {
                                    log.error("Failed to update embeddings", e);
                                    listener.onResponse(results);
                                }));
                            } else {
                                listener.onResponse(results);
                            }
                        }, e -> {
                            log.error("Failed to generate embeddings for memory operations", e);
                            listener.onResponse(results);
                        }));
                } else {
                    listener.onResponse(results);
                }
            } else {
                listener.onResponse(results);
            }
        }, e -> {
            log.error("Failed to execute memory operations", e);
            listener.onFailure(e);
        }));
    }
}
