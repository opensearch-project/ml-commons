/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
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
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
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
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
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

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;

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
        MLModelManager mlModelManager
    ) {
        super(MLAddMemoriesAction.NAME, transportService, actionFilters, MLAddMemoriesRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
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

        if (memoryContainerId == null || memoryContainerId.isEmpty()) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        // Get memory container
        getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Check access
            if (!checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }

            // Get memory index name
            String indexName = getIndexName(container);
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
            // Get the first message (currently limited to 1)
            MessageInput message = input.getMessages().get(0);

            // Generate session ID if not provided
            final String sessionId;
            if (input.getSessionId() == null || input.getSessionId().isEmpty()) {
                sessionId = "sess_" + UUID.randomUUID().toString();
            } else {
                sessionId = input.getSessionId();
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

            // Validate role when infer=false
            if (!infer && message.getRole() == null) {
                actionListener.onFailure(new IllegalArgumentException("Role is required when infer=false"));
                return;
            }

            // Process the message based on infer flag
            if (infer) {
                // Extract facts using LLM before saving
                extractFactsWithLLM(message, storageConfig, ActionListener.wrap(facts -> {
                    // Save memories with facts
                    saveMemoriesWithFacts(input, container, indexName, message, sessionId, user, facts, storageConfig, actionListener);
                }, e -> {
                    log.error("Failed to extract facts with LLM, saving raw message only", e);
                    // Fall back to saving raw message only
                    boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();
                    if (needsEmbedding) {
                        generateEmbedding(message.getContent(), storageConfig, ActionListener.wrap(embedding -> {
                            saveMemoryWithEmbedding(
                                input,
                                container,
                                indexName,
                                message,
                                sessionId,
                                user,
                                memoryType,
                                embedding,
                                actionListener
                            );
                        }, error -> {
                            log.error("Failed to generate embedding, saving memory without embedding", error);
                            saveMemoryWithEmbedding(
                                input,
                                container,
                                indexName,
                                message,
                                sessionId,
                                user,
                                memoryType,
                                null,
                                actionListener
                            );
                        }));
                    } else {
                        saveMemoryWithEmbedding(input, container, indexName, message, sessionId, user, memoryType, null, actionListener);
                    }
                }));
            } else {
                // No LLM processing needed, check for embedding
                boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

                if (needsEmbedding) {
                    // Generate embedding first, then save memory
                    generateEmbedding(message.getContent(), storageConfig, ActionListener.wrap(embedding -> {
                        saveMemoryWithEmbedding(
                            input,
                            container,
                            indexName,
                            message,
                            sessionId,
                            user,
                            memoryType,
                            embedding,
                            actionListener
                        );
                    }, e -> {
                        log.error("Failed to generate embedding for memory", e);
                        // For infer=false, propagate the error
                        actionListener.onFailure(new OpenSearchException("Failed to generate embedding: " + e.getMessage(), e));
                    }));
                } else {
                    // No embedding needed, save directly
                    saveMemoryWithEmbedding(input, container, indexName, message, sessionId, user, memoryType, null, actionListener);
                }
            }
        } catch (Exception e) {
            log.error("Failed to add memory", e);
            actionListener.onFailure(e);
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
            log.info("Successfully indexed message {} in session {} to index {}", generatedId, sessionId, indexName);

            // Build response with single result
            List<MemoryResult> results = new ArrayList<>();
            results.add(MemoryResult.builder().memoryId(generatedId).memory(message.getContent()).event(MemoryEvent.ADD).build());

            MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(results).sessionId(sessionId).build();
            actionListener.onResponse(response);
        }, actionListener::onFailure));
    }

    private void extractFactsWithLLM(MessageInput message, MemoryStorageConfig storageConfig, ActionListener<List<String>> listener) {
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
            messagesBuilder.startObject();
            messagesBuilder.field("role", message.getRole() != null ? message.getRole() : "user");
            messagesBuilder.startArray("content");
            messagesBuilder.startObject();
            messagesBuilder.field("type", "text");
            messagesBuilder.field("text", message.getContent());
            messagesBuilder.endObject();
            messagesBuilder.endArray();
            messagesBuilder.endObject();
            messagesBuilder.endArray();

            String messagesJson = messagesBuilder.toString();
            stringParameters.put("messages", messagesJson);

            log.info("LLM request - system_prompt length: {}", PERSONAL_INFORMATION_ORGANIZER_PROMPT.length());
            log.info("LLM request - messages JSON: {}", messagesJson);
            log.info("LLM request - full parameters: {}", stringParameters);
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

        log.info("Created MLInput for LLM model: {}", llmModelId);

        // Create prediction request
        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

        // Execute LLM call
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                log.info("Received LLM response, parsing facts...");
                MLOutput mlOutput = response.getOutput();
                log.info("LLM response output type: {}", mlOutput != null ? mlOutput.getClass().getName() : "null");

                List<String> facts = parseFactsFromLLMResponse(mlOutput);
                log.info("Extracted {} facts from LLM response", facts.size());
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
            .info(
                "ModelTensorOutput - outputs count: {}",
                tensorOutput.getMlModelOutputs() != null ? tensorOutput.getMlModelOutputs().size() : 0
            );

        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.warn("No model outputs found in LLM response");
            return facts;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        log
            .info(
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
            log.info("Tensor[{}] - name: {}, dataType: {}", i, tensor.getName(), tensor.getDataType());

            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                log.info("Tensor[{}] - dataMap keys: {}", i, dataMap.keySet());

                // Check for content field which contains the actual response
                if (dataMap.containsKey("content")) {
                    log.info("Found content field in tensor dataMap");

                    try {
                        List<?> contentList = (List<?>) dataMap.get("content");
                        if (contentList != null && !contentList.isEmpty()) {
                            log.info("Content list size: {}", contentList.size());

                            // Get the first content item
                            Map<String, ?> contentItem = (Map<String, ?>) contentList.get(0);
                            if (contentItem != null && contentItem.containsKey("text")) {
                                String responseStr = String.valueOf(contentItem.get("text"));
                                log.info("Found text in content item: {}", responseStr);

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
                                                log.info("Found fact: {}", fact);
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
                log.info("Tensor[{}] - dataMap is null", i);
            }
        }

        log.info("Total facts extracted from LLM response: {}", facts.size());
        return facts;
    }

    private void saveMemoriesWithFacts(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        String indexName,
        MessageInput message,
        String sessionId,
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

        IndexRequest rawMemoryRequest = new IndexRequest(indexName)
            .source(rawMemory.toIndexMap())
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        indexRequests.add(rawMemoryRequest);
        // RAW_MESSAGE is not included in response when infer=true
        memoryInfos.add(new MemoryInfo(null, rawMemory.getMemory(), rawMemory.getMemoryType(), false));

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

            IndexRequest factRequest = new IndexRequest(indexName)
                .source(factMemory.toIndexMap())
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
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

            generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
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

    private void generateEmbeddingsForMultipleTexts(
        List<String> texts,
        MemoryStorageConfig storageConfig,
        ActionListener<List<Object>> listener
    ) {
        if (texts.isEmpty()) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        // Validate model state before generating embeddings
        validateEmbeddingModelState(embeddingModelId, embeddingModelType, ActionListener.wrap(isValid -> {
            // Create MLInput for text embedding with multiple documents
            MLInput mlInput = MLInput
                .builder()
                .algorithm(embeddingModelType)
                .inputDataset(TextDocsInputDataSet.builder().docs(texts).build())
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
                        List<Object> embeddings = new ArrayList<>();

                        if (tensorOutput.getMlModelOutputs() != null) {
                            for (ModelTensors modelTensors : tensorOutput.getMlModelOutputs()) {
                                Object embedding = null;
                                if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                                    embedding = extractDenseEmbeddingFromModelTensors(modelTensors);
                                } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                                    embedding = extractSparseEmbeddingFromModelTensors(modelTensors);
                                }
                                embeddings.add(embedding);
                            }
                        }

                        listener.onResponse(embeddings);
                    } else {
                        log.error("Unexpected ML output type: {}", mlOutput.getClass().getName());
                        listener.onFailure(new IllegalStateException("Unexpected ML output type: " + mlOutput.getClass().getName()));
                    }
                } catch (Exception e) {
                    log.error("Failed to extract embeddings from ML output", e);
                    listener.onFailure(new IllegalStateException("Failed to extract embeddings from ML output", e));
                }
            }, e -> {
                log.error("Failed to generate embeddings", e);
                listener.onFailure(new OpenSearchException("Failed to generate embeddings: " + e.getMessage(), e));
            }));
        }, e -> {
            log.error("Failed to validate embedding model state", e);
            listener.onFailure(e);
        }));
    }

    private Object extractDenseEmbeddingFromModelTensors(ModelTensors modelTensors) {
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            return null;
        }

        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            if ("sentence_embedding".equals(tensor.getName()) && tensor.getData() != null) {
                Number[] data = tensor.getData();
                float[] floatData = new float[data.length];
                for (int i = 0; i < data.length; i++) {
                    floatData[i] = data[i].floatValue();
                }
                return floatData;
            }
        }
        return null;
    }

    private Object extractSparseEmbeddingFromModelTensors(ModelTensors modelTensors) {
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            return null;
        }

        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                if (dataMap.containsKey("response") && dataMap.get("response") instanceof List) {
                    List<?> responseList = (List<?>) dataMap.get("response");
                    if (!responseList.isEmpty() && responseList.get(0) instanceof Map) {
                        return responseList.get(0);
                    }
                }
                return dataMap;
            }
        }
        return null;
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
            log.info("Successfully indexed {} memories for session {} in index {}", indexRequests.size(), sessionId, indexName);
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
                results.add(MemoryResult.builder().memoryId(memoryId).memory(info.content).event(MemoryEvent.ADD).build());
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
