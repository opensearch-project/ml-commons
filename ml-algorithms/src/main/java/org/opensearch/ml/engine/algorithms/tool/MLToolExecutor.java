/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.tool.ToolMLInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
@Function(FunctionName.TOOL)
public class MLToolExecutor implements Executable {

    private Client client;
    private SdkClient sdkClient;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;

    public MLToolExecutor(
        Client client,
        SdkClient sdkClient,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories
    ) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener) {
        if (!(input instanceof ToolMLInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        ToolMLInput toolMLInput = (ToolMLInput) input;
        String toolName = toolMLInput.getToolName();

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) toolMLInput.getInputDataset();
        if (inputDataSet == null || inputDataSet.getParameters() == null) {
            throw new IllegalArgumentException("Tool input data can not be empty.");
        }
        Map<String, String> parameters = inputDataSet.getParameters();

        Tool.Factory toolFactory = toolFactories.get(toolName);
        if (toolFactory == null) {
            listener.onFailure(new IllegalArgumentException("Tool not found: " + toolName));
            return;
        }

        try {
            Tool tool = toolFactory.create(new HashMap<>(parameters));
            if (!tool.validate(parameters)) {
                listener.onFailure(new IllegalArgumentException("Invalid parameters for tool: " + toolName));
                return;
            }

            tool.run(parameters, ActionListener.wrap(result -> {
                List<ModelTensor> modelTensors = new ArrayList<>();
                processOutput(result, modelTensors);
                ModelTensors tensors = ModelTensors.builder().mlModelTensors(modelTensors).build();
                listener.onResponse(new ModelTensorOutput(List.of(tensors)));
            }, error -> {
                log.error("Failed to execute tool: " + toolName, error);
                listener.onFailure(error);
            }));
        } catch (Exception e) {
            log.error("Failed to execute tool: " + toolName, e);
            listener.onFailure(e);
        }
    }

    private void processOutput(Object output, List<ModelTensor> modelTensors) {
        if (output instanceof ModelTensorOutput) {
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) output;
            modelTensorOutput.getMlModelOutputs().forEach(outs -> modelTensors.addAll(outs.getMlModelTensors()));
        } else if (output instanceof ModelTensor) {
            modelTensors.add((ModelTensor) output);
        } else if (output instanceof List) {
            List<?> list = (List<?>) output;
            if (!list.isEmpty()) {
                if (list.get(0) instanceof ModelTensor) {
                    modelTensors.addAll((List<ModelTensor>) list);
                } else if (list.get(0) instanceof ModelTensors) {
                    ((List<ModelTensors>) list).forEach(outs -> modelTensors.addAll(outs.getMlModelTensors()));
                } else {
                    String result = StringUtils.toJson(output);
                    modelTensors.add(ModelTensor.builder().name("response").result(result).build());
                }
            }
        } else {
            String result = output instanceof String ? (String) output : StringUtils.toJson(output);
            modelTensors.add(ModelTensor.builder().name("response").result(result).build());
        }
    }
}
