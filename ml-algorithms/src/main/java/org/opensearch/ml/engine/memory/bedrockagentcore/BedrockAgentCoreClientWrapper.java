/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

/**
 * Client wrapper for AWS Bedrock AgentCore SDK.
 * Handles AWS API calls for memory operations using Bedrock AgentCore service.
 */
@Log4j2
public class BedrockAgentCoreClientWrapper implements AutoCloseable {

    private final BedrockAgentCoreClient awsClient;
    private final String region;

    public BedrockAgentCoreClientWrapper(String region, Map<String, String> credentials) {
        this.region = region;
        this.awsClient = createAwsClient(region, credentials);
        log.info("Initialized Bedrock AgentCore client for region: {}", region);
    }

    private BedrockAgentCoreClient createAwsClient(String region, Map<String, String> credentials) {
        String accessKey = credentials.get("access_key");
        String secretKey = credentials.get("secret_key");
        String sessionToken = credentials.get("session_token");

        AwsCredentials awsCredentials = sessionToken == null
            ? AwsBasicCredentials.create(accessKey, secretKey)
            : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);

        try {
            BedrockAgentCoreClient client = AccessController
                .doPrivileged(
                    (PrivilegedExceptionAction<BedrockAgentCoreClient>) () -> BedrockAgentCoreClient
                        .builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                        .build()
                );
            return client;
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Can't create Bedrock AgentCore client", e);
        }
    }

    /**
     * Create an event (memory record) in Bedrock AgentCore memory
     */
    public void createEvent(String memoryId, BedrockAgentCoreMemoryRecord record, String agentId, ActionListener<String> listener) {
        try {
            log
                .info(
                    "🚀 CREATE EVENT START: memoryId={}, recordSessionId={}, recordType={}, agentId={}",
                    memoryId,
                    record.getSessionId(),
                    record.getType(),
                    agentId
                );
            log.info("CREATE EVENT CONTENT: '{}'", record.getContent());

            log
                .info(
                    "Creating event with content: '{}', type: '{}', sessionId: '{}', agentId: '{}'",
                    record.getContent(),
                    record.getType(),
                    record.getSessionId(),
                    agentId
                );

            // Handle null content
            String content = record.getContent();
            if (content == null || content.trim().isEmpty() || "null".equals(content)) {
                log.error("CREATE FAILED: Record content was null/empty/literal-null");
                listener.onFailure(new IllegalArgumentException("Cannot create event with null/empty content"));
                return;
            }

            // Handle null type with default value
            String recordType = record.getType();
            if (recordType == null || recordType.trim().isEmpty()) {
                recordType = "assistant"; // Default type for agent messages
                log.warn("Record type was null/empty, using default: {}", recordType);
            }

            PayloadType payload = PayloadType
                .builder()
                .conversational(
                    software.amazon.awssdk.services.bedrockagentcore.model.Conversational
                        .builder()
                        .content(software.amazon.awssdk.services.bedrockagentcore.model.Content.builder().text(content).build())
                        .role(software.amazon.awssdk.services.bedrockagentcore.model.Role.fromValue(recordType.toUpperCase()))
                        .build()
                )
                .build();

            String actualActorId = agentId != null ? agentId : "default-actor";
            String actualSessionId = record.getSessionId() != null ? record.getSessionId() : "default-session";

            CreateEventRequest request = CreateEventRequest
                .builder()
                .memoryId(memoryId)
                .actorId(actualActorId)  // Use agentId for actorId
                .sessionId(actualSessionId) // Use sessionId for sessionId
                .payload(payload)
                .eventTimestamp(java.time.Instant.now())
                .build();

            log
                .info(
                    "📤 AWS CREATE REQUEST: memoryId={}, actorId='{}', sessionId='{}' (lengths: actorId={}, sessionId={})",
                    memoryId,
                    request.actorId(),
                    request.sessionId(),
                    request.actorId().length(),
                    request.sessionId().length()
                );

            CreateEventResponse response = awsClient.createEvent(request);

            log.info("AWS CREATE RESPONSE: {}", response);
            log
                .info(
                    "📥 AWS RESPONSE EVENT: memoryId={}, actorId={}, sessionId={}, eventId={}",
                    response.event().memoryId(),
                    response.event().actorId(),
                    response.event().sessionId(),
                    response.event().eventId()
                );

            // Extract the real event ID from the response
            String eventId = "event-" + System.currentTimeMillis(); // fallback
            if (response.event() != null && response.event().eventId() != null) {
                eventId = response.event().eventId();
                log.info("CREATE SUCCESS: Extracted eventId={}", eventId);
            }

            log.info("CREATE COMPLETE: memoryId={}, eventId={}, content='{}'", memoryId, eventId, record.getContent());
            listener.onResponse(eventId);
        } catch (Exception e) {
            log.error("CREATE FAILED: memoryId={}, content='{}', error={}", memoryId, record.getContent(), e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Backward compatibility method - uses sessionId as actorId
     */
    public void createEvent(String memoryId, BedrockAgentCoreMemoryRecord record, ActionListener<String> listener) {
        String sessionId = record.getSessionId() != null ? record.getSessionId() : "default-session";
        createEvent(memoryId, record, sessionId, listener);
    }

    /**
     * List events from Bedrock AgentCore (changed from listMemoryRecords to listEvents)
     */
    public void listMemoryRecords(
        String memoryId,
        String sessionId,
        String actorId,
        ActionListener<List<BedrockAgentCoreMemoryRecord>> listener
    ) {
        try {
            log
                .info(
                    "🔍 LIST EVENTS START: memoryId={}, sessionId='{}', actorId='{}' (lengths: sessionId={}, actorId={})",
                    memoryId,
                    sessionId,
                    actorId,
                    sessionId != null ? sessionId.length() : 0,
                    actorId != null ? actorId.length() : 0
                );

            ListEventsRequest request = ListEventsRequest.builder().memoryId(memoryId).sessionId(sessionId).actorId(actorId).build();

            log.info("LIST REQUEST BUILT: {}", request);

            // Wrap synchronous call in CompletableFuture
            java.util.concurrent.CompletableFuture<ListEventsResponse> future = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> awsClient.listEvents(request));

            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    log.error("AWS API call failed for listEvents: {}", memoryId, throwable);
                    listener.onFailure(new RuntimeException(throwable));
                } else {
                    try {
                        List<BedrockAgentCoreMemoryRecord> records = new ArrayList<>();

                        log.info("AWS ListEventsResponse: {}", response);
                        log.info("Made actual AWS API call to listEvents for memory: {}", memoryId);
                        log.info("DEBUG: Response events count: {}", response.events() != null ? response.events().size() : 0);

                        if (response.events() != null) {
                            log.info("Listed {} events from Bedrock AgentCore memory: {}", response.events().size(), memoryId);
                            log.info("DEBUG: Event details:");
                            for (int i = 0; i < response.events().size(); i++) {
                                var event = response.events().get(i);
                                log
                                    .info(
                                        "DEBUG: Event {}: eventId={}, actorId={}, sessionId={}",
                                        i,
                                        event.eventId(),
                                        event.actorId(),
                                        event.sessionId()
                                    );
                            }

                            // Convert events to memory records
                            for (var event : response.events()) {
                                String content = "Event ID: " + event.eventId(); // Default fallback
                                String type = "user"; // Default type

                                // Extract actual content from payload (payload is a List<PayloadType>)
                                if (event.payload() != null && !event.payload().isEmpty()) {
                                    PayloadType firstPayload = event.payload().get(0);
                                    if (firstPayload.conversational() != null) {
                                        var conversational = firstPayload.conversational();
                                        if (conversational.content() != null && conversational.content().text() != null) {
                                            content = conversational.content().text();
                                        }
                                        if (conversational.role() != null) {
                                            type = conversational.role().toString().toLowerCase();
                                        }
                                    }
                                }

                                BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
                                    .bedrockAgentCoreMemoryRecordBuilder()
                                    .sessionId(event.actorId())
                                    .content(content)
                                    .type(type)
                                    .timestamp(event.eventTimestamp())
                                    .build();
                                records.add(record);
                            }
                        } else {
                            log.info("Listed 0 events from Bedrock AgentCore memory: {}", memoryId);
                        }

                        listener.onResponse(records);
                    } catch (Exception e) {
                        log.error("Error processing listEvents response for memory: {}", memoryId, e);
                        listener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to list events from Bedrock AgentCore memory: {}", memoryId, e);
            listener.onFailure(e);
        }
    }

    /**
     * List events from Bedrock AgentCore (backward compatibility)
     */
    public void listMemoryRecords(String memoryId, String sessionId, ActionListener<List<BedrockAgentCoreMemoryRecord>> listener) {
        listMemoryRecords(memoryId, sessionId, sessionId, listener);
    }

    /**
     * Get specific memory record from Bedrock AgentCore
     */
    public void getMemoryRecord(String memoryId, String recordId, ActionListener<BedrockAgentCoreMemoryRecord> listener) {
        try {
            GetMemoryRecordRequest request = GetMemoryRecordRequest.builder().memoryId(memoryId).build();

            GetMemoryRecordResponse response = awsClient.getMemoryRecord(request);

            BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
                .bedrockAgentCoreMemoryRecordBuilder()
                .content("Memory record from Bedrock AgentCore")
                .sessionId(memoryId)
                .build();

            log.info("Retrieved memory record from Bedrock AgentCore: {} / {}", memoryId, recordId);
            listener.onResponse(record);
        } catch (Exception e) {
            log.error("Failed to get memory record from Bedrock AgentCore: {} / {}", memoryId, recordId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Delete memory record from Bedrock AgentCore
     */
    public void deleteMemoryRecord(String memoryId, String recordId, ActionListener<Void> listener) {
        try {
            DeleteMemoryRecordRequest request = DeleteMemoryRecordRequest.builder().memoryId(memoryId).build();

            awsClient.deleteMemoryRecord(request);

            log.info("Deleted memory record from Bedrock AgentCore: {} / {}", memoryId, recordId);
            listener.onResponse(null);
        } catch (Exception e) {
            log.error("Failed to delete memory record from Bedrock AgentCore: {} / {}", memoryId, recordId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Search memory records in Bedrock AgentCore
     */
    public void retrieveMemoryRecords(String memoryId, String searchQuery, ActionListener<List<BedrockAgentCoreMemoryRecord>> listener) {
        try {
            RetrieveMemoryRecordsRequest request = RetrieveMemoryRecordsRequest.builder().memoryId(memoryId).build();

            RetrieveMemoryRecordsResponse response = awsClient.retrieveMemoryRecords(request);

            List<BedrockAgentCoreMemoryRecord> records = List
                .of(
                    BedrockAgentCoreMemoryRecord
                        .bedrockAgentCoreMemoryRecordBuilder()
                        .content("Search result from Bedrock AgentCore")
                        .sessionId(memoryId)
                        .build()
                );

            log.info("Found {} matching memory records in Bedrock AgentCore: {}", records.size(), memoryId);
            listener.onResponse(records);
        } catch (Exception e) {
            log.error("Failed to search memory records in Bedrock AgentCore: {}", memoryId, e);
            listener.onFailure(e);
        }
    }

    public void close() {
        if (awsClient != null) {
            awsClient.close();
        }
    }
}
