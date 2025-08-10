/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
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
public class TransportAddMemoriesAction extends HandledTransportAction<MLAddMemoriesRequest, MLAddMemoriesResponse> {

    final Client client;
    final NamedXContentRegistry xContentRegistry;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;
    final MemoryEmbeddingHelper memoryEmbeddingHelper;

    // Extracted services
    final MemoryProcessingService memoryProcessingService;
    final MemorySearchService memorySearchService;
    final MemoryOperationsService memoryOperationsService;

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
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
        this.memoryEmbeddingHelper = memoryEmbeddingHelper;

        // Initialize services
        this.memoryProcessingService = new MemoryProcessingService(client, xContentRegistry);
        this.memorySearchService = new MemorySearchService(client);
        this.memoryOperationsService = new MemoryOperationsService(client, memoryEmbeddingHelper);
    }

    // Package-private constructor for testing
    TransportAddMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper,
        MemoryEmbeddingHelper memoryEmbeddingHelper,
        MemoryProcessingService memoryProcessingService,
        MemorySearchService memorySearchService,
        MemoryOperationsService memoryOperationsService
    ) {
        super(MLAddMemoriesAction.NAME, transportService, actionFilters, MLAddMemoriesRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
        this.memoryEmbeddingHelper = memoryEmbeddingHelper;
        this.memoryProcessingService = memoryProcessingService;
        this.memorySearchService = memorySearchService;
        this.memoryOperationsService = memoryOperationsService;
    }

    @Override
    protected void doExecute(Task task, MLAddMemoriesRequest request, ActionListener<MLAddMemoriesResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

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

        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }

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

            boolean userProvidedSessionId = input.getSessionId() != null && !input.getSessionId().isEmpty();

            final String sessionId;
            if (!userProvidedSessionId) {
                sessionId = "sess_" + UUID.randomUUID().toString();
                log.debug("Auto-generated session ID: {}", sessionId);
            } else {
                sessionId = input.getSessionId();
                log.debug("User provided session ID: {}", sessionId);
            }

            MemoryType memoryType = MemoryType.RAW_MESSAGE;

            Boolean infer = input.getInfer();
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            boolean hasLlmModel = storageConfig != null && storageConfig.getLlmModelId() != null;

            if (infer != null && infer && !hasLlmModel) {
                actionListener.onFailure(new IllegalArgumentException(INFER_REQUIRES_LLM_MODEL_ERROR));
                return;
            }

            if (infer == null) {
                infer = hasLlmModel;
            }

            if (!infer) {
                for (MessageInput message : messages) {
                    if (message.getRole() == null) {
                        actionListener.onFailure(new IllegalArgumentException("Role is required for all messages when infer=false"));
                        return;
                    }
                }
            }

            if (infer) {
                processMessagesWithLLM(input, container, indexName, sessionId, userProvidedSessionId, user, storageConfig, actionListener);
            } else {
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

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, ActionListener.wrap(facts -> {
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

        // Create RAW_MESSAGE entries for each message
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
            memoryInfos.add(new MemoryInfo(null, rawMemory.getMemory(), rawMemory.getMemoryType(), false));
        }

        // Search for similar facts and make memory decisions
        if (facts.size() > 0 && storageConfig != null && storageConfig.getLlmModelId() != null) {
            log.debug("Searching for similar facts in session to make memory decisions");
            memorySearchService
                .searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, ActionListener.wrap(allSearchResults -> {
                    log.debug("Found {} total similar facts across all {} new facts", allSearchResults.size(), facts.size());

                    memoryProcessingService.makeMemoryDecisions(facts, allSearchResults, storageConfig, ActionListener.wrap(decisions -> {
                        memoryOperationsService
                            .executeMemoryOperations(
                                decisions,
                                indexName,
                                sessionId,
                                user,
                                input,
                                storageConfig,
                                ActionListener.wrap(operationResults -> {
                                    List<MemoryResult> allResults = new ArrayList<>(operationResults);
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
            memoryOperationsService.createFactMemoriesFromList(facts, input, indexName, sessionId, user, now, indexRequests, memoryInfos);
            processEmbeddingsAndIndex(messages, facts, storageConfig, indexRequests, memoryInfos, sessionId, indexName, actionListener);
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
        boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

        if (needsEmbedding) {
            List<String> textsToEmbed = new ArrayList<>();
            for (MessageInput message : messages) {
                textsToEmbed.add(message.getContent());
            }
            textsToEmbed.addAll(facts);

            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
                if (embeddings != null && embeddings.size() == indexRequests.size()) {
                    for (int i = 0; i < indexRequests.size(); i++) {
                        Map<String, Object> sourceMap = indexRequests.get(i).sourceAsMap();
                        sourceMap.put("memory_embedding", embeddings.get(i));
                        indexRequests.get(i).source(sourceMap);
                    }
                }
                memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }, e -> {
                log.error("Failed to generate embeddings for memories", e);
                actionListener.onFailure(new OpenSearchException("Failed to generate embeddings for memories: " + e.getMessage(), e));
            }));
        } else {
            memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
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
            memoryInfos.add(new MemoryInfo(null, memory.getMemory(), memory.getMemoryType(), true));
        }

        if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
            List<String> texts = new ArrayList<>();
            for (MessageInput message : messages) {
                texts.add(message.getContent());
            }

            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(texts, storageConfig, ActionListener.wrap(embeddings -> {
                if (embeddings != null && embeddings.size() == indexRequests.size()) {
                    for (int i = 0; i < indexRequests.size(); i++) {
                        Map<String, Object> sourceMap = indexRequests.get(i).sourceAsMap();
                        sourceMap.put("memory_embedding", embeddings.get(i));
                        indexRequests.get(i).source(sourceMap);
                    }
                }
                memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }, e -> {
                log.error("Failed to generate embeddings, storing without", e);
                memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
            }));
        } else {
            memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, actionListener);
        }
    }
}
