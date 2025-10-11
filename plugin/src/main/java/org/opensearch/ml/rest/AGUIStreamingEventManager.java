/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages AG-UI streaming event state across multiple streaming chunks.
 * Ensures proper event sequence: RUN_STARTED -> TEXT_MESSAGE_START -> TEXT_MESSAGE_CONTENT -> TEXT_MESSAGE_END -> RUN_FINISHED
 */
public class AGUIStreamingEventManager {

    private static final ConcurrentHashMap<String, ConversationState> conversations = new ConcurrentHashMap<>();

    private static class ConversationState {
        final String threadId;
        final String runId;
        final String messageId;
        final AtomicBoolean runStarted = new AtomicBoolean(false);
        final AtomicBoolean messageStarted = new AtomicBoolean(false);
        final AtomicBoolean runFinished = new AtomicBoolean(false);

        ConversationState(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
            this.messageId = "msg_" + System.currentTimeMillis();
        }
    }

    public static String[] getRequiredStartEvents(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.computeIfAbsent(conversationKey, k -> new ConversationState(threadId, runId));

        // Return events needed to start the conversation properly
        if (!state.runStarted.get()) {
            state.runStarted.set(true);
            if (!state.messageStarted.get()) {
                state.messageStarted.set(true);
                return new String[] { createRunStartedEvent(threadId, runId), createTextMessageStartEvent(state.messageId, "assistant") };
            } else {
                return new String[] { createRunStartedEvent(threadId, runId) };
            }
        } else if (!state.messageStarted.get()) {
            state.messageStarted.set(true);
            return new String[] { createTextMessageStartEvent(state.messageId, "assistant") };
        }

        return new String[0]; // No startup events needed
    }

    public static String createTextMessageContentEvent(String threadId, String runId, String delta) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);
        if (state == null) {
            state = new ConversationState(threadId, runId);
            conversations.put(conversationKey, state);
        }

        return String
            .format(
                "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"%s\",\"delta\":\"%s\",\"timestamp\":%d}",
                state.messageId,
                escapeJsonString(delta),
                System.currentTimeMillis()
            );
    }

    public static String[] getRequiredEndEvents(String threadId, String runId) {
        String conversationKey = threadId + "_" + runId;
        ConversationState state = conversations.get(conversationKey);
        if (state == null) {
            return new String[] { createRunFinishedEvent(threadId, runId) };
        }

        if (!state.runFinished.get()) {
            state.runFinished.set(true);
            conversations.remove(conversationKey); // Cleanup

            return new String[] { createTextMessageEndEvent(state.messageId), createRunFinishedEvent(threadId, runId) };
        }

        return new String[0];
    }

    private static String createRunStartedEvent(String threadId, String runId) {
        return String
            .format(
                "{\"type\":\"RUN_STARTED\",\"threadId\":\"%s\",\"runId\":\"%s\",\"timestamp\":%d}",
                threadId,
                runId,
                System.currentTimeMillis()
            );
    }

    private static String createTextMessageStartEvent(String messageId, String role) {
        return String
            .format(
                "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"%s\",\"role\":\"%s\",\"timestamp\":%d}",
                messageId,
                role,
                System.currentTimeMillis()
            );
    }

    private static String createTextMessageEndEvent(String messageId) {
        return String
            .format("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"%s\",\"timestamp\":%d}", messageId, System.currentTimeMillis());
    }

    private static String createRunFinishedEvent(String threadId, String runId) {
        return String
            .format(
                "{\"type\":\"RUN_FINISHED\",\"threadId\":\"%s\",\"runId\":\"%s\",\"timestamp\":%d}",
                threadId,
                runId,
                System.currentTimeMillis()
            );
    }

    private static String escapeJsonString(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
