/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agui.BaseEvent;
import org.opensearch.ml.common.agui.RunErrorEvent;
import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.RunStartedEvent;
import org.opensearch.ml.common.agui.TextMessageContentEvent;
import org.opensearch.ml.common.agui.TextMessageEndEvent;
import org.opensearch.ml.common.agui.TextMessageStartEvent;
import org.opensearch.ml.common.agui.ToolCallEndEvent;
import org.opensearch.ml.common.agui.ToolCallResultEvent;
import org.opensearch.ml.common.agui.ToolCallStartEvent;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.transport.TransportChannel;

import lombok.extern.log4j.Log4j2;

/**
 * Streaming event emitter for AG-UI protocol compliance.
 * Emits individual events as SSE chunks instead of batching them.
 */
@Log4j2
public class AGUIStreamingEventEmitter {

    private final TransportChannel channel;
    private final String threadId;
    private final String runId;
    private String currentMessageId;

    public AGUIStreamingEventEmitter(TransportChannel channel, String threadId, String runId) {
        this.channel = channel;
        this.threadId = threadId != null ? threadId : UUID.randomUUID().toString();
        this.runId = runId != null ? runId : UUID.randomUUID().toString();
    }

    public void emitRunStarted() {
        RunStartedEvent event = new RunStartedEvent(threadId, runId);
        emitEvent(event);
    }

    public void emitRunFinished(Object result) {
        RunFinishedEvent event = new RunFinishedEvent(threadId, runId, result);
        emitEvent(event);
    }

    public void emitRunError(String message, String code) {
        RunErrorEvent event = new RunErrorEvent(message, code);
        emitEvent(event);
    }

    public String emitTextMessageStart(String role) {
        currentMessageId = UUID.randomUUID().toString();
        TextMessageStartEvent event = new TextMessageStartEvent(currentMessageId, role != null ? role : "assistant");
        emitEvent(event);
        return currentMessageId;
    }

    public void emitTextMessageContent(String messageId, String delta) {
        TextMessageContentEvent event = new TextMessageContentEvent(messageId, delta);
        emitEvent(event);
    }

    public void emitTextMessageEnd(String messageId) {
        TextMessageEndEvent event = new TextMessageEndEvent(messageId);
        emitEvent(event);
    }

    public String emitToolCallStart(String toolName, String parentMessageId) {
        String toolCallId = UUID.randomUUID().toString();
        ToolCallStartEvent event = new ToolCallStartEvent(toolCallId, toolName, parentMessageId);
        emitEvent(event);
        return toolCallId;
    }

    public void emitToolCallEnd(String toolCallId) {
        ToolCallEndEvent event = new ToolCallEndEvent(toolCallId);
        emitEvent(event);
    }

    public void emitToolCallResult(String toolCallId, String content) {
        String messageId = UUID.randomUUID().toString();
        ToolCallResultEvent event = new ToolCallResultEvent(messageId, toolCallId, content);
        emitEvent(event);
    }

    private void emitEvent(BaseEvent event) {
        if (channel == null) {
            log.warn("No transport channel available for AG-UI event emission");
            return;
        }

        try {
            // Convert event to JSON
            XContentBuilder builder = XContentFactory.jsonBuilder();
            event.toXContent(builder, ToXContent.EMPTY_PARAMS);
            String eventJson = builder.toString();

            // Create MLTaskResponse with AG-UI event as tensor content
            ModelTensor eventTensor = ModelTensor.builder().name("agui_event").result(eventJson).build();

            ModelTensors tensors = ModelTensors.builder().mlModelTensors(Arrays.asList(eventTensor)).build();

            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

            MLTaskResponse response = new MLTaskResponse(output);
            channel.sendResponseBatch(response);

            log.debug("AG-UI: Emitted event type={}, threadId={}, runId={}", event.getType(), threadId, runId);

        } catch (Exception e) {
            log.error("Failed to emit AG-UI event: {}", event.getType(), e);
        }
    }

    public String getThreadId() {
        return threadId;
    }

    public String getRunId() {
        return runId;
    }

    public String getCurrentMessageId() {
        return currentMessageId;
    }
}
