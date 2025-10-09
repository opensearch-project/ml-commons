/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLAGUIAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;
    private final SdkClient sdkClient;
    private final Encryptor encryptor;

    public MLAGUIAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel) {
        log.info("Starting AG-UI agent execution for agent: {}", mlAgent.getName());

        // Create event collector to accumulate AG-UI events
        AGUIEventCollector eventCollector = new AGUIEventCollector();
        eventCollector.startRun();

        try {
            String underlyingAgentType = params.getOrDefault("underlying_agent_type", "CONVERSATIONAL");
            MLAgentRunner underlyingRunner = createUnderlyingRunner(underlyingAgentType);

            String messageId = eventCollector.startTextMessage("assistant");
            ActionListener<Object> aguiListener = ActionListener.wrap(result -> {
                try {
                    processAgentResult(result, eventCollector, messageId);
                    eventCollector.endTextMessage(messageId);
                    eventCollector.finishRun(result);

                    String eventsJson = eventCollector.getEventsAsJson();
                    log.debug("AG-UI events generated: {}", eventsJson);

                    listener.onResponse(eventsJson);
                } catch (Exception e) {
                    log.error("Error processing AG-UI events", e);
                    listener.onFailure(e);
                }
            }, error -> {
                log.error("Error in underlying agent execution", error);
                eventCollector.finishRun(null);
                listener.onFailure(error);
            });

            underlyingRunner.run(mlAgent, params, aguiListener, channel);

        } catch (Exception e) {
            log.error("Error starting AG-UI agent execution", e);
            eventCollector.finishRun(null);
            listener.onFailure(e);
        }
    }

    private MLAgentRunner createUnderlyingRunner(String agentType) {
        MLAgentType type = MLAgentType.from(agentType.toUpperCase());

        switch (type) {
            case CONVERSATIONAL:
                return new MLChatAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case FLOW:
                return new MLFlowAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case CONVERSATIONAL_FLOW:
                return new MLConversationalFlowAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case PLAN_EXECUTE_AND_REFLECT:
                return new MLPlanExecuteAndReflectAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            default:
                throw new IllegalArgumentException("Unsupported underlying agent type: " + agentType);
        }
    }

    private void processAgentResult(Object result, AGUIEventCollector eventCollector, String messageId) {
        if (result instanceof ModelTensorOutput) {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) result;
            // Extract tool calls and text responses from the tensor output
            processTensorOutput(tensorOutput, eventCollector);
        } else if (result instanceof String) {
            log.debug("AG-UI: Processing string result: {}", result);
        } else {
            log.debug("AG-UI: Processing generic result of type: {}", result != null ? result.getClass() : "null");
        }
        List<Object> messages = new ArrayList<>();
        String responseText = extractResponseText(result);
        messages.add(Map.of("id", messageId, "role", "assistant", "content", responseText));
        eventCollector.addMessagesSnapshot(messages);
    }

    private String extractResponseText(Object result) {
        if (result instanceof ModelTensorOutput) {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) result;
            if (tensorOutput.getMlModelOutputs() != null) {
                for (var modelTensors : tensorOutput.getMlModelOutputs()) {
                    if (modelTensors.getMlModelTensors() != null) {
                        for (var tensor : modelTensors.getMlModelTensors()) {
                            if ("response".equals(tensor.getName())) {
                                if (tensor.getDataAsMap() != null && tensor.getDataAsMap().containsKey("response")) {
                                    Object responseValue = tensor.getDataAsMap().get("response");
                                    return responseValue != null ? responseValue.toString() : "";
                                }
                                if (tensor.getResult() != null) {
                                    return tensor.getResult();
                                }
                            }
                        }
                    }
                }
            }
        } else if (result instanceof String) {
            return (String) result;
        }

        return result != null ? result.toString() : "";
    }

    private void processTensorOutput(ModelTensorOutput tensorOutput, AGUIEventCollector eventCollector) {
        if (tensorOutput.getMlModelOutputs() != null) {
            tensorOutput.getMlModelOutputs().forEach(modelTensors -> {
                if (modelTensors.getMlModelTensors() != null) {
                    modelTensors.getMlModelTensors().forEach(tensor -> { processModelTensor(tensor, eventCollector); });
                }
            });
        }
    }

    private void processModelTensor(ModelTensor tensor, AGUIEventCollector eventCollector) {
        String tensorName = tensor.getName();

        if ("response".equals(tensorName) && tensor.getResult() != null) {
            String result = tensor.getResult();
            log.debug("AG-UI: Processing tensor result: {}", result);

            if (result.contains("tool_call") || result.contains("function_call")) {
                String toolCallId = eventCollector.startToolCall("mock_tool", null);
                eventCollector.endToolCall(toolCallId);
                eventCollector.addToolCallResult(toolCallId, "Mock tool result");
            }
        }
    }
}
