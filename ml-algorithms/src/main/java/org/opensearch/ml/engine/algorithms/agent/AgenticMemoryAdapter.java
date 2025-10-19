/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.PayloadType;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Adapter for Agentic Memory system to work with MLChatAgentRunner.
 * 
 * <p>This adapter provides a bridge between the ML Chat Agent system and the Agentic Memory
 * infrastructure, enabling intelligent conversation management and context retention.</p>
 * 
 * <h3>Memory Types Handled:</h3>
 * <ul>
 *   <li><strong>WORKING memory</strong>: Recent conversation history and active interactions</li>
 *   <li><strong>LONG_TERM memory</strong>: Extracted facts, summaries, and semantic insights</li>
 * </ul>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Dual memory querying for comprehensive context retrieval</li>
 *   <li>Dynamic inference configuration based on memory container LLM settings</li>
 *   <li>Structured trace data storage for tool invocation tracking</li>
 *   <li>Robust error handling with fallback mechanisms</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * AgenticMemoryAdapter adapter = new AgenticMemoryAdapter(
 *     client, "memory-container-id", "session-123", "user-456"
 * );
 * 
 * // Retrieve conversation messages
 * adapter.getMessages(ActionListener.wrap(
 *     messages -> processMessages(messages),
 *     error -> handleError(error)
 * ));
 * 
 * // Save trace data
 * adapter.saveTraceData("search_tool", "query", "results", 
 *     "parent-id", 1, "search", listener);
 * }</pre>
 * 
 * @see ChatMemoryAdapter
 * @see MLChatAgentRunner
 */
@Log4j2
public class AgenticMemoryAdapter implements ChatMemoryAdapter {
    private final Client client;
    private final String memoryContainerId;
    private final String sessionId;
    private final String ownerId;

    /**
     * Creates a new AgenticMemoryAdapter instance.
     * 
     * @param client OpenSearch client for executing memory operations
     * @param memoryContainerId Unique identifier for the memory container
     * @param sessionId Session identifier for conversation context
     * @param ownerId Owner/user identifier for access control
     * @throws IllegalArgumentException if any required parameter is null
     */
    public AgenticMemoryAdapter(Client client, String memoryContainerId, String sessionId, String ownerId) {
        if (client == null) {
            throw new IllegalArgumentException("Client cannot be null");
        }
        if (memoryContainerId == null || memoryContainerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory container ID cannot be null or empty");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (ownerId == null || ownerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner ID cannot be null or empty");
        }

        this.client = client;
        this.memoryContainerId = memoryContainerId;
        this.sessionId = sessionId;
        this.ownerId = ownerId;
    }

    @Override
    public void getMessages(ActionListener<List<ChatMessage>> listener) {
        // Query both WORKING memory (recent conversations) and LONG_TERM memory
        // (extracted facts)
        // This provides both conversation history and learned context

        List<ChatMessage> allChatMessages = new ArrayList<>();
        AtomicInteger pendingRequests = new AtomicInteger(2);

        // 1. Get recent conversation history from WORKING memory
        SearchSourceBuilder workingSearchBuilder = new SearchSourceBuilder()
            .query(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery("namespace.session_id", sessionId))
                    .must(QueryBuilders.termQuery("namespace.user_id", ownerId))
            )
            .sort("created_time", SortOrder.DESC)
            .size(50); // Limit recent conversation history

        MLSearchMemoriesRequest workingRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(
                MLSearchMemoriesInput
                    .builder()
                    .memoryContainerId(memoryContainerId)
                    .memoryType(MemoryType.WORKING)
                    .searchSourceBuilder(workingSearchBuilder)
                    .build()
            )
            .build();

        client.execute(MLSearchMemoriesAction.INSTANCE, workingRequest, ActionListener.wrap(workingResponse -> {
            synchronized (allChatMessages) {
                allChatMessages.addAll(parseAgenticMemoryResponse(workingResponse));
                if (pendingRequests.decrementAndGet() == 0) {
                    // Sort all chat messages by timestamp and return
                    allChatMessages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
                    listener.onResponse(allChatMessages);
                }
            }
        }, listener::onFailure));

        // 2. Get relevant context from LONG_TERM memory (extracted facts)
        SearchSourceBuilder longTermSearchBuilder = new SearchSourceBuilder()
            .query(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery("namespace.session_id", sessionId))
                    .must(QueryBuilders.termQuery("namespace.user_id", ownerId))
                    .should(QueryBuilders.termQuery("strategy_type", "SUMMARY"))
                    .should(QueryBuilders.termQuery("strategy_type", "SEMANTIC"))
            )
            .sort("created_time", SortOrder.DESC)
            .size(10); // Limit context facts

        MLSearchMemoriesRequest longTermRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(
                MLSearchMemoriesInput
                    .builder()
                    .memoryContainerId(memoryContainerId)
                    .memoryType(MemoryType.LONG_TERM)
                    .searchSourceBuilder(longTermSearchBuilder)
                    .build()
            )
            .build();

        client.execute(MLSearchMemoriesAction.INSTANCE, longTermRequest, ActionListener.wrap(longTermResponse -> {
            synchronized (allChatMessages) {
                allChatMessages.addAll(parseAgenticMemoryResponse(longTermResponse));
                if (pendingRequests.decrementAndGet() == 0) {
                    // Sort all chat messages by timestamp and return
                    allChatMessages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
                    listener.onResponse(allChatMessages);
                }
            }
        }, e -> {
            // If long-term memory fails, still return working memory results
            log.warn("Failed to retrieve long-term memory, continuing with working memory only", e);
            synchronized (allChatMessages) {
                if (pendingRequests.decrementAndGet() == 0) {
                    listener.onResponse(allChatMessages);
                }
            }
        }));
    }

    @Override
    public String getConversationId() {
        return sessionId;
    }

    @Override
    public String getMemoryContainerId() {
        return memoryContainerId;
    }

    @Override
    public void saveInteraction(
        String question,
        String assistantResponse,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<String> listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("ActionListener cannot be null");
        }
        final String finalQuestion = question != null ? question : "";
        final String finalAssistantResponse = assistantResponse != null ? assistantResponse : "";

        log
            .info(
                "AgenticMemoryAdapter.saveInteraction: Called with parentId: {}, action: {}, hasResponse: {}",
                parentId,
                action,
                !finalAssistantResponse.isEmpty()
            );

        // If parentId is provided and we have a response, update the existing
        // interaction
        if (parentId != null && !finalAssistantResponse.isEmpty()) {
            log.info("AgenticMemoryAdapter.saveInteraction: Updating existing interaction {} with final response", parentId);

            // Update the existing interaction with the complete conversation
            Map<String, Object> updateFields = new HashMap<>();
            updateFields.put("response", finalAssistantResponse);
            updateFields.put("input", finalQuestion);

            updateInteraction(parentId, updateFields, ActionListener.wrap(res -> {
                log.info("AgenticMemoryAdapter.saveInteraction: Successfully updated interaction {}", parentId);
                listener.onResponse(parentId); // Return the same interaction ID
            }, ex -> {
                log
                    .error(
                        "AgenticMemoryAdapter.saveInteraction: Failed to update interaction {}, falling back to create new",
                        parentId,
                        ex
                    );
                // Fallback to creating new interaction if update fails
                createNewInteraction(finalQuestion, finalAssistantResponse, parentId, traceNum, action, listener);
            }));
        } else {
            // Create new interaction (root interaction or when no parentId)
            log.info("AgenticMemoryAdapter.saveInteraction: Creating new interaction - parentId: {}, action: {}", parentId, action);
            createNewInteraction(finalQuestion, finalAssistantResponse, parentId, traceNum, action, listener);
        }
    }

    private void createNewInteraction(
        String question,
        String assistantResponse,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<String> listener
    ) {
        List<MessageInput> messages = Arrays
            .asList(
                MessageInput.builder().role("user").content(createTextContent(question)).build(),
                MessageInput.builder().role("assistant").content(createTextContent(assistantResponse)).build()
            );

        // Create namespace map with proper String types
        Map<String, String> namespaceMap = new java.util.HashMap<>();
        namespaceMap.put("session_id", sessionId);
        namespaceMap.put("user_id", ownerId);

        Map<String, String> metadataMap = new java.util.HashMap<>();
        if (traceNum != null) {
            metadataMap.put("trace_num", traceNum.toString());
        }
        if (action != null) {
            metadataMap.put("action", action);
        }
        if (parentId != null) {
            metadataMap.put("parent_id", parentId);
        }

        // Check if memory container has LLM ID configured to determine infer value
        hasLlmIdConfigured(ActionListener.wrap(hasLlmId -> {
            MLAddMemoriesInput input = MLAddMemoriesInput
                .builder()
                .memoryContainerId(memoryContainerId)
                .messages(messages)
                .namespace(namespaceMap)
                .metadata(metadataMap)
                .ownerId(ownerId)
                .infer(hasLlmId) // Use dynamic infer based on LLM ID presence
                .build();

            MLAddMemoriesRequest request = new MLAddMemoriesRequest(input);

            client.execute(MLAddMemoriesAction.INSTANCE, request, ActionListener.wrap(addResponse -> {
                log
                    .info(
                        "AgenticMemoryAdapter.createNewInteraction: Created interaction with ID: {}, sessionId: {}, action: {}, infer: {}",
                        addResponse.getWorkingMemoryId(),
                        addResponse.getSessionId(),
                        action,
                        hasLlmId
                    );
                listener.onResponse(addResponse.getWorkingMemoryId());
            }, listener::onFailure));
        }, ex -> {
            log.warn("Failed to check LLM ID configuration for interaction, proceeding with infer=false", ex);
            // Fallback to infer=false if we can't determine LLM ID status
            MLAddMemoriesInput input = MLAddMemoriesInput
                .builder()
                .memoryContainerId(memoryContainerId)
                .messages(messages)
                .namespace(namespaceMap)
                .metadata(metadataMap)
                .ownerId(ownerId)
                .infer(false)
                .build();

            MLAddMemoriesRequest request = new MLAddMemoriesRequest(input);

            client.execute(MLAddMemoriesAction.INSTANCE, request, ActionListener.wrap(addResponse -> {
                log
                    .info(
                        "AgenticMemoryAdapter.createNewInteraction: Created interaction with ID: {}, sessionId: {}, action: {}, infer: false (fallback)",
                        addResponse.getWorkingMemoryId(),
                        addResponse.getSessionId(),
                        action
                    );
                listener.onResponse(addResponse.getWorkingMemoryId());
            }, listener::onFailure));
        }));
    }

    /**
     * Save trace data as structured tool invocation information in working memory.
     * 
     * <p>This method stores detailed information about tool executions, including inputs,
     * outputs, and contextual metadata. The data is stored with appropriate tags and
     * namespace information for later retrieval and analysis.</p>
     * 
     * <p><strong>Important:</strong> This method always uses {@code infer=false} to prevent
     * LLM-based long-term memory extraction from tool traces. Tool execution data is already
     * structured and queryable, and extracting facts from intermediate steps would create
     * fragmented, duplicate long-term memories. Semantic extraction happens only on final
     * conversation interactions via {@link #saveInteraction}.</p>
     * 
     * @param toolName Name of the tool that was executed (required, non-empty)
     * @param toolInput Input parameters passed to the tool (nullable, defaults to empty string)
     * @param toolOutput Output/response from the tool execution (nullable, defaults to empty string)
     * @param parentMemoryId Parent memory ID to associate this trace with (nullable)
     * @param traceNum Trace sequence number for ordering (nullable)
     * @param action Action/origin identifier for categorization (nullable)
     * @param listener ActionListener to handle the response with the created memory ID
     * @throws IllegalArgumentException if toolName is null/empty or listener is null
     * @see #saveInteraction for conversational data that triggers long-term memory extraction
     */
    @Override
    public void saveTraceData(
        String toolName,
        String toolInput,
        String toolOutput,
        String parentMemoryId,
        Integer traceNum,
        String action,
        ActionListener<String> listener
    ) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        if (listener == null) {
            throw new IllegalArgumentException("ActionListener cannot be null");
        }
        final String finalToolName = toolName;

        // Create tool invocation structured data
        Map<String, Object> toolInvocation = new HashMap<>();
        toolInvocation.put("tool_name", finalToolName);
        toolInvocation.put("tool_input", toolInput != null ? toolInput : "");
        toolInvocation.put("tool_output", toolOutput != null ? toolOutput : "");

        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("tool_invocations", List.of(toolInvocation));

        // Create namespace map
        Map<String, String> namespaceMap = new HashMap<>();
        namespaceMap.put("session_id", sessionId);
        namespaceMap.put("user_id", ownerId);

        // Create metadata map
        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("status", "checkpoint");
        if (traceNum != null) {
            metadataMap.put("trace_num", traceNum.toString());
        }
        if (action != null) {
            metadataMap.put("action", action);
        }
        if (parentMemoryId != null) {
            metadataMap.put("parent_memory_id", parentMemoryId);
        }

        // Create tags map with trace-specific information
        Map<String, String> tagsMap = new HashMap<>();
        tagsMap.put("data_type", "trace");

        if (action != null) {
            tagsMap.put("topic", action);
        }

        /*
         * IMPORTANT: Tool trace data uses infer=false to prevent long-term memory extraction
         * 
         * Rationale:
         * 1. Tool traces are intermediate execution steps, not final user-facing content
         * 2. Running LLM inference on tool traces would create fragmented, low-quality long-term memories
         * 3. Multiple tool executions in a single conversation would generate redundant/duplicate facts
         * 4. Tool trace data is already structured (tool_name, tool_input, tool_output) and queryable
         * 5. Final conversation interactions (saveInteraction) will trigger proper semantic extraction
         * 
         * Example problem if infer=true:
         *   User: "What's the weather in Seattle?"
         *   - Tool trace saved → LLM extracts: "User queried Seattle" (incomplete context)
         *   - Final response saved → LLM extracts: "User asked about Seattle weather" (complete context)
         *   Result: Duplicate/conflicting long-term memories
         * 
         * By setting infer=false for tool traces:
         * - Tool execution data remains queryable via structured data search
         * - Long-term memory extraction happens only on final, contextually complete interactions
         * - Cleaner, more accurate long-term memory without duplication
         * - Reduced LLM inference costs and processing overhead
         */
        executeTraceDataSave(structuredData, namespaceMap, metadataMap, tagsMap, false, finalToolName, action, listener);
    }

    /**
     * Execute the actual trace data save operation.
     * 
     * <p>Note: The infer parameter is kept for potential future use cases where selective
     * inference on tool traces might be needed, but currently always receives false to
     * prevent duplicate long-term memory extraction.</p>
     * 
     * @param structuredData The structured data containing tool invocation information
     * @param namespaceMap The namespace mapping for the memory
     * @param metadataMap The metadata for the memory entry
     * @param tagsMap The tags for the memory entry
     * @param infer Whether to enable inference processing (currently always false for tool traces)
     * @param toolName The name of the tool (for logging)
     * @param action The action identifier (for logging)
     * @param listener ActionListener to handle the response
     */
    private void executeTraceDataSave(
        Map<String, Object> structuredData,
        Map<String, String> namespaceMap,
        Map<String, String> metadataMap,
        Map<String, String> tagsMap,
        boolean infer,
        String toolName,
        String action,
        ActionListener<String> listener
    ) {
        try {
            MLAddMemoriesInput input = MLAddMemoriesInput
                .builder()
                .memoryContainerId(memoryContainerId)
                .structuredData(structuredData)
                .namespace(namespaceMap)
                .metadata(metadataMap)
                .tags(tagsMap)
                .ownerId(ownerId)
                .payloadType(PayloadType.DATA)
                .infer(infer)
                .build();

            MLAddMemoriesRequest request = new MLAddMemoriesRequest(input);

            client.execute(MLAddMemoriesAction.INSTANCE, request, ActionListener.wrap(addResponse -> {
                log
                    .info(
                        "AgenticMemoryAdapter.saveTraceData: Successfully saved trace data with ID: {}, toolName: {}, action: {}, infer: {}",
                        addResponse.getWorkingMemoryId(),
                        toolName,
                        action,
                        infer
                    );
                listener.onResponse(addResponse.getWorkingMemoryId());
            }, ex -> {
                log
                    .error(
                        "AgenticMemoryAdapter.saveTraceData: Failed to save trace data for tool: {}, action: {}, infer: {}. Error: {}",
                        toolName,
                        action,
                        infer,
                        ex.getMessage(),
                        ex
                    );
                listener.onFailure(ex);
            }));
        } catch (Exception e) {
            log
                .error(
                    "AgenticMemoryAdapter.saveTraceData: Exception while building trace data save request for tool: {}, action: {}",
                    toolName,
                    action,
                    e
                );
            listener.onFailure(e);
        }
    }

    /**
     * Check if the memory container has an LLM ID configured for inference
     * @param callback ActionListener to handle the result (true if LLM ID exists, false otherwise)
     */
    private void hasLlmIdConfigured(ActionListener<Boolean> callback) {
        MLMemoryContainerGetRequest getRequest = MLMemoryContainerGetRequest.builder().memoryContainerId(memoryContainerId).build();

        client.execute(MLMemoryContainerGetAction.INSTANCE, getRequest, ActionListener.wrap(response -> {
            boolean hasLlmId = response.getMlMemoryContainer().getConfiguration().getLlmId() != null;
            log.info("Memory container {} has LLM ID configured: {}", memoryContainerId, hasLlmId);
            callback.onResponse(hasLlmId);
        }, ex -> {
            log
                .warn(
                    "Failed to get memory container {} configuration, defaulting infer to false. Error: {}",
                    memoryContainerId,
                    ex.getMessage(),
                    ex
                );
            callback.onResponse(false);
        }));
    }

    private List<Map<String, Object>> createTextContent(String text) {
        return List.of(Map.of("type", "text", "text", text));
    }

    private List<ChatMessage> parseAgenticMemoryResponse(SearchResponse response) {
        List<ChatMessage> chatMessages = new ArrayList<>();

        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();

            // Parse working memory documents (conversational format)
            if ("conversational".equals(source.get("payload_type"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> messages = (List<Map<String, Object>>) source.get("messages");
                if (messages != null && messages.size() >= 2) {
                    // Extract user question and assistant response
                    String question = extractMessageText(messages.get(0)); // user message
                    String assistantResponse = extractMessageText(messages.get(1)); // assistant message

                    if (question != null && assistantResponse != null) {
                        // Add user message
                        ChatMessage userMessage = ChatMessage
                            .builder()
                            .id(hit.getId() + "_user")
                            .timestamp(Instant.ofEpochMilli((Long) source.get("created_time")))
                            .sessionId(getSessionIdFromNamespace(source))
                            .role("user")
                            .content(question)
                            .contentType("text")
                            .origin("agentic_memory_working")
                            .metadata(
                                Map
                                    .of(
                                        "payload_type",
                                        source.get("payload_type"),
                                        "memory_container_id",
                                        source.get("memory_container_id"),
                                        "namespace",
                                        source.get("namespace"),
                                        "tags",
                                        source.get("tags")
                                    )
                            )
                            .build();
                        chatMessages.add(userMessage);

                        // Add assistant message
                        ChatMessage assistantMessage = ChatMessage
                            .builder()
                            .id(hit.getId() + "_assistant")
                            .timestamp(Instant.ofEpochMilli((Long) source.get("created_time")))
                            .sessionId(getSessionIdFromNamespace(source))
                            .role("assistant")
                            .content(assistantResponse)
                            .contentType("text")
                            .origin("agentic_memory_working")
                            .metadata(
                                Map
                                    .of(
                                        "payload_type",
                                        source.get("payload_type"),
                                        "memory_container_id",
                                        source.get("memory_container_id"),
                                        "namespace",
                                        source.get("namespace"),
                                        "tags",
                                        source.get("tags")
                                    )
                            )
                            .build();
                        chatMessages.add(assistantMessage);
                    }
                }
            }
            // Parse long-term memory documents (extracted facts)
            else if (source.containsKey("memory") && source.containsKey("strategy_type")) {
                String memory = (String) source.get("memory");
                String strategyType = (String) source.get("strategy_type");

                // Convert extracted facts to chat message format for context
                ChatMessage contextMessage = ChatMessage
                    .builder()
                    .id(hit.getId())
                    .timestamp(Instant.ofEpochMilli((Long) source.get("created_time")))
                    .sessionId(sessionId) // Use current session
                    .role("system") // System context message
                    .content("Context (" + strategyType + "): " + memory) // The extracted fact with context
                    .contentType("context")
                    .origin("agentic_memory_longterm")
                    .metadata(
                        Map
                            .of(
                                "strategy_type",
                                strategyType,
                                "strategy_id",
                                source.get("strategy_id"),
                                "memory_container_id",
                                source.get("memory_container_id")
                            )
                    )
                    .build();
                chatMessages.add(contextMessage);
            }
        }

        // Sort by timestamp to maintain chronological order
        chatMessages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        return chatMessages;
    }

    private String extractMessageText(Map<String, Object> message) {
        if (message == null)
            return null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        if (content != null && !content.isEmpty()) {
            Map<String, Object> firstContent = content.get(0);
            return (String) firstContent.get("text");
        }
        return null;
    }

    private String getSessionIdFromNamespace(Map<String, Object> source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> namespace = (Map<String, Object>) source.get("namespace");
        return namespace != null ? (String) namespace.get("session_id") : null;
    }

    @Override
    public void updateInteraction(String interactionId, java.util.Map<String, Object> updateFields, ActionListener<Void> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("ActionListener cannot be null");
        }
        if (interactionId == null || interactionId.trim().isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Interaction ID is required and cannot be empty"));
            return;
        }
        if (updateFields == null || updateFields.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Update fields are required and cannot be empty"));
            return;
        }

        try {
            log
                .info(
                    "AgenticMemoryAdapter.updateInteraction: CALLED - Updating interaction {} with fields: {} in memory container: {}",
                    interactionId,
                    updateFields.keySet(),
                    memoryContainerId
                );

            // Convert updateFields to the format expected by memory container API
            Map<String, Object> updateContent = new java.util.HashMap<>();

            // Handle the response field - this is the main field we need to update
            if (updateFields.containsKey("response")) {
                String response = (String) updateFields.get("response");
                String question = (String) updateFields.getOrDefault("input", "");

                // For working memory updates, we need to provide the complete messages array
                // with both user question and assistant response
                List<Map<String, Object>> messages = Arrays
                    .asList(
                        Map.of("role", "user", "content", createTextContent(question)),
                        Map.of("role", "assistant", "content", createTextContent(response))
                    );

                updateContent.put("messages", messages);

                log
                    .debug(
                        "AgenticMemoryAdapter.updateInteraction: Updating messages for interaction {} with question: '{}' and response length: {}",
                        interactionId,
                        question.length() > 50 ? question.substring(0, 50) + "..." : question,
                        response.length()
                    );
            }

            // Handle other fields that might be updated
            if (updateFields.containsKey("additional_info")) {
                updateContent.put("additional_info", updateFields.get("additional_info"));
            }

            MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId(memoryContainerId)
                .memoryType(MemoryType.WORKING) // We're updating working memory
                .memoryId(interactionId)
                .mlUpdateMemoryInput(input)
                .build();

            client.execute(MLUpdateMemoryAction.INSTANCE, request, ActionListener.wrap(updateResponse -> {
                log
                    .debug(
                        "AgenticMemoryAdapter.updateInteraction: Successfully updated interaction {} in memory container: {}",
                        interactionId,
                        memoryContainerId
                    );
                listener.onResponse(null);
            }, ex -> {
                log
                    .error(
                        "AgenticMemoryAdapter.updateInteraction: Failed to update interaction {} in memory container {}",
                        interactionId,
                        memoryContainerId,
                        ex
                    );
                listener.onFailure(ex);
            }));

        } catch (Exception e) {
            log
                .error(
                    "AgenticMemoryAdapter.updateInteraction: Exception while updating interaction {} in memory container {}",
                    interactionId,
                    memoryContainerId,
                    e
                );
            listener.onFailure(e);
        }
    }

}
