/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.script.ScriptService;
import org.opensearch.search.SearchHit;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Remote agentic memory implementation backed by connector-defined REST APIs.
 */
@Log4j2
@Getter
public class RemoteAgenticConversationMemory implements Memory<Message, CreateInteractionResponse, UpdateResponse> {

    public static final String TYPE = MLMemoryType.REMOTE_AGENTIC_MEMORY.name();
    private static final String SESSION_ID_FIELD = "session_id";
    private static final String CREATED_TIME_FIELD = "created_time";
    private static final Gson GSON = new Gson();

    private final String conversationId;
    private final String memoryContainerId;
    private final String userId;
    private final Connector connector;
    private final RemoteConnectorExecutor executor;

    // Dependencies for connector execution
    private final ScriptService scriptService;
    private final ClusterService clusterService;
    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    public RemoteAgenticConversationMemory(
        String memoryId,
        String memoryContainerId,
        String userId,
        Connector connector,
        ScriptService scriptService,
        ClusterService clusterService,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        this.conversationId = memoryId;
        this.memoryContainerId = memoryContainerId;
        this.userId = userId;
        this.connector = connector;
        this.scriptService = scriptService;
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;

        // Initialize the executor for the connector
        this.executor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
        this.executor.setScriptService(scriptService);
        this.executor.setClusterService(clusterService);
        this.executor.setClient(client);
        this.executor.setXContentRegistry(xContentRegistry);

        // Log creation for debugging/monitoring
        log
            .info(
                "RemoteAgenticConversationMemory created - sessionId: {}, containerId: {}, endpoint: {}, protocol: {}",
                memoryId,
                memoryContainerId,
                connector.getParameters() != null ? connector.getParameters().get("endpoint") : "unknown",
                connector.getProtocol()
            );
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
            log.info("Saved message to remote agentic memory, session id: {}, working memory id: {}", conversationId, r.getId());
        }, e -> { log.error("Failed to save message to remote agentic memory", e); }));
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
                        "Memory container ID is not configured for this RemoteAgenticConversationMemory. "
                            + "Cannot save messages without a valid memory container."
                    )
                );
            return;
        }

        ConversationIndexMessage msg = (ConversationIndexMessage) message;

        // Build namespace with session_id and optionally user_id
        Map<String, String> namespace = new HashMap<>();
        namespace.put(SESSION_ID_FIELD, conversationId);
        if (!Strings.isNullOrEmpty(userId)) {
            namespace.put("user_id", userId);
        }

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

        // Build request body for add_memory action
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("memory_container_id", memoryContainerId);
        requestBody.put("structured_data", structuredData);
        requestBody.put("message_id", traceNum); // Store trace number in messageId field (null for messages)
        requestBody.put("namespace", namespace);
        requestBody.put("metadata", metadata);
        requestBody.put("infer", false); // Don't infer long-term memory by default

        // Execute the connector action
        executeConnectorAction("add_memory", requestBody, ActionListener.wrap(response -> {
            // Parse response using proper Response class
            MLAddMemoriesResponse addResponse = parseAddMemoryResponse(response);
            String workingMemoryId = addResponse.getWorkingMemoryId();
            CreateInteractionResponse interactionResponse = new CreateInteractionResponse(workingMemoryId);
            listener.onResponse(interactionResponse);
        }, e -> {
            log.error("Failed to add memories to remote memory container", e);
            listener.onFailure(e);
        }));
    }

    @Override
    public void update(String messageId, Map<String, Object> updateContent, ActionListener<UpdateResponse> updateListener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            updateListener
                .onFailure(new IllegalStateException("Memory container ID is not configured for this RemoteAgenticConversationMemory"));
            return;
        }

        // Use retry mechanism for AOSS compatibility (high refresh latency)
        updateWithRetry(messageId, updateContent, updateListener, 0);
    }

    /**
     * Update with retry mechanism to handle AOSS refresh latency (up to 10s)
     * Uses exponential backoff: 500ms, 1s, 2s, 4s, 8s
     */
    private void updateWithRetry(
        String messageId,
        Map<String, Object> updateContent,
        ActionListener<UpdateResponse> updateListener,
        int attemptNumber
    ) {
        final int maxRetries = 5;
        final long baseDelayMs = 500;

        // Step 1: Get the existing working memory to retrieve current structured_data
        Map<String, Object> getRequest = new HashMap<>();
        getRequest.put("memory_container_id", memoryContainerId);
        getRequest.put("memory_type", "working");
        getRequest.put("memory_id", messageId);

        executeConnectorAction("get_memory", getRequest, ActionListener.wrap(getResponse -> {
            // Step 2: Extract existing structured_data using proper Response class
            MLGetMemoryResponse memoryResponse = parseGetMemoryResponse(getResponse);
            MLWorkingMemory workingMemory = memoryResponse.getWorkingMemory();

            Map<String, Object> structuredData;
            if (workingMemory == null || workingMemory.getStructuredData() == null) {
                structuredData = new HashMap<>();
            } else {
                // Create a mutable copy
                structuredData = new HashMap<>(workingMemory.getStructuredData());
            }

            // Step 3: Merge update content into structured_data
            for (Map.Entry<String, Object> entry : updateContent.entrySet()) {
                structuredData.put(entry.getKey(), entry.getValue());
            }

            // Step 4: Create update request with merged structured_data
            Map<String, Object> finalUpdateContent = new HashMap<>();
            finalUpdateContent.put("structured_data", structuredData);

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("memory_container_id", memoryContainerId);
            updateRequest.put("memory_type", "working");
            updateRequest.put("memory_id", messageId);
            updateRequest.put("update_content", finalUpdateContent);

            // Step 5: Execute the update
            executeConnectorAction("update_memory", updateRequest, ActionListener.wrap(response -> {
                try {
                    // Parse using standard UpdateResponse parser
                    UpdateResponse updateResponse = parseUpdateResponse(response);
                    updateListener.onResponse(updateResponse);
                } catch (Exception parseException) {
                    log.error("Failed to parse update response from remote memory", parseException);
                    updateListener.onFailure(parseException);
                }
            }, e -> {
                log.error("Failed to update memory in remote memory container", e);
                updateListener.onFailure(e);
            }));
        }, e -> {
            // Check if it's a 404 (document not found) and we haven't exceeded max retries
            boolean isNotFound = e.getMessage() != null && (e.getMessage().contains("404") || e.getMessage().contains("\"found\":false"));

            if (isNotFound && attemptNumber < maxRetries) {
                // Calculate delay with exponential backoff
                long delayMs = baseDelayMs * (1L << attemptNumber);

                log
                    .warn(
                        "Document not found (attempt {}/{}), retrying after {}ms due to refresh latency. MessageId: {}",
                        attemptNumber + 1,
                        maxRetries,
                        delayMs,
                        messageId
                    );

                // Schedule retry after delay
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    updateListener.onFailure(new RuntimeException("Retry interrupted", ie));
                    return;
                }

                // Retry
                updateWithRetry(messageId, updateContent, updateListener, attemptNumber + 1);
            } else {
                if (attemptNumber >= maxRetries) {
                    log.error("Failed to get existing memory after {} retries. MessageId: {}", maxRetries, messageId, e);
                } else {
                    log.error("Failed to get existing memory for update. MessageId: {}", messageId, e);
                }
                updateListener.onFailure(e);
            }
        }));
    }

    @Override
    public void getMessages(int size, ActionListener<List<Message>> listener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this RemoteAgenticConversationMemory"));
            return;
        }

        // Build search query for working memory by session_id, filtering only final messages (not traces)
        Map<String, Object> query = new HashMap<>();
        Map<String, Object> bool = new HashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();
        List<Map<String, Object>> mustNot = new ArrayList<>();

        // Must match session_id
        Map<String, Object> sessionTerm = new HashMap<>();
        sessionTerm.put("namespace." + SESSION_ID_FIELD, conversationId);
        must.add(Map.of("term", sessionTerm));

        // Must not have trace_number (exclude traces)
        mustNot.add(Map.of("exists", Map.of("field", "structured_data.trace_number")));

        bool.put("must", must);
        bool.put("must_not", mustNot);
        query.put("bool", bool);

        // Build search request
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put("memory_container_id", memoryContainerId);
        searchRequest.put("memory_type", "working");
        searchRequest.put("query", query);
        searchRequest.put("size", size);
        searchRequest.put("sort", List.of(Map.of(CREATED_TIME_FIELD, "asc")));

        executeConnectorAction("search_memories", searchRequest, ActionListener.wrap(response -> {
            List<Message> interactions = parseSearchResponseToInteractions(response);
            listener.onResponse(interactions);
        }, e -> {
            log.error("Failed to search memories in remote memory container", e);
            listener.onFailure(e);
        }));
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear method is not supported in RemoteAgenticConversationMemory");
    }

    @Override
    public void deleteInteractionAndTrace(String interactionId, ActionListener<Boolean> listener) {
        // For now, delegate to a simple implementation
        // In the future, this could use delete_memory action
        log.warn("deleteInteractionAndTrace is not fully implemented for RemoteAgenticConversationMemory");
        listener.onResponse(false);
    }

    /**
     * Get traces (intermediate steps/tool usage) for a specific parent message
     * @param parentMessageId The parent message ID
     * @param listener Action listener for the traces
     */
    public void getTraces(String parentMessageId, ActionListener<List<Interaction>> listener) {
        if (Strings.isNullOrEmpty(memoryContainerId)) {
            listener.onFailure(new IllegalStateException("Memory container ID is not configured for this RemoteAgenticConversationMemory"));
            return;
        }

        // Build search query for traces by parent_message_id
        Map<String, Object> query = new HashMap<>();
        Map<String, Object> bool = new HashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();

        // Must match session_id
        Map<String, Object> sessionTerm = new HashMap<>();
        sessionTerm.put("namespace." + SESSION_ID_FIELD, conversationId);
        must.add(Map.of("term", sessionTerm));

        // Must be a trace
        Map<String, Object> typeTerm = new HashMap<>();
        typeTerm.put("metadata.type", "trace");
        must.add(Map.of("term", typeTerm));

        // Must have specific parent_message_id
        Map<String, Object> parentTerm = new HashMap<>();
        parentTerm.put("metadata.parent_message_id", parentMessageId);
        must.add(Map.of("term", parentTerm));

        bool.put("must", must);
        query.put("bool", bool);

        // Build search request
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put("memory_container_id", memoryContainerId);
        searchRequest.put("memory_type", "working");
        searchRequest.put("query", query);
        searchRequest.put("size", 1000); // Get all traces for this message
        searchRequest.put("sort", List.of(Map.of("message_id", "asc"))); // Sort by trace number

        executeConnectorAction("search_memories", searchRequest, ActionListener.wrap(response -> {
            List<Interaction> traces = parseSearchResponseToTraces(response);
            listener.onResponse(traces);
        }, e -> {
            log.error("Failed to search traces in remote memory container", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Helper method to execute connector actions
     */
    private void executeConnectorAction(String action, Map<String, Object> parameters, ActionListener<String> listener) {
        // Log the action being executed for debugging
        if (log.isDebugEnabled()) {
            Map<String, Object> actionDebug = new HashMap<>();
            actionDebug.put("action", action);

            // Log parameter keys but mask values that might be sensitive
            if (parameters != null && !parameters.isEmpty()) {
                Map<String, Object> paramKeys = new HashMap<>();
                for (String key : parameters.keySet()) {
                    Object value = parameters.get(key);
                    paramKeys.put(key, value);
                }
                actionDebug.put("parameters", paramKeys);
            }

            log.debug("Executing RemoteAgenticConversationMemory action: {}", GSON.toJson(actionDebug));
        }

        // Use the cached executor that was initialized in the constructor
        // The executor already has the connector with all actions defined and decrypted credentials

        // Prepare parameters for the action
        Map<String, String> inputParams = new HashMap<>();

        // Add required parameters that match the URL template placeholders
        inputParams.put("memory_container_id", (String) parameters.get("memory_container_id"));

        if (parameters.containsKey("memory_id")) {
            inputParams.put("memory_id", (String) parameters.get("memory_id"));
        }
        if (parameters.containsKey("memory_type")) {
            inputParams.put("memory_type", (String) parameters.get("memory_type"));
        }

        // Build request body based on action type
        String requestBody = buildRequestBody(action, parameters);
        if (requestBody != null) {
            inputParams.put("body", requestBody);
        }

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(inputParams).build();

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.CONNECTOR).inputDataset(inputDataSet).build();

        // Execute the action - the executor will find the action by name in the connector's actions list
        this.executor.executeAction(action, mlInput, ActionListener.wrap(response -> {
            String output;

            // Try to extract the actual data from ModelTensorOutput wrapper
            Map<String, ?> dataMap = extractDataFromModelTensorOutput(response);

            if (dataMap != null) {
                // Convert the extracted data map back to JSON
                output = GSON.toJson(dataMap);
            } else if (response instanceof MLTaskResponse) {
                // Fallback: if extraction fails, use toString() as before
                MLTaskResponse taskResponse = (MLTaskResponse) response;
                output = taskResponse.getOutput() != null ? taskResponse.getOutput().toString() : "{}";
            } else {
                output = response.toString();
            }

            // Log successful response for debugging (truncate if too long)
            if (log.isDebugEnabled()) {
                String debugOutput = output;
                if (debugOutput.length() > 500) {
                    debugOutput = debugOutput.substring(0, 500) + "... [truncated]";
                }
                log.debug("RemoteAgenticConversationMemory action '{}' response: {}", action, debugOutput);
            }

            listener.onResponse(output);
        }, e -> {
            log.error("Failed to execute connector action '{}' for RemoteAgenticConversationMemory: {}", action, e.getMessage(), e);
            listener.onFailure(e);
        }));
    }

    /**
     * Build request body based on action type
     */
    private String buildRequestBody(String action, Map<String, Object> parameters) {
        switch (action) {
            case "add_memory":
                return GSON.toJson(parameters);
            case "search_memories":
                Map<String, Object> searchBody = new HashMap<>();
                searchBody.put("query", parameters.get("query"));
                if (parameters.containsKey("size")) {
                    searchBody.put("size", parameters.get("size"));
                }
                if (parameters.containsKey("sort")) {
                    searchBody.put("sort", parameters.get("sort"));
                }
                return GSON.toJson(searchBody);
            case "update_memory":
                return GSON.toJson(parameters.get("update_content"));
            case "create_session":
                Map<String, Object> sessionBody = new HashMap<>();
                if (parameters.containsKey("summary")) {
                    sessionBody.put("summary", parameters.get("summary"));
                }
                return GSON.toJson(sessionBody);
            default:
                return null;
        }
    }

    /**
     * Parse JSON string into SearchResponse using OpenSearch's standard parser
     */
    private SearchResponse parseSearchResponse(String jsonResponse) throws IOException {
        try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, jsonResponse)) {
            return SearchResponse.fromXContent(parser);
        }
    }

    /**
     * Parse JSON string into GetResponse using OpenSearch's standard parser
     */
    private GetResponse parseGetResponse(String jsonResponse) throws IOException {
        try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, jsonResponse)) {
            return GetResponse.fromXContent(parser);
        }
    }

    /**
     * Parse JSON string into UpdateResponse using OpenSearch's standard parser
     */
    private UpdateResponse parseUpdateResponse(String jsonResponse) throws IOException {
        try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, jsonResponse)) {
            return UpdateResponse.fromXContent(parser);
        }
    }

    /**
     * Parse add memory response using XContentParser
     */
    private MLAddMemoriesResponse parseAddMemoryResponse(String jsonResponse) {
        try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, jsonResponse)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

            String workingMemoryId = null;
            String sessionId = null;
            List<MemoryResult> results = new ArrayList<>();

            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case "working_memory_id":
                        workingMemoryId = parser.text();
                        break;
                    case "session_id":
                        sessionId = parser.text();
                        break;
                    case "long_term_memories":
                        // Parse array if needed in future
                        parser.skipChildren();
                        break;
                    default:
                        parser.skipChildren();
                }
            }

            return MLAddMemoriesResponse.builder().workingMemoryId(workingMemoryId).sessionId(sessionId).results(results).build();
        } catch (Exception e) {
            log.error("Failed to parse add memory response: " + jsonResponse, e);
            // Return a minimal response with null values
            return MLAddMemoriesResponse.builder().build();
        }
    }

    /**
     * Parse get memory response using XContentParser
     * Following the pattern from MLGetMemoryResponse.fromGetResponse
     */
    private MLGetMemoryResponse parseGetMemoryResponse(String jsonResponse) {
        try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, jsonResponse)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

            // Parse the entire response as MLWorkingMemory (matching MLGetMemoryResponse.fromGetResponse pattern)
            MLWorkingMemory workingMemory = MLWorkingMemory.parse(parser);

            return MLGetMemoryResponse.builder().workingMemory(workingMemory).build();
        } catch (Exception e) {
            log.error("Failed to parse get memory response: " + jsonResponse, e);
            return MLGetMemoryResponse.builder().build();
        }
    }

    private List<Message> parseSearchResponseToInteractions(String response) {
        try {
            SearchResponse searchResponse = parseSearchResponse(response);
            return convertSearchHitsToMessages(searchResponse);
        } catch (Exception e) {
            log.error("Failed to parse search response: " + response, e);
            return new ArrayList<>();
        }
    }

    /**
     * Convert SearchResponse hits to Message list
     */
    private List<Message> convertSearchHitsToMessages(SearchResponse searchResponse) {
        List<Message> messages = new ArrayList<>();
        if (searchResponse.getHits() != null && searchResponse.getHits().getHits() != null) {
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                try {
                    Interaction interaction = convertHitToInteraction(hit, false);
                    if (interaction != null) {
                        messages.add(interaction);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse hit: " + hit.getId(), e);
                }
            }
        }
        return messages;
    }

    /**
     * Convert a SearchHit to an Interaction object
     * @param hit The SearchHit from the SearchResponse
     * @param isTrace Whether this is a trace (true) or a message (false)
     * @return Interaction object or null if conversion fails
     */
    private Interaction convertHitToInteraction(SearchHit hit, boolean isTrace) {
        String id = hit.getId();
        Map<String, Object> source = hit.getSourceAsMap();

        if (source != null && source.containsKey("structured_data")) {
            Map<String, Object> structuredData = (Map<String, Object>) source.get("structured_data");

            String input = (String) structuredData.getOrDefault("input", "");
            String responseText = (String) structuredData.getOrDefault("response", "");

            // For traces, extract origin from structured_data
            String origin = isTrace ? (String) structuredData.getOrDefault("origin", "remote_agentic_memory") : "remote_agentic_memory";

            // Extract timestamps
            Long createdTimeMs = source.containsKey("created_time") ? ((Number) source.get("created_time")).longValue() : null;
            Long updatedTimeMs = source.containsKey("last_updated_time") ? ((Number) source.get("last_updated_time")).longValue() : null;

            java.time.Instant createTime = createdTimeMs != null ? java.time.Instant.ofEpochMilli(createdTimeMs) : java.time.Instant.now();
            java.time.Instant updatedTime = updatedTimeMs != null ? java.time.Instant.ofEpochMilli(updatedTimeMs) : null;

            // Extract metadata
            String parentInteractionId = null;
            Integer traceNumber = null;

            if (source.containsKey("metadata")) {
                Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                parentInteractionId = (String) metadata.get("parent_message_id");

                // For traces, extract trace number
                if (isTrace && metadata.containsKey("trace_number")) {
                    Object traceNum = metadata.get("trace_number");
                    if (traceNum instanceof Number) {
                        traceNumber = ((Number) traceNum).intValue();
                    }
                }
            }

            // For traces, also check parent_message_id in structured_data
            if (isTrace && parentInteractionId == null && structuredData.containsKey("parent_message_id")) {
                parentInteractionId = (String) structuredData.get("parent_message_id");
            }

            // For traces, extract trace number from structured_data if not found in metadata
            if (isTrace && traceNumber == null) {
                if (structuredData.containsKey("trace_number")) {
                    Object traceNum = structuredData.get("trace_number");
                    if (traceNum instanceof Number) {
                        traceNumber = ((Number) traceNum).intValue();
                    }
                }
                // Also check message_id field at root level for traces
                if (traceNumber == null && source.containsKey("message_id")) {
                    Object msgId = source.get("message_id");
                    if (msgId instanceof Number) {
                        traceNumber = ((Number) msgId).intValue();
                    }
                }
            }

            // Create Interaction object
            if (!input.isEmpty() || !responseText.isEmpty()) {
                return Interaction
                    .builder()
                    .id(id)
                    .conversationId(conversationId)
                    .createTime(createTime)
                    .updatedTime(updatedTime)
                    .input(input)
                    .response(responseText)
                    .origin(origin)
                    .promptTemplate(null)
                    .additionalInfo(null)
                    .parentInteractionId(parentInteractionId)
                    .traceNum(traceNumber)
                    .build();
            }
        }

        return null;
    }

    private List<Interaction> parseSearchResponseToTraces(String response) {
        try {
            SearchResponse searchResponse = parseSearchResponse(response);
            return convertSearchHitsToTraces(searchResponse);
        } catch (Exception e) {
            log.error("Failed to parse trace response: " + response, e);
            return new ArrayList<>();
        }
    }

    /**
     * Convert SearchResponse hits to Interaction list (for traces)
     */
    private List<Interaction> convertSearchHitsToTraces(SearchResponse searchResponse) {
        List<Interaction> traces = new ArrayList<>();
        if (searchResponse.getHits() != null && searchResponse.getHits().getHits() != null) {
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                try {
                    Interaction trace = convertHitToInteraction(hit, true);
                    if (trace != null) {
                        traces.add(trace);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse trace hit: " + hit.getId(), e);
                }
            }
        }
        return traces;
    }

    /**
     * Extract data map from ModelTensorOutput response
     * This handles the ML Commons output wrapping that converts raw responses into ModelTensorOutput structures
     *
     * @param response The response object, typically MLTaskResponse
     * @return Map containing the actual response data, or null if extraction fails
     */
    protected static Map<String, ?> extractDataFromModelTensorOutput(Object response) {
        if (response instanceof MLTaskResponse) {
            MLTaskResponse taskResponse = (MLTaskResponse) response;
            MLOutput mlOutput = taskResponse.getOutput();

            if (mlOutput instanceof ModelTensorOutput) {
                ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                List<ModelTensors> outputs = tensorOutput.getMlModelOutputs();

                if (outputs != null && !outputs.isEmpty()) {
                    List<ModelTensor> tensors = outputs.get(0).getMlModelTensors();
                    if (tensors != null && !tensors.isEmpty()) {
                        return tensors.get(0).getDataAsMap();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Factory for creating RemoteAgenticConversationMemory instances
     */
    public static class Factory implements Memory.Factory<RemoteAgenticConversationMemory> {
        private ScriptService scriptService;
        private ClusterService clusterService;
        private Client client;
        private NamedXContentRegistry xContentRegistry;

        public void init(
            ScriptService scriptService,
            ClusterService clusterService,
            Client client,
            NamedXContentRegistry xContentRegistry
        ) {
            this.scriptService = scriptService;
            this.clusterService = clusterService;
            this.client = client;
            this.xContentRegistry = xContentRegistry;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<RemoteAgenticConversationMemory> listener) {
            if (map == null || map.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating RemoteAgenticConversationMemory"));
                return;
            }

            String memoryId = (String) map.get(MEMORY_ID);
            String name = (String) map.get(MEMORY_NAME);
            String appType = (String) map.get(APP_TYPE);
            String memoryContainerId = (String) map.get("memory_container_id");

            // Extract inline connector metadata
            String endpoint = (String) map.get("endpoint");
            String region = (String) map.get("region");
            Map<String, String> credential = (Map<String, String>) map.get("credential");
            String userId = (String) map.get("user_id");

            create(name, memoryId, appType, memoryContainerId, endpoint, region, credential, userId, listener);
        }

        public void create(
            String name,
            String memoryId,
            String appType,
            String memoryContainerId,
            String endpoint,
            String region,
            Map<String, String> credential,
            String userId,
            ActionListener<RemoteAgenticConversationMemory> listener
        ) {
            // Memory container ID is required
            if (Strings.isNullOrEmpty(memoryContainerId)) {
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "Memory container ID is required for RemoteAgenticConversationMemory. "
                                + "Please provide 'memory_container_id' in the agent configuration."
                        )
                    );
                return;
            }

            // Inline connector parameters are required
            if (Strings.isNullOrEmpty(endpoint)) {
                listener.onFailure(new IllegalArgumentException("Endpoint is required for RemoteAgenticConversationMemory"));
                return;
            }

            // Create inline connector
            Connector connector = createInlineConnector(endpoint, region, credential);

            if (Strings.isEmpty(memoryId)) {
                // Create new session using create_session action
                createSessionInRemoteContainer(name, memoryContainerId, connector, ActionListener.wrap(sessionId -> {
                    create(sessionId, memoryContainerId, connector, userId, listener);
                    log.debug("Created session in remote memory container, session id: {}", sessionId);
                }, e -> {
                    log.error("Failed to create session in remote memory container", e);
                    listener.onFailure(e);
                }));
            } else {
                // Use existing session/memory ID
                create(memoryId, memoryContainerId, connector, userId, listener);
            }
        }

        /**
         * Create a new session in the remote memory container.
         *
         * This method uses the connector that already has all actions defined,
         * including the create_session action with the proper name field.
         */
        private void createSessionInRemoteContainer(
            String summary,
            String memoryContainerId,
            Connector connector,
            ActionListener<String> listener
        ) {
            // The connector already has all actions defined, including create_session
            // Log connector actions for debugging
            if (log.isDebugEnabled()) {
                if (connector.getActions() != null) {
                    log.debug("Connector has {} actions defined", connector.getActions().size());
                    for (ConnectorAction action : connector.getActions()) {
                        log.debug("Action: name='{}', actionType='{}'", action.getName(), action.getActionType());
                    }
                } else {
                    log.debug("Connector has no actions defined!");
                }
            }

            // Create executor for this connector
            RemoteConnectorExecutor executor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
            executor.setScriptService(scriptService);
            executor.setClusterService(clusterService);
            executor.setClient(client);
            executor.setXContentRegistry(xContentRegistry);

            // Prepare parameters for the action
            Map<String, String> inputParams = new HashMap<>();
            inputParams.put("memory_container_id", memoryContainerId);

            // Build request body for create_session
            Map<String, Object> sessionBody = new HashMap<>();
            if (summary != null) {
                sessionBody.put("summary", summary);
            }
            inputParams.put("body", GSON.toJson(sessionBody));

            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(inputParams).build();

            MLInput mlInput = MLInput.builder().algorithm(FunctionName.CONNECTOR).inputDataset(inputDataSet).build();

            // Execute the action - the executor will find the create_session action by name
            executor.executeAction("create_session", mlInput, ActionListener.wrap(response -> {
                try {
                    String sessionId = null;

                    // Extract data from ModelTensorOutput wrapper
                    Map<String, ?> dataMap = extractDataFromModelTensorOutput(response);

                    // Log the create session response for debugging
                    log.debug("Create session response - extracted data: {}", dataMap != null ? GSON.toJson(dataMap) : "null");

                    if (dataMap != null && dataMap.containsKey("session_id")) {
                        sessionId = (String) dataMap.get("session_id");
                    }

                    if (sessionId != null) {
                        listener.onResponse(sessionId);
                    } else {
                        listener.onFailure(new RuntimeException("Failed to parse session_id from response"));
                    }
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to create session via remote connector", e);
                listener.onFailure(e);
            }));
        }

        public void create(
            String memoryId,
            String memoryContainerId,
            Connector connector,
            String userId,
            ActionListener<RemoteAgenticConversationMemory> listener
        ) {
            listener
                .onResponse(
                    new RemoteAgenticConversationMemory(
                        memoryId,
                        memoryContainerId,
                        userId,
                        connector,
                        scriptService,
                        clusterService,
                        client,
                        xContentRegistry
                    )
                );
        }

        /**
         * Create inline connector from runtime parameters
         */
        private Connector createInlineConnector(String endpoint, String region, Map<String, String> credential) {
            // Validate endpoint
            if (!isValidEndpoint(endpoint)) {
                throw new IllegalArgumentException("Invalid endpoint URL: " + endpoint);
            }

            // Determine protocol based on credentials
            String protocol = (region != null && credential != null && !credential.isEmpty()) ? "aws_sigv4" : "http";

            // Build parameters
            Map<String, String> parameters = new HashMap<>();
            parameters.put("endpoint", endpoint);
            if (region != null) {
                parameters.put("region", region);
            }

            // For AWS services, we need to specify the service name
            // Extract from endpoint or default to "es" for OpenSearch/Elasticsearch
            String serviceName = extractServiceName(endpoint);
            parameters.put("service_name", serviceName);

            Map<String, String> credentials = new HashMap<>();
            if (credential != null && !credential.isEmpty()) {
                // Pass the credential map directly - it should already contain the correct structure
                // For AWS SigV4: access_key, secret_key, session_token (optional)
                // For other auth types: appropriate key-value pairs
                credentials.putAll(credential);
            }

            // Extract tenant ID from role ARN if applicable
            String tenantId = extractTenantIdFromRoleArn(serviceName, credentials);

            // Create Memory Container API actions
            List<ConnectorAction> actions = createMemoryContainerActions();

            // Create appropriate connector based on protocol
            Connector connector;
            if ("aws_sigv4".equals(protocol)) {
                // Use AwsConnector for AWS SigV4
                connector = AwsConnector
                    .awsConnectorBuilder()
                    .name("inline_remote_memory_connector")
                    .protocol(protocol)
                    .parameters(parameters)
                    .credential(credentials)
                    .actions(actions)
                    .tenantId(tenantId)
                    .build();
            } else {
                // Use HttpConnector for plain HTTP
                connector = HttpConnector
                    .builder()
                    .name("inline_remote_memory_connector")
                    .protocol(protocol)
                    .parameters(parameters)
                    .credential(credentials.isEmpty() ? null : credentials)
                    .actions(actions)
                    .tenantId(null)
                    .build();
            }

            // Log connector configuration for debugging (mask sensitive credentials)
            if (log.isDebugEnabled()) {
                Map<String, Object> debugInfo = new HashMap<>();
                debugInfo.put("name", connector.getName());
                debugInfo.put("protocol", connector.getProtocol());
                debugInfo.put("parameters", connector.getParameters());
                debugInfo
                    .put(
                        "actions",
                        actions
                            .stream()
                            .map(a -> a.getName() != null ? a.getName() : a.getActionType().toString())
                            .collect(Collectors.toList())
                    );

                // Log credential keys but not values for security
                if (credentials != null && !credentials.isEmpty()) {
                    debugInfo.put("credential_keys", credentials.keySet());
                }

                log.debug("Created inline connector for RemoteAgenticConversationMemory: {}", GSON.toJson(debugInfo));
            }

            // Decrypt the connector credentials (for inline connectors, credentials are already plaintext)
            // This populates the decryptedCredential field which AwsConnector methods depend on
            connector
                .decrypt(
                    ConnectorAction.ActionType.EXECUTE.name(),
                    (cred, tenant) -> cred,  // No-op function - credentials are already plaintext
                    tenantId
                );

            return connector;
        }

        /**
         * Create Memory Container API actions for the inline connector
         */
        private List<ConnectorAction> createMemoryContainerActions() {
            List<ConnectorAction> actions = new ArrayList<>();

            // Create session action
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("create_session")
                        .method("POST")
                        .url("${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories/sessions")
                        .headers(Map.of("Content-Type", "application/json"))
                        .requestBody("${parameters.body}")
                        .build()
                );

            // Add memory action
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("add_memory")
                        .method("POST")
                        .url("${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories")
                        .headers(Map.of("Content-Type", "application/json"))
                        .requestBody("${parameters.body}")
                        .build()
                );

            // Search memories action
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("search_memories")
                        .method("POST")
                        .url(
                            "${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories/${parameters.memory_type}/_search"
                        )
                        .headers(Map.of("Content-Type", "application/json"))
                        .requestBody("${parameters.body}")
                        .build()
                );

            // Get memory action
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("get_memory")
                        .method("GET")
                        .url(
                            "${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories/${parameters.memory_type}/${parameters.memory_id}"
                        )
                        .headers(Map.of("Content-Type", "application/json"))
                        .build()
                );

            // Update memory action
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("update_memory")
                        .method("PUT")
                        .url(
                            "${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories/${parameters.memory_type}/${parameters.memory_id}"
                        )
                        .headers(Map.of("Content-Type", "application/json"))
                        .requestBody("${parameters.body}")
                        .build()
                );

            // Delete memory action (if needed in the future)
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.EXECUTE)
                        .name("delete_memory")
                        .method("DELETE")
                        .url(
                            "${parameters.endpoint}/_plugins/_ml/memory_containers/${parameters.memory_container_id}/memories/${parameters.memory_type}/${parameters.memory_id}"
                        )
                        .headers(Map.of("Content-Type", "application/json"))
                        .build()
                );

            return actions;
        }

        /**
         * Helper method to extract service name from endpoint
         */
        private String extractServiceName(String endpoint) {
            // For AOSS endpoints: https://xxx.us-west-2.aoss.amazonaws.com
            if (endpoint.contains(".aoss.amazonaws.com")) {
                return "aoss";
            }
            // For managed OpenSearch (production, staging, integration)
            if (endpoint.contains(".es.amazonaws.com")
                || endpoint.contains(".es-staging.amazonaws.com")
                || endpoint.contains(".es-integ.amazonaws.com")) {
                return "es";
            }
            // Default to aoss for other OpenSearch services
            return "aoss";
        }

        /**
         * Extract dummy tenant ID from role ARN for AOSS services.
         * Essentially we only need the front part (account ID) as client ID when in AOSS
         *
         * @param serviceName The AWS service name (aoss or es)
         * @param credential The credential map that may contain roleArn
         * @return Tenant ID in format "account:role" for AOSS, null for ES or if roleArn not present
         *
         * Example:
         * - Input: serviceName="aoss", roleArn="arn:aws:iam::123456789012:role/role-name"
         * - Output: "123456789012:role"
         */
        private String extractTenantIdFromRoleArn(String serviceName, Map<String, String> credential) {
            // Return null for ES service
            if (!"aoss".equals(serviceName)) {
                return null;
            }

            // Check if credential map exists and contains roleArn
            if (credential == null || !credential.containsKey("roleArn")) {
                return null;
            }

            String roleArn = credential.get("roleArn");
            if (Strings.isNullOrEmpty(roleArn)) {
                return null;
            }

            // Expected format: arn:aws:iam::{account}:role/{role-name}
            try {
                String[] parts = roleArn.split(":");
                if (parts.length >= 6 && parts[5].startsWith("role/")) {
                    String account = parts[4];
                    return account + ":role";
                }
            } catch (Exception e) {
                log.error("Failed to parse roleArn: {}", roleArn, e);
            }

            return null;
        }

        /**
         * Validate endpoint URL
         */
        private boolean isValidEndpoint(String endpoint) {
            try {
                // Basic validation - ensure it starts with http:// or https://
                return endpoint != null && (endpoint.startsWith("http://") || endpoint.startsWith("https://"));
            } catch (Exception e) {
                return false;
            }
        }
    }

}
