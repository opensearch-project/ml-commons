/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.RunStartedEvent;
import org.opensearch.ml.common.agui.TextMessageContentEvent;
import org.opensearch.ml.common.agui.TextMessageEndEvent;
import org.opensearch.ml.common.agui.TextMessageStartEvent;

/**
 * Manages AG-UI streaming event state across multiple streaming chunks.
 * 
 * Event Lifecycle:
 * 1. RUN_STARTED - Emitted immediately when request is acknowledged
 * 2. TEXT_MESSAGE_START - Emitted only when text content will be streamed (optional)
 * 3. TEXT_MESSAGE_CONTENT - Streamed text chunks (optional, only if TEXT_MESSAGE_START was sent)
 * 4. TEXT_MESSAGE_END - Ends text message (optional, only if TEXT_MESSAGE_START was sent)
 * 5. TOOL_CALL_* events - For tool executions (optional)
 * 6. RUN_FINISHED - Always emitted at the end
 */
public class AGUIStreamingEventManager {

    private static final ConcurrentHashMap<String, ConversationState> conversations = new ConcurrentHashMap<>();

    private static class ConversationState {
        final String threadId;
        final String runId;
        final String messageId;
        final AtomicBoolean runStarted = new AtomicBoolean(false);
        final AtomicBoolean messageStarted = new AtomicBoolean(false);
        final AtomicBoolean messageEnded = new AtomicBoolean(false);
        final AtomicBoolean runFinished = new AtomicBoolean(false);

        ConversationState(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
            this.messageId = "msg_" + System.currentTimeMillis();
        }
    }

    /**
     * Gets the RUN_STARTED event if not already sent.
     * This should be called immediately when the request is acknowledged.
     *
     * @param threadId Thread identifier
     * @param runId Run identifier
     * @return RUN_STARTED event JSON string, or null if already sent
     */
    public static String getRunStartedEvent(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;

        ConversationState state = conversations.computeIfAbsent(conversationKey, k -> new ConversationState(threadId, runId));

        if (!state.runStarted.get()) {
            state.runStarted.set(true);
            return new RunStartedEvent(threadId, runId).toJsonString();
        }

        return null;
    }

    /**
     * Gets the TEXT_MESSAGE_START event if not already sent.
     * This should only be called when text content will be streamed.
     *
     * @param threadId Thread identifier
     * @param runId Run identifier
     * @return TEXT_MESSAGE_START event JSON string, or null if already sent
     */
    public static String getTextMessageStartEvent(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);

        if (state == null) {
            state = new ConversationState(threadId, runId);
            conversations.put(conversationKey, state);
        }

        if (!state.messageStarted.get()) {
            state.messageStarted.set(true);
            return new TextMessageStartEvent(state.messageId, "assistant").toJsonString();
        }

        return null;
    }

    /**
     * Creates a TEXT_MESSAGE_CONTENT event for streaming text chunks.
     * Should only be called after TEXT_MESSAGE_START has been sent.
     * Note: This method assumes TEXT_MESSAGE_START was already sent and marks the message as started.
     *
     * @param threadId Thread identifier
     * @param runId Run identifier
     * @param delta Text content chunk
     * @return TEXT_MESSAGE_CONTENT event JSON string
     */
    public static String createTextMessageContentEvent(String threadId, String runId, String delta) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);
        if (state == null) {
            state = new ConversationState(threadId, runId);
            conversations.put(conversationKey, state);
        }

        // Mark message as started since we're sending content
        // This ensures TEXT_MESSAGE_END will be sent later
        state.messageStarted.set(true);

        return new TextMessageContentEvent(state.messageId, delta).toJsonString();
    }

    /**
     * Gets the TEXT_MESSAGE_END event if not already sent.
     * Should only be called if TEXT_MESSAGE_START was sent.
     *
     * @param threadId Thread identifier
     * @param runId Run identifier
     * @return TEXT_MESSAGE_END event JSON string, or null if already sent or message not started
     */
    public static String getTextMessageEndEvent(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);

        if (state != null && state.messageStarted.get() && !state.messageEnded.get()) {
            state.messageEnded.set(true);
            return new TextMessageEndEvent(state.messageId).toJsonString();
        }

        return null;
    }

    /**
     * Gets the RUN_FINISHED event if not already sent.
     * This should always be called at the end of the request.
     *
     * @param threadId Thread identifier
     * @param runId Run identifier
     * @return RUN_FINISHED event JSON string, or null if already sent
     */
    public static String getRunFinishedEvent(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);

        if (state == null) {
            // No state exists, create a simple RUN_FINISHED event
            return new RunFinishedEvent(threadId, runId, null).toJsonString();
        }

        if (!state.runFinished.get()) {
            state.runFinished.set(true);
            conversations.remove(conversationKey); // Cleanup
            return new RunFinishedEvent(threadId, runId, null).toJsonString();
        }

        return null;
    }
}
