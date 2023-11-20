/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.StepListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
@Data
@NoArgsConstructor
public class MLFlowAgentRunner {

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;

    public MLFlowAgentRunner(Client client, Settings settings, ClusterService clusterService, NamedXContentRegistry xContentRegistry, Map<String, Tool.Factory> toolFactories, Map<String, Memory.Factory> memoryFactoryMap) {
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
        Map<String, String> firstToolExecuteParams = null;
        StepListener<Object> lastStepListener = null;
        if (toolSpecs.size() == 0) {
            listener.onFailure(new IllegalArgumentException("no tool configured"));
            return;
        }
        for (int i = 0 ;i<toolSpecs.size(); i++) {
            MLToolSpec toolSpec = toolSpecs.get(i);
            Tool tool = createTool(toolSpec);
            Map<String, String> map = new HashMap<>();
            map.put("index", "employee");
            map.put("question", "How many employee with the age greater than 56?");
            StepListener<Object> nextStepListener2 = new StepListener<>();
            tool.run(map, nextStepListener2);

            if (i == 0) {
                firstStepListener = new StepListener();
                lastStepListener = firstStepListener;
                firstTool = tool;
                firstToolExecuteParams = getToolExecuteParams(toolSpec, params);
            } else {
                MLToolSpec lastToolSpec = toolSpecs.get(i - 1);
                StepListener<Object> nextStepListener = new StepListener<>();
                int finalI = i;
                lastStepListener.whenComplete(output -> {
                    String outputKey = lastToolSpec.getName() + ".output";
                    if (lastToolSpec.getName() !=  null) {
                        outputKey = lastToolSpec.getName() + ".output";
                    }
                    if (output instanceof List && !((List) output).isEmpty() && ((List) output).get(0) instanceof ModelTensors) {
                        ModelTensors tensors = (ModelTensors) ((List) output).get(0);
                        Object response = tensors.getMlModelTensors().get(0).getDataAsMap().get("response");
                        params.put(outputKey, response + "");
                    } else if (output instanceof ModelTensor) {
                        params.put(outputKey, escapeJson(toJson(((ModelTensor)output).getDataAsMap())));
                    } else {
                        if (output instanceof String) {
                            params.put(outputKey, (String)output);
                        } else {
                            params.put(outputKey, escapeJson(toJson(output.toString())));
                        }
                    }
                    if (finalI < toolSpecs.size() - 1) {
                        tool.run(getToolExecuteParams(toolSpec, params), nextStepListener);
                    } else {
                        tool.run(getToolExecuteParams(toolSpec, params), listener);
                    }
                }, e -> {
                    log.error("Failed to run flow agent", e);
                    listener.onFailure(e);
                });
                if (i < toolSpecs.size() - 1) {
                    lastStepListener  = nextStepListener;
                }
            }
        }
        if (toolSpecs.size() == 1) {
            firstTool.run(firstToolExecuteParams, listener);
        } else {
            firstTool.run(firstToolExecuteParams, firstStepListener);
        }
    }

    private String toJson(Object value) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                if (value instanceof String) {
                    return (String)value;
                } else {
                    return gson.toJson(value);
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e);
        }
    }

    private Tool createTool(MLToolSpec toolSpec) {
        Map<String, String> toolParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            toolParams.putAll(toolSpec.getParameters());
        }
        if (!toolFactories.containsKey(toolSpec.getName())) {
            throw new IllegalArgumentException("Tool not found: " + toolSpec.getName());
        }
        Tool tool = toolFactories.get(toolSpec.getName()).create(toolParams);
        tool.setName(toolSpec.getName());

        if (toolSpec.getDescription() != null) {
            tool.setDescription(toolSpec.getDescription());
        }
        return tool;
    }

    private Map<String, String> getToolExecuteParams(MLToolSpec toolSpec, Map<String, String> params) {
        Map<String, String> executeParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        for (String key : params.keySet()) {
            String toBeReplaced = null;
            if (key.startsWith(toolSpec.getName() + ".")) {
                toBeReplaced = toolSpec.getName()+".";
            }
            if (toolSpec.getName() != null && key.startsWith(toolSpec.getName() + ".")) {
                toBeReplaced = toolSpec.getName()+".";
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
