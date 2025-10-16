/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.opensearch.ml.common.agui.BaseEvent;
import org.opensearch.ml.common.agui.MessagesSnapshotEvent;
import org.opensearch.ml.common.agui.RunErrorEvent;
import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.RunStartedEvent;
import org.opensearch.ml.common.agui.TextMessageContentEvent;
import org.opensearch.ml.common.agui.TextMessageEndEvent;
import org.opensearch.ml.common.agui.TextMessageStartEvent;
import org.opensearch.ml.common.agui.ToolCallArgsEvent;
import org.opensearch.ml.common.agui.ToolCallEndEvent;
import org.opensearch.ml.common.agui.ToolCallResultEvent;
import org.opensearch.ml.common.agui.ToolCallStartEvent;

import com.google.gson.Gson;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AGUIEventCollector {

    private final List<BaseEvent> events;
    private final String threadId;
    private final String runId;
    private final Gson gson;

    public AGUIEventCollector() {
        this.events = new ArrayList<>();
        this.threadId = UUID.randomUUID().toString();
        this.runId = UUID.randomUUID().toString();
        this.gson = new Gson();
    }

    public void startRun() {
        events.add(new RunStartedEvent(threadId, runId));
        log.debug("AG-UI: Run started with thread_id={}, run_id={}", threadId, runId);
    }

    public void finishRun(Object result) {
        events.add(new RunFinishedEvent(threadId, runId, result));
        log.debug("AG-UI: Run finished with thread_id={}, run_id={}", threadId, runId);
    }

    public void errorRun(String message, String code) {
        events.add(new RunErrorEvent(message, code));
        log.debug("AG-UI: Run error with message={}, code={}", message, code);
    }

    public String startTextMessage(String role) {
        String messageId = UUID.randomUUID().toString();
        events.add(new TextMessageStartEvent(messageId, role));
        log.debug("AG-UI: Text message started with message_id={}, role={}", messageId, role);
        return messageId;
    }

    public void addTextMessageContent(String messageId, String delta) {
        events.add(new TextMessageContentEvent(messageId, delta));
        log.debug("AG-UI: Text message content added with message_id={}, delta_length={}", messageId, delta != null ? delta.length() : 0);
    }

    public void endTextMessage(String messageId) {
        events.add(new TextMessageEndEvent(messageId));
        log.debug("AG-UI: Text message ended with message_id={}", messageId);
    }

    public String startToolCall(String toolName, String parentMessageId) {
        String toolCallId = UUID.randomUUID().toString();
        events.add(new ToolCallStartEvent(toolCallId, toolName, parentMessageId));
        log.debug("AG-UI: Tool call started with tool_call_id={}, tool_name={}", toolCallId, toolName);
        return toolCallId;
    }

    public void addToolCallArgs(String toolCallId, String delta) {
        events.add(new ToolCallArgsEvent(toolCallId, delta));
        log.debug("AG-UI: Tool call args added with tool_call_id={}, delta_length={}", toolCallId, delta != null ? delta.length() : 0);
    }

    public void endToolCall(String toolCallId) {
        events.add(new ToolCallEndEvent(toolCallId));
        log.debug("AG-UI: Tool call ended with tool_call_id={}", toolCallId);
    }

    public void addToolCallResult(String toolCallId, String result) {
        String messageId = UUID.randomUUID().toString();
        events.add(new ToolCallResultEvent(messageId, toolCallId, result));
        log.debug("AG-UI: Tool call result added with tool_call_id={}, message_id={}", toolCallId, messageId);
    }

    public void addMessagesSnapshot(List<Object> messages) {
        events.add(new MessagesSnapshotEvent(messages));
        log.debug("AG-UI: Messages snapshot added with {} messages", messages.size());
    }

    public String getEventsAsJson() {
        return gson.toJson(events);
    }

    public List<BaseEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public void clear() {
        events.clear();
        log.debug("AG-UI: Event collector cleared");
    }

    public String getThreadId() {
        return threadId;
    }

    public String getRunId() {
        return runId;
    }
}
