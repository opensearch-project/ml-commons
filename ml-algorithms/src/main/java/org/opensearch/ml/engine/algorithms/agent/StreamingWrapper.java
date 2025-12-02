/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.returnFinalResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agui.BaseEvent;
import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.ToolCallResultEvent;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
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
    private final Map<String, String> parameters;

    public StreamingWrapper(TransportChannel channel, Client client, Map<String, String> parameters) {
        this.channel = channel;
        this.client = client;
        this.parameters = parameters;
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

    public void sendBackendToolResult(String toolCallId, String toolResult, String sessionId, String parentInteractionId) {
        try {
            BaseEvent toolCallResultEvent = new ToolCallResultEvent("msg_" + System.currentTimeMillis(), toolCallId, toolResult);
            MLTaskResponse toolChunk = createStreamChunk(toolCallResultEvent.toJsonString(), sessionId, parentInteractionId, false);
            channel.sendResponseBatch(toolChunk);
        } catch (Exception e) {
            log.error("Failed to send backend tool AGUI events for toolCallId '{}': {}", toolCallId, e.getMessage());
            sendToolResponse(toolResult, sessionId, parentInteractionId);
        }
    }

    public void sendRunFinishedAndCloseStream(String sessionId, String parentInteractionId) {

        String threadId = parameters.get(AGUI_PARAM_THREAD_ID);
        String runId = parameters.get(AGUI_PARAM_RUN_ID);

        BaseEvent runFinishedEvent = new RunFinishedEvent(threadId, runId, null);
        List<ModelTensor> modelTensors = new ArrayList<>();
        Map<String, Object> dataMap = Map.of("content", runFinishedEvent.toJsonString(), "is_last", true);

        modelTensors.add(ModelTensor.builder().name("response").dataAsMap(dataMap).build());
        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(modelTensors).build()))
            .build();
        channel.sendResponseBatch(new MLTaskResponse(output));
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
