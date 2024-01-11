/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.StepListener;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;

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

    public MLFlowAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
    }

    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener) {
        List<MLToolSpec> toolSpecs = mlAgent.getTools();
        StepListener<Object> firstStepListener = null;
        Tool firstTool = null;
        List<ModelTensor> flowAgentOutput = new ArrayList<>();
        Map<String, String> firstToolExecuteParams = null;
        StepListener<Object> previousStepListener = null;
        Map<String, Object> additionalInfo = new ConcurrentHashMap<>();
        if (toolSpecs == null || toolSpecs.size() == 0) {
            listener.onFailure(new IllegalArgumentException("no tool configured"));
            return;
        }

        MLMemorySpec memorySpec = mlAgent.getMemory();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String parentInteractionId = params.get(MLAgentExecutor.PARENT_INTERACTION_ID);

        for (int i = 0; i <= toolSpecs.size(); i++) {
            if (i == 0) {
                MLToolSpec toolSpec = toolSpecs.get(i);
                Tool tool = createTool(toolSpec);
                firstStepListener = new StepListener<>();
                previousStepListener = firstStepListener;
                firstTool = tool;
                firstToolExecuteParams = getToolExecuteParams(toolSpec, params);
            } else {
                MLToolSpec previousToolSpec = toolSpecs.get(i - 1);
                StepListener<Object> nextStepListener = new StepListener<>();
                int finalI = i;
                previousStepListener.whenComplete(output -> {
                    String key = previousToolSpec.getName();
                    String outputKey = previousToolSpec.getName() != null
                        ? previousToolSpec.getName() + ".output"
                        : previousToolSpec.getType() + ".output";

                    if (previousToolSpec.isIncludeOutputInAgentResponse() || finalI == toolSpecs.size()) {
                        if (output instanceof ModelTensorOutput) {
                            flowAgentOutput.addAll(((ModelTensorOutput) output).getMlModelOutputs().get(0).getMlModelTensors());
                        } else {
                            String result = output instanceof String
                                ? (String) output
                                : AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> StringUtils.toJson(output));

                            ModelTensor stepOutput = ModelTensor.builder().name(key).result(result).build();
                            flowAgentOutput.add(stepOutput);
                        }
                    }

                    String outputResponse = parseResponse(output);
                    params.put(outputKey, escapeJson(outputResponse));
                    additionalInfo.put(outputKey, outputResponse);

                    if (finalI == toolSpecs.size()) {
                        updateMemory(additionalInfo, memorySpec, memoryId, parentInteractionId);
                        listener.onResponse(flowAgentOutput);
                        return;
                    }

                    MLToolSpec toolSpec = toolSpecs.get(finalI);
                    Tool tool = createTool(toolSpec);
                    if (finalI < toolSpecs.size()) {
                        tool.run(getToolExecuteParams(toolSpec, params), nextStepListener);
                    }

                }, e -> {
                    log.error("Failed to run flow agent", e);
                    listener.onFailure(e);
                });
                previousStepListener = nextStepListener;
            }
        }
        if (toolSpecs.size() == 1) {
            firstTool.run(firstToolExecuteParams, listener);
        } else {
            firstTool.run(firstToolExecuteParams, firstStepListener);
        }
    }

    // VisibleForTesting
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
                        e -> log.error("Failed create memory from id: " + memoryId, e)
                    )
            );
    }

    // VisibleForTesting
    void updateInteraction(Map<String, Object> additionalInfo, String interactionId, ConversationIndexMemory memory) {
        memory
            .getMemoryManager()
            .updateInteraction(
                interactionId,
                Map.of(ActionConstants.ADDITIONAL_INFO_FIELD, additionalInfo),
                ActionListener.<UpdateResponse>wrap(updateResponse -> {
                    log.info("Updated additional info for interaction ID: " + interactionId);
                }, e -> { log.error("Failed to update root interaction", e); })
            );
    }

    // VisibleForTesting
    String parseResponse(Object output) throws IOException {
        if (output instanceof List && !((List) output).isEmpty() && ((List) output).get(0) instanceof ModelTensors) {
            ModelTensors tensors = (ModelTensors) ((List) output).get(0);
            return tensors.toXContent(JsonXContent.contentBuilder(), null).toString();
        } else if (output instanceof ModelTensor) {
            return ((ModelTensor) output).toXContent(JsonXContent.contentBuilder(), null).toString();
        } else if (output instanceof ModelTensorOutput) {
            return ((ModelTensorOutput) output).toXContent(JsonXContent.contentBuilder(), null).toString();
        } else {
            if (output instanceof String) {
                return (String) output;
            } else {
                return StringUtils.toJson(output);
            }
        }
    }

    // VisibleForTesting
    Tool createTool(MLToolSpec toolSpec) {
        Map<String, String> toolParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            toolParams.putAll(toolSpec.getParameters());
        }
        if (!toolFactories.containsKey(toolSpec.getType())) {
            throw new IllegalArgumentException("Tool not found: " + toolSpec.getType());
        }
        Tool tool = toolFactories.get(toolSpec.getType()).create(toolParams);
        if (toolSpec.getName() != null) {
            tool.setName(toolSpec.getName());
        }

        if (toolSpec.getDescription() != null) {
            tool.setDescription(toolSpec.getDescription());
        }
        return tool;
    }

    // VisibleForTesting
    Map<String, String> getToolExecuteParams(MLToolSpec toolSpec, Map<String, String> params) {
        Map<String, String> executeParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        for (String key : params.keySet()) {
            String toBeReplaced = null;
            if (key.startsWith(toolSpec.getType() + ".")) {
                toBeReplaced = toolSpec.getType() + ".";
            }
            if (toolSpec.getName() != null && key.startsWith(toolSpec.getName() + ".")) {
                toBeReplaced = toolSpec.getName() + ".";
            }
            if (toBeReplaced != null) {
                executeParams.put(key.replace(toBeReplaced, ""), params.get(key));
            } else {
                executeParams.put(key, params.get(key));
            }
        }

        if (executeParams.containsKey("input")) {
            String input = executeParams.get("input");
            StringSubstitutor substitutor = new StringSubstitutor(executeParams, "${parameters.", "}");
            input = substitutor.replace(input);
            executeParams.put("input", input);
        }
        return executeParams;
    }
}
