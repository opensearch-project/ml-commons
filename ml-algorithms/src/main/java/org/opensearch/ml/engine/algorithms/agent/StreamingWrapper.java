/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.returnFinalResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.agui.ToolCallResultEvent;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StreamingWrapper {
    private final TransportChannel channel;
    private boolean isStreaming;
    private Client client;

    public StreamingWrapper(TransportChannel channel, org.opensearch.transport.client.Client client) {
        this.channel = channel;
        this.client = client;
        this.isStreaming = (channel != null);
    }

    public void fixInteractionRole(List<String> interactions) {
        if (isStreaming && !interactions.isEmpty()) {
            try {
                String lastInteraction = interactions.get(interactions.size() - 1);
                Map<String, Object> messageMap = gson.fromJson(lastInteraction, Map.class);

                if (!messageMap.containsKey("role") && messageMap.containsKey("tool_calls")) {
                    messageMap.put("role", "assistant");
                    interactions.set(interactions.size() - 1, StringUtils.toJson(messageMap));
                }
            } catch (Exception e) {
                log.error("Failed to fix assistant message role after parseLLMOutput", e);
            }
        }
    }

    public ActionRequest createPredictionRequest(LLMSpec llm, Map<String, String> parameters, String tenantId) {
        return new MLPredictionTaskRequest(
            llm.getModelId(),
            RemoteInferenceMLInput
                .builder()
                .algorithm(FunctionName.REMOTE)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(parameters).build())
                .build(),
            // TODO: handle agent streaming in multi-node
            !isStreaming, // set dispatchTask to false for streaming
            null,
            tenantId
        );
    }

    public void executeRequest(ActionRequest request, ActionListener<MLTaskResponse> listener) {
        if (isStreaming) {
            ((MLPredictionTaskRequest) request).setStreamingChannel(channel);
            client.execute(MLPredictionStreamTaskAction.INSTANCE, request, listener);
            return;
        }
        client.execute(MLPredictionTaskAction.INSTANCE, request, listener);
    }

    public void sendCompletionChunk(String sessionId, String parentInteractionId) {
        if (!isStreaming) {
            return;
        }
        MLTaskResponse completionChunk = createStreamChunk("", sessionId, parentInteractionId, true);
        try {
            channel.sendResponseBatch(completionChunk);
        } catch (Exception e) {
            log.warn("Failed to send completion chunk: {}", e.getMessage());
        }
    }

    public void sendFinalResponse(
        String sessionId,
        ActionListener<Object> listener,
        String parentInteractionId,
        boolean verbose,
        List<ModelTensors> cotModelTensors,
        Map<String, Object> additionalInfo,
        String finalAnswer
    ) {
        if (isStreaming) {
            listener.onResponse("Streaming completed");
        } else {
            returnFinalResponse(sessionId, listener, parentInteractionId, verbose, cotModelTensors, additionalInfo, finalAnswer);
        }
    }

    public void sendToolResponse(String toolOutput, String sessionId, String parentInteractionId) {
        if (isStreaming) {
            try {
                MLTaskResponse toolChunk = createStreamChunk(toolOutput, sessionId, parentInteractionId, false);
                channel.sendResponseBatch(toolChunk);
            } catch (Exception e) {
                log.error("Failed to send tool response chunk", e);
            }
        }
    }

    /**
     * Send TOOL_CALL_RESULT event for backend tools.
     * Note: TOOL_CALL_START, TOOL_CALL_ARGS, and TOOL_CALL_END are already sent by BedrockStreamingHandler during LLM streaming.
     */
    public void sendBackendToolResult(String toolCallId, String toolResult, String sessionId, String parentInteractionId) {
        if (isStreaming) {
            try {
                // Generate only TOOL_CALL_RESULT event (TOOL_CALL_END already sent after args completed)
                ToolCallResultEvent resultEvent = new ToolCallResultEvent("msg_" + System.currentTimeMillis(), toolCallId, toolResult);

                // Create JSON array with single AGUI event using proper toXContent() method for correct field ordering
                String aguiEventsJson = "[" + resultEvent.toJsonString() + "]";

                log.debug("AG-UI: Sending backend tool TOOL_CALL_RESULT event for toolCallId '{}'", toolCallId);

                // Send as streaming chunk
                MLTaskResponse toolChunk = createStreamChunk(aguiEventsJson, sessionId, parentInteractionId, false);
                channel.sendResponseBatch(toolChunk);
            } catch (Exception e) {
                log.error("Failed to send backend tool AGUI events for toolCallId '{}': {}", toolCallId, e.getMessage());
                // Fallback to plain text response
                sendToolResponse(toolResult, sessionId, parentInteractionId);
            }
        }
    }

    private MLTaskResponse createStreamChunk(String toolOutput, String sessionId, String parentInteractionId, boolean isLast) {
        List<ModelTensor> tensors = Arrays
            .asList(
                ModelTensor.builder().name("response").dataAsMap(Map.of("content", toolOutput, "is_last", isLast)).build(),
                ModelTensor.builder().name("memory_id").result(sessionId).build(),
                ModelTensor.builder().name("parent_interaction_id").result(parentInteractionId).build()
            );

        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(tensors).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(modelTensors)).build();
        return new MLTaskResponse(output);
    }
}
