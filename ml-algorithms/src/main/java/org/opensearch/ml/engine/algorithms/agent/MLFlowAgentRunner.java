/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.tools.ToolUtils.TOOL_OUTPUT_FILTERS_FIELD;
import static org.opensearch.ml.engine.tools.ToolUtils.filterToolOutput;
import static org.opensearch.ml.engine.tools.ToolUtils.getToolName;
import static org.opensearch.ml.engine.tools.ToolUtils.parseResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.action.StepListener;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.algorithms.agent.tracing.MLAgentTracer;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.tools.ToolUtils;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.transport.client.Client;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
public class MLFlowAgentRunner implements MLAgentRunner {

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;

    public MLFlowAgentRunner(
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

    @SuppressWarnings("removal")
    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener) {
        Span agentTaskSpan = MLAgentTracer.getInstance().startFlowAgentTaskSpan(mlAgent.getName(), params.get(MLAgentExecutor.QUESTION));
        List<MLToolSpec> toolSpecs = getMlToolSpecs(mlAgent, params);
        StepListener<Object> firstStepListener = null;
        Tool firstTool = null;
        List<ModelTensor> flowAgentOutput = new ArrayList<>();
        Map<String, String> firstToolExecuteParams = null;
        StepListener<Object> previousStepListener = null;
        Map<String, Object> additionalInfo = new ConcurrentHashMap<>();
        if (toolSpecs == null || toolSpecs.isEmpty()) {
            MLAgentTracer.handleSpanError(agentTaskSpan, "no tool configured", new IllegalArgumentException("no tool configured"));
            listener.onFailure(new IllegalArgumentException("no tool configured"));
            return;
        }

        MLMemorySpec memorySpec = mlAgent.getMemory();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String parentInteractionId = params.get(MLAgentExecutor.PARENT_INTERACTION_ID);

        for (int i = 0; i <= toolSpecs.size(); i++) {
            if (i == 0) {
                MLToolSpec toolSpec = toolSpecs.get(i);
                firstToolExecuteParams = ToolUtils.buildToolParameters(params, toolSpec, mlAgent.getTenantId());
                Tool tool = ToolUtils.createTool(toolFactories, firstToolExecuteParams, toolSpec);
                firstStepListener = new StepListener<>();
                previousStepListener = firstStepListener;
                firstTool = tool;
            } else {
                MLToolSpec previousToolSpec = toolSpecs.get(i - 1);
                StepListener<Object> nextStepListener = new StepListener<>();
                int finalI = i;
                previousStepListener.whenComplete(output -> {
                    String toolName = getToolName(previousToolSpec);
                    String outputKey = toolName + ".output";
                    Map<String, String> toolParameters = ToolUtils.buildToolParameters(params, previousToolSpec, mlAgent.getTenantId());
                    String filteredOutput = parseResponse(filterToolOutput(toolParameters, output));
                    params.put(outputKey, StringUtils.prepareJsonValue(filteredOutput));
                    if (previousToolSpec.isIncludeOutputInAgentResponse() || finalI == toolSpecs.size()) {
                        if (toolParameters.containsKey(TOOL_OUTPUT_FILTERS_FIELD)) {
                            flowAgentOutput.add(ModelTensor.builder().name(outputKey).result(filteredOutput).build());
                        } else if (output instanceof ModelTensorOutput) {
                            flowAgentOutput.addAll(((ModelTensorOutput) output).getMlModelOutputs().get(0).getMlModelTensors());
                        } else {
                            ModelTensor stepOutput = ModelTensor.builder().name(toolName).result(StringUtils.toJson(output)).build();
                            flowAgentOutput.add(stepOutput);
                        }

                        additionalInfo.put(outputKey, filteredOutput);
                    }

                    if (finalI == toolSpecs.size()) {
                        agentTaskSpan.addAttribute(MLAgentTracer.ATTR_RESULT, filteredOutput);
                        if (memoryId == null || parentInteractionId == null || memorySpec == null || memorySpec.getType() == null) {
                            MLAgentTracer.getInstance().endSpan(agentTaskSpan);
                            listener.onResponse(flowAgentOutput);
                        } else {
                            ActionListener<UpdateResponse> updateListener = ActionListener.wrap(updateResponse -> {
                                log.info("Updated additional info for interaction ID: {} in the flow agent.", updateResponse.getId());
                                MLAgentTracer.getInstance().endSpan(agentTaskSpan);
                                listener.onResponse(flowAgentOutput);
                            }, e -> {
                                log.error("Failed to update root interaction", e);
                                MLAgentTracer.getInstance().endSpan(agentTaskSpan);
                                listener.onResponse(flowAgentOutput);
                            });
                            updateMemoryWithListener(additionalInfo, memorySpec, memoryId, parentInteractionId, updateListener);
                        }
                        return;
                    }

                    MLToolSpec toolSpec = toolSpecs.get(finalI);
                    Map<String, String> executeParams = ToolUtils.buildToolParameters(params, toolSpec, mlAgent.getTenantId());
                    Tool tool = ToolUtils.createTool(toolFactories, executeParams, toolSpec);
                    if (finalI < toolSpecs.size()) {

                        Span toolCallSpan = MLAgentTracer
                            .getInstance()
                            .startConversationalToolCallSpan(
                                params.get(MLAgentExecutor.QUESTION),
                                finalI + 1,
                                toolSpec.getType(),
                                toolSpec.getDescription() != null ? toolSpec.getDescription() : toolSpec.getName(),
                                agentTaskSpan
                            );

                        tool.run(executeParams, ActionListener.wrap(toolOutput -> {
                            MLAgentTracer.updateSpanWithTool(toolCallSpan, toolOutput, params.get(MLAgentExecutor.QUESTION));
                            nextStepListener.onResponse(toolOutput);
                        }, e -> {
                            MLAgentTracer.handleSpanError(toolCallSpan, "Failed to run next step", e);
                            nextStepListener.onFailure(e);
                        }));
                    }

                }, e -> {
                    log.error("Failed to run flow agent", e);
                    MLAgentTracer.handleSpanError(agentTaskSpan, "Failed to run flow agent", e);
                    listener.onFailure(e);
                });
                previousStepListener = nextStepListener;
            }
        }

        MLToolSpec toolSpec = toolSpecs.getFirst();
        if (toolSpec == null) {
            throw new IllegalArgumentException("Tool spec cannot be null. Check if toolSpecs list contains null elements.");
        }
        Span toolCallSpan = MLAgentTracer
            .getInstance()
            .startConversationalToolCallSpan(
                params.get(MLAgentExecutor.QUESTION),
                1,
                toolSpec.getType(),
                toolSpec.getDescription() != null ? toolSpec.getDescription() : toolSpec.getName(),
                agentTaskSpan
            );

        if (toolSpecs.size() == 1) {
            firstTool.run(firstToolExecuteParams, ActionListener.wrap(firstToolOutput -> {
                MLAgentTracer.updateSpanWithTool(toolCallSpan, firstToolOutput, params.get(MLAgentExecutor.QUESTION));
                MLAgentTracer.getInstance().endSpan(agentTaskSpan);
                listener.onResponse(firstToolOutput);
            }, e -> {
                MLAgentTracer.handleSpanError(toolCallSpan, "Failed to run first tool", e);
                MLAgentTracer.handleSpanError(agentTaskSpan, "Failed to run first tool", e);
                listener.onFailure(e);
            }));
        } else {
            final StepListener<Object> finalFirstStepListener = firstStepListener;
            firstTool.run(firstToolExecuteParams, ActionListener.wrap(firstToolOutput -> {
                MLAgentTracer.updateSpanWithTool(toolCallSpan, firstToolOutput, params.get(MLAgentExecutor.QUESTION));
                finalFirstStepListener.onResponse(firstToolOutput);
            }, e -> {
                MLAgentTracer.handleSpanError(toolCallSpan, "Failed to run first tool", e);
                MLAgentTracer.handleSpanError(agentTaskSpan, "Failed to run first tool", e);
                finalFirstStepListener.onFailure(e);
            }));
        }
    }

    @VisibleForTesting
    void updateMemory(Map<String, Object> additionalInfo, MLMemorySpec memorySpec, String memoryId, String interactionId) {
        if (memoryId == null || interactionId == null || memorySpec == null || memorySpec.getType() == null) {
            return;
        }
        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap
            .get(memorySpec.getType());
        conversationIndexMemoryFactory
            .create(
                memoryId,
                ActionListener
                    .wrap(
                        memory -> updateInteraction(additionalInfo, interactionId, memory),
                        e -> log.error("Failed create memory from id: {}", memoryId, e)
                    )
            );
    }

    @VisibleForTesting
    void updateMemoryWithListener(
        Map<String, Object> additionalInfo,
        MLMemorySpec memorySpec,
        String memoryId,
        String interactionId,
        ActionListener listener
    ) {
        if (memoryId == null || interactionId == null || memorySpec == null || memorySpec.getType() == null) {
            return;
        }
        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap
            .get(memorySpec.getType());
        conversationIndexMemoryFactory
            .create(
                memoryId,
                ActionListener
                    .wrap(
                        memory -> updateInteractionWithListener(additionalInfo, interactionId, memory, listener),
                        e -> log.error("Failed create memory from id: {}", memoryId, e)
                    )
            );
    }

    @VisibleForTesting
    void updateInteraction(Map<String, Object> additionalInfo, String interactionId, ConversationIndexMemory memory) {
        memory
            .getMemoryManager()
            .updateInteraction(
                interactionId,
                ImmutableMap.of(ActionConstants.ADDITIONAL_INFO_FIELD, additionalInfo),
                ActionListener.<UpdateResponse>wrap(updateResponse -> {
                    log.info("Updated additional info for interaction ID: {}", interactionId);
                }, e -> log.error("Failed to update root interaction", e))
            );
    }

    @VisibleForTesting
    void updateInteractionWithListener(
        Map<String, Object> additionalInfo,
        String interactionId,
        ConversationIndexMemory memory,
        ActionListener listener
    ) {
        memory
            .getMemoryManager()
            .updateInteraction(interactionId, ImmutableMap.of(ActionConstants.ADDITIONAL_INFO_FIELD, additionalInfo), listener);
    }

}
