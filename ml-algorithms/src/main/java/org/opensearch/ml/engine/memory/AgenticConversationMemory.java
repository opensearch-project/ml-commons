/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionInput;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Agentic Memory implementation that stores conversations in Memory Container
 * Uses TransportCreateSessionAction and TransportAddMemoriesAction for all operations
 */
@Log4j2
@Getter
public class AgenticConversationMemory implements Memory<Message, CreateInteractionResponse, UpdateResponse> {

    public static final String TYPE = MLMemoryType.AGENTIC_MEMORY.name();
    private static final String SESSION_ID_FIELD = "session_id";
    private static final String CREATED_TIME_FIELD = "created_time";

    private final Client client;
    private final String conversationId;
    private final String memoryContainerId;

    public AgenticConversationMemory(Client client, String memoryId, String memoryContainerId) {
        this.client = client;
        this.conversationId = memoryId;
        this.memoryContainerId = memoryContainerId;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getId() {
        return conversationId;
    }

    @Override
    public void save(Message message, String parentId, Integer traceNum, String action) {
        this.save(message, parentId, traceNum, action, ActionListener.<CreateInteractionResponse>wrap(r -> {
            log.info("Saved message to agentic memory, session id: {}, working memory id: {}", conversationId, r.getId());
        }, e -> { log.error("Failed to save message to agentic memory", e); }));
    }

    @Override
    public void save(
        Message message,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<CreateInteractionResponse> listener
    ) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener
                .onFailure(
                    new IllegalStateException(
                        "Memory container ID is not configured for this AgenticConversationMemory. "
                            + "Cannot save messages without a valid memory container."
                    )
                );
            return;
        }

        ConversationIndexMessage msg = (ConversationIndexMessage) message;

        // Build namespace with session_id
        Map<String, String> namespace = new HashMap<>();
        namespace.put(SESSION_ID_FIELD, conversationId);

        // Simple rule matching ConversationIndexMemory:
        // - If traceNum != null → it's a trace
        // - If traceNum == null → it's a message
        boolean isTrace = (traceNum != null);

        Map<String, String> metadata = new HashMap<>();
        Map<String, Object> structuredData = new HashMap<>();

        // Store data in structured_data format matching conversation index
        structuredData.put("input", msg.getQuestion() != null ? msg.getQuestion() : "");
        structuredData.put("response", msg.getResponse() != null ? msg.getResponse() : "");

        if (isTrace) {
            // This is a trace (tool usage or intermediate step)
            metadata.put("type", "trace");
            if (parentId != null) {
                metadata.put("parent_message_id", parentId);
                structuredData.put("parent_message_id", parentId);
            }
            metadata.put("trace_number", String.valueOf(traceNum));
            structuredData.put("trace_number", traceNum);
            if (action != null) {
                metadata.put("origin", action);
                structuredData.put("origin", action);
            }
        } else {
            // This is a final message (Q&A pair)
            metadata.put("type", "message");
            if (msg.getFinalAnswer() != null) {
                structuredData.put("final_answer", msg.getFinalAnswer());
            }
        }

        // Add timestamps
        java.time.Instant now = java.time.Instant.now();
        structuredData.put("create_time", now.toString());
        structuredData.put("updated_time", now.toString());

        // Create MLAddMemoriesInput
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .structuredDataBlob(structuredData)
            .messageId(traceNum) // Store trace number in messageId field (null for messages)
            .namespace(namespace)
            .metadata(metadata)
            .infer(false) // Don't infer long-term memory by default
            .build();

        MLAddMemoriesRequest request = MLAddMemoriesRequest.builder().mlAddMemoryInput(input).build();

        // Execute the add memories action
        client.execute(MLAddMemoriesAction.INSTANCE, request, ActionListener.wrap(response -> {
            // Convert MLAddMemoriesResponse to CreateInteractionResponse
            CreateInteractionResponse interactionResponse = new CreateInteractionResponse(response.getWorkingMemoryId());
            listener.onResponse(interactionResponse);
        }, e -> {
            log.error("Failed to add memories to memory container", e);
            listener.onFailure(e);
        }));
    }

    @Override
    public void update(String messageId, Map<String, Object> updateContent, ActionListener<UpdateResponse> updateListener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            updateListener.onFailure(new IllegalStateException("Memory container ID is not configured for this AgenticConversationMemory"));
            return;
        }

        // Step 1: Get the existing working memory to retrieve current structured_data
        MLGetMemoryRequest getRequest = MLGetMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(MemoryType.WORKING)
            .memoryId(messageId)
            .build();

        client.execute(MLGetMemoryAction.INSTANCE, getRequest, ActionListener.wrap(getResponse -> {
            // Step 2: Extract existing structured_data and merge with updates
            MLWorkingMemory workingMemory = getResponse.getWorkingMemory();
            if (workingMemory == null) {
                updateListener.onFailure(new IllegalStateException("Working memory not found for id: " + messageId));
                return;
            }

            Map<String, Object> structuredData = workingMemory.getStructuredDataBlob();
            if (structuredData == null) {
                structuredData = new HashMap<>();
            } else {
                // Create a mutable copy
                structuredData = new HashMap<>(structuredData);
            }

            // Step 3: Merge update content into structured_data
            // The updateContent contains fields like "response" and "additional_info"
            // These should be stored in structured_data
            for (Map.Entry<String, Object> entry : updateContent.entrySet()) {
                structuredData.put(entry.getKey(), entry.getValue());
            }

            // Update the timestamp
            structuredData.put("updated_time", java.time.Instant.now().toString());

            // Step 4: Create update request with merged structured_data_blob
            Map<String, Object> finalUpdateContent = new HashMap<>();
            finalUpdateContent.put("structured_data_blob", structuredData);

            MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(finalUpdateContent).build();

            MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId(memoryContainerId)
                .memoryType(MemoryType.WORKING)
                .memoryId(messageId)
                .mlUpdateMemoryInput(input)
                .build();

            // Step 5: Execute the update
            client.execute(MLUpdateMemoryAction.INSTANCE, updateRequest, ActionListener.wrap(indexResponse -> {
                // Convert IndexResponse to UpdateResponse
                UpdateResponse updateResponse = new UpdateResponse(
                    indexResponse.getShardInfo(),
                    indexResponse.getShardId(),
                    indexResponse.getId(),
                    indexResponse.getSeqNo(),
                    indexResponse.getPrimaryTerm(),
                    indexResponse.getVersion(),
                    indexResponse.getResult()
                );
                updateListener.onResponse(updateResponse);
            }, e -> {
                log.error("Failed to update memory in memory container", e);
                updateListener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to get existing memory for update", e);
            updateListener.onFailure(e);
        }));
    }

    @Override
    public void getMessages(int size, ActionListener<List<Message>> listener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this AgenticConversationMemory"));
            return;
        }

        // Build search query for working memory by session_id, filtering only final messages (not traces)
        // Match ConversationIndexMemory pattern: exclude entries tagged as trace
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("namespace." + SESSION_ID_FIELD, conversationId));
        boolQuery.mustNot(QueryBuilders.termQuery("metadata.type", "trace")); // Exclude traces

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(size);
        searchSourceBuilder.sort(CREATED_TIME_FIELD, SortOrder.ASC);

        MLSearchMemoriesInput searchInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(MemoryType.WORKING)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        MLSearchMemoriesRequest request = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(searchInput).tenantId(null).build();

        client.execute(MLSearchMemoriesAction.INSTANCE, request, ActionListener.wrap(searchResponse -> {
            List<Message> interactions = parseSearchResponseToInteractions(searchResponse);
            listener.onResponse(interactions);
        }, e -> {
            log.error("Failed to search memories in memory container", e);
            listener.onFailure(e);
        }));
    }

    private List<Message> parseSearchResponseToInteractions(SearchResponse searchResponse) {
        List<Message> interactions = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();

            // Extract structured_data_blob which contains the interaction data
            @SuppressWarnings("unchecked")
            Map<String, Object> structuredData = (Map<String, Object>) sourceMap.get("structured_data_blob");

            if (structuredData != null) {
                String input = (String) structuredData.get("input");
                String response = (String) structuredData.get("response");

                // Extract timestamps
                Long createdTimeMs = (Long) sourceMap.get("created_time");
                Long updatedTimeMs = (Long) sourceMap.get("last_updated_time");

                // Parse create_time from structured_data if available
                String createTimeStr = (String) structuredData.get("create_time");
                String updatedTimeStr = (String) structuredData.get("updated_time");

                java.time.Instant createTime = null;
                java.time.Instant updatedTime = null;

                if (createTimeStr != null) {
                    try {
                        createTime = java.time.Instant.parse(createTimeStr);
                    } catch (Exception e) {
                        log.warn("Failed to parse create_time from structured_data", e);
                    }
                }
                if (updatedTimeStr != null) {
                    try {
                        updatedTime = java.time.Instant.parse(updatedTimeStr);
                    } catch (Exception e) {
                        log.warn("Failed to parse updated_time from structured_data", e);
                    }
                }

                // Fallback to document timestamps if structured_data timestamps not available
                if (createTime == null && createdTimeMs != null) {
                    createTime = java.time.Instant.ofEpochMilli(createdTimeMs);
                }
                if (updatedTime == null && updatedTimeMs != null) {
                    updatedTime = java.time.Instant.ofEpochMilli(updatedTimeMs);
                }

                // Extract metadata
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = (Map<String, String>) sourceMap.get("metadata");
                String parentInteractionId = metadata != null ? metadata.get("parent_message_id") : null;

                // Create Interaction object
                if (input != null || response != null) {
                    Interaction interaction = Interaction
                        .builder()
                        .id(hit.getId())
                        .conversationId(conversationId)
                        .createTime(createTime != null ? createTime : java.time.Instant.now())
                        .updatedTime(updatedTime)
                        .input(input != null ? input : "")
                        .response(response != null ? response : "")
                        .origin("agentic_memory")
                        .promptTemplate(null)
                        .additionalInfo(null)
                        .parentInteractionId(parentInteractionId)
                        .traceNum(null) // Messages don't have trace numbers
                        .build();
                    interactions.add(interaction);
                }
            }
        }
        return interactions;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear method is not supported in AgenticConversationMemory");
    }

    @Override
    public void deleteInteractionAndTrace(String interactionId, ActionListener<Boolean> listener) {
        // For now, delegate to a simple implementation
        // In the future, this could use MLDeleteMemoryAction
        log.warn("deleteInteractionAndTrace is not fully implemented for AgenticConversationMemory");
        listener.onResponse(false);
    }

    /**
     * Get traces (intermediate steps/tool usage) for a specific parent message
     * @param parentMessageId The parent message ID
     * @param listener Action listener for the traces
     */
    public void getTraces(String parentMessageId, ActionListener<List<Interaction>> listener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this AgenticConversationMemory"));
            return;
        }

        // Build search query for traces by parent_message_id
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("namespace." + SESSION_ID_FIELD, conversationId));
        boolQuery.must(QueryBuilders.termQuery("metadata.type", "trace")); // Only get traces
        boolQuery.must(QueryBuilders.termQuery("metadata.parent_message_id", parentMessageId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(1000); // Get all traces for this message
        searchSourceBuilder.sort("message_id", SortOrder.ASC); // Sort by trace number

        MLSearchMemoriesInput searchInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(MemoryType.WORKING)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        MLSearchMemoriesRequest request = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(searchInput).tenantId(null).build();

        client.execute(MLSearchMemoriesAction.INSTANCE, request, ActionListener.wrap(searchResponse -> {
            List<Interaction> traces = parseSearchResponseToTraces(searchResponse);
            listener.onResponse(traces);
        }, e -> {
            log.error("Failed to search traces in memory container", e);
            listener.onFailure(e);
        }));
    }

    private List<Interaction> parseSearchResponseToTraces(SearchResponse searchResponse) {
        List<Interaction> traces = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();

            // Extract structured_data_blob which contains the trace data
            @SuppressWarnings("unchecked")
            Map<String, Object> structuredData = (Map<String, Object>) sourceMap.get("structured_data_blob");

            if (structuredData != null) {
                String input = (String) structuredData.get("input");
                String response = (String) structuredData.get("response");
                String origin = (String) structuredData.get("origin");
                String parentMessageId = (String) structuredData.get("parent_message_id");

                // Extract trace number
                Integer traceNum = null;
                Object traceNumObj = structuredData.get("trace_number");
                if (traceNumObj instanceof Integer) {
                    traceNum = (Integer) traceNumObj;
                } else if (traceNumObj instanceof String) {
                    try {
                        traceNum = Integer.parseInt((String) traceNumObj);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse trace_number", e);
                    }
                }

                // Also check message_id field which stores trace number
                Integer messageId = (Integer) sourceMap.get("message_id");
                if (traceNum == null && messageId != null) {
                    traceNum = messageId;
                }

                // Extract timestamps
                Long createdTimeMs = (Long) sourceMap.get("created_time");
                Long updatedTimeMs = (Long) sourceMap.get("last_updated_time");

                java.time.Instant createTime = createdTimeMs != null
                    ? java.time.Instant.ofEpochMilli(createdTimeMs)
                    : java.time.Instant.now();
                java.time.Instant updatedTime = updatedTimeMs != null ? java.time.Instant.ofEpochMilli(updatedTimeMs) : null;

                // Create Interaction object for trace
                if (input != null || response != null) {
                    Interaction trace = Interaction
                        .builder()
                        .id(hit.getId())
                        .conversationId(conversationId)
                        .createTime(createTime)
                        .updatedTime(updatedTime)
                        .input(input != null ? input : "")
                        .response(response != null ? response : "")
                        .origin(origin != null ? origin : "")
                        .promptTemplate(null)
                        .additionalInfo(null)
                        .parentInteractionId(parentMessageId)
                        .traceNum(traceNum)
                        .build();
                    traces.add(trace);
                }
            }
        }
        return traces;
    }

    /**
     * Save a message in Strands format (for V2 agents)
     * Stores the complete message structure with role and content blocks
     *
     * @param role Message role (user, assistant, tool, etc.)
     * @param contentBlocks List of content blocks (text, image, etc.)
     * @param messageId Sequential message ID
     * @param listener Action listener for save completion
     */
    public void saveMessageInStrandsFormat(
        String role,
        List<Map<String, Object>> contentBlocks,
        Integer messageId,
        ActionListener<CreateInteractionResponse> listener
    ) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this AgenticConversationMemory"));
            return;
        }

        // Build namespace with session_id
        Map<String, String> namespace = new HashMap<>();
        namespace.put(SESSION_ID_FIELD, conversationId);

        // Build message structure in Strands format
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", contentBlocks);

        // Build structured_data_blob
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("message", message);
        structuredData.put("message_id", messageId);

        // Add timestamps
        java.time.Instant now = java.time.Instant.now();
        structuredData.put("created_at", now.toString());
        structuredData.put("updated_at", now.toString());

        // Metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "message");
        metadata.put("role", role);
        metadata.put("format", "strands");

        // Create MLAddMemoriesInput
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .structuredDataBlob(structuredData)
            .messageId(messageId)
            .namespace(namespace)
            .metadata(metadata)
            .infer(false)
            .build();

        MLAddMemoriesRequest request = MLAddMemoriesRequest.builder().mlAddMemoryInput(input).build();

        // Execute the add memories action
        client.execute(MLAddMemoriesAction.INSTANCE, request, ActionListener.wrap(response -> {
            CreateInteractionResponse interactionResponse = new CreateInteractionResponse(response.getWorkingMemoryId());
            listener.onResponse(interactionResponse);
        }, e -> {
            log.error("Failed to save message in Strands format to memory container", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Get full conversation history including tool interactions (for V2 agents)
     * Returns conversation turns with complete structured data
     *
     * @param limit Maximum number of conversation turns to retrieve
     * @param listener Action listener for the conversation history
     */
    public void getFullConversationHistory(int limit, ActionListener<List<Map<String, Object>>> listener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this AgenticConversationMemory"));
            return;
        }

        // Build search query for working memory by session_id
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("namespace." + SESSION_ID_FIELD, conversationId));
        boolQuery.mustNot(QueryBuilders.termQuery("metadata.type", "trace")); // Exclude traces for now

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(limit);
        searchSourceBuilder.sort(CREATED_TIME_FIELD, SortOrder.ASC);

        MLSearchMemoriesInput searchInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(MemoryType.WORKING)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        MLSearchMemoriesRequest request = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(searchInput).tenantId(null).build();

        client.execute(MLSearchMemoriesAction.INSTANCE, request, ActionListener.wrap(searchResponse -> {
            List<Map<String, Object>> conversationHistory = new ArrayList<>();

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                Map<String, Object> sourceMap = hit.getSourceAsMap();

                // Extract structured_data_blob which contains the full conversation data
                @SuppressWarnings("unchecked")
                Map<String, Object> structuredData = (Map<String, Object>) sourceMap.get("structured_data_blob");

                if (structuredData != null) {
                    // Create a conversation turn with all available data
                    Map<String, Object> turn = new HashMap<>();
                    turn.put("id", hit.getId());
                    turn.put("structured_data", structuredData);

                    // Add metadata
                    @SuppressWarnings("unchecked")
                    Map<String, String> metadata = (Map<String, String>) sourceMap.get("metadata");
                    if (metadata != null) {
                        turn.put("metadata", metadata);
                    }

                    // Add timestamps
                    Long createdTimeMs = (Long) sourceMap.get("created_time");
                    if (createdTimeMs != null) {
                        turn.put("created_time", java.time.Instant.ofEpochMilli(createdTimeMs));
                    }

                    conversationHistory.add(turn);
                }
            }

            listener.onResponse(conversationHistory);
        }, e -> {
            log.error("Failed to retrieve full conversation history from memory container", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Factory for creating AgenticConversationMemory instances
     */
    public static class Factory implements Memory.Factory<AgenticConversationMemory> {
        private Client client;

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<AgenticConversationMemory> listener) {
            if (map == null || map.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating AgenticConversationMemory"));
                return;
            }

            String memoryId = (String) map.get(MEMORY_ID);
            String name = (String) map.get(MEMORY_NAME);
            String appType = (String) map.get(APP_TYPE);
            String memoryContainerId = (String) map.get("memory_container_id");

            create(name, memoryId, appType, memoryContainerId, listener);
        }

        public void create(
            String name,
            String memoryId,
            String appType,
            String memoryContainerId,
            ActionListener<AgenticConversationMemory> listener
        ) {
            // Memory container ID is required for AgenticConversationMemory
            if (Strings.isNullOrEmpty(memoryContainerId)) {
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "Memory container ID is required for AgenticConversationMemory. "
                                + "Please provide 'memory_container_id' in the agent configuration."
                        )
                    );
                return;
            }

            if (Strings.isEmpty(memoryId)) {
                // Create new session using TransportCreateSessionAction
                createSessionInMemoryContainer(name, memoryContainerId, ActionListener.wrap(sessionId -> {
                    create(sessionId, memoryContainerId, listener);
                    log.debug("Created session in memory container, session id: {}", sessionId);
                }, e -> {
                    log.error("Failed to create session in memory container", e);
                    listener.onFailure(e);
                }));
            } else {
                // Use existing session/memory ID
                create(memoryId, memoryContainerId, listener);
            }
        }

        /**
         * Create a new session in the memory container using the new session API
         */
        private void createSessionInMemoryContainer(String summary, String memoryContainerId, ActionListener<String> listener) {
            MLCreateSessionInput input = MLCreateSessionInput.builder().memoryContainerId(memoryContainerId).summary(summary).build();

            MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(input).build();

            client
                .execute(
                    MLCreateSessionAction.INSTANCE,
                    request,
                    ActionListener.wrap(response -> { listener.onResponse(response.getSessionId()); }, e -> {
                        log.error("Failed to create session via TransportCreateSessionAction", e);
                        listener.onFailure(e);
                    })
                );
        }

        public void create(String memoryId, String memoryContainerId, ActionListener<AgenticConversationMemory> listener) {
            listener.onResponse(new AgenticConversationMemory(client, memoryId, memoryContainerId));
        }
    }
}
