package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.MAX_ITERATION;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.DEFAULT_EXECUTOR_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.DEFAULT_REACT_MAX_ITERATIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.MEMORY_ID_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.PARENT_INTERACTION_ID_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.RESPONSE_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.STEP_RESULT_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.SYSTEM_PROMPT_FIELD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(SopBasedRCATool.TYPE)
public class SopBasedRCATool implements Tool {
    public static final String TYPE = "SopBasedRCATool";
    private final String executeAgentId;
    private final Client client;

    public SopBasedRCATool(Client client, String executeAgentId) {
        this.client = client;
        this.executeAgentId = executeAgentId;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        List<String> sops = List
            .of(
                "Identified Relevant Indices",
                "Analyzed Index Structure",
                "Retrieved relevant Log Entries",
                "Extracted Trace IDs",
                "Analyzed Trace Data",
                "Investigated Error Details"
            );
        Map<String, String> reactParams = new HashMap<>();
        reactParams.putAll(parameters);
        reactParams.put(SYSTEM_PROMPT_FIELD, DEFAULT_EXECUTOR_SYSTEM_PROMPT);
        reactParams.put(MAX_ITERATION, DEFAULT_REACT_MAX_ITERATIONS);
        Map<String, String> resultMap = new HashMap<>();
        executeStep(reactParams, sops, 0, listener, resultMap);
    }

    private <T> void executeStep(
        Map<String, String> parameters,
        List<String> sops,
        int currentStep,
        ActionListener<T> listener,
        Map<String, String> resultMap
    ) {
        if (currentStep == sops.size()) {
            listener.onResponse((T) resultMap);
        }
        parameters.put("target", sops.get(currentStep));
        parameters.put("previous_Result", gson.toJson(resultMap));
        AgentMLInput agentInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(executeAgentId)
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(parameters).build())
            .build();

        MLExecuteTaskRequest executeRequest = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);
        client.execute(MLExecuteTaskAction.INSTANCE, executeRequest, ActionListener.wrap(executeResponse -> {
            ModelTensorOutput reactResult = (ModelTensorOutput) executeResponse.getOutput();

            // Navigate through the structure to get the response
            Map<String, String> results = new HashMap<>();

            // Process tensors in a single stream
            reactResult.getMlModelOutputs().stream().flatMap(output -> output.getMlModelTensors().stream()).forEach(tensor -> {
                switch (tensor.getName()) {
                    case MEMORY_ID_FIELD:
                        results.put(MEMORY_ID_FIELD, tensor.getResult());
                        break;
                    case PARENT_INTERACTION_ID_FIELD:
                        results.put(PARENT_INTERACTION_ID_FIELD, tensor.getResult());
                        break;
                    default:
                        Map<String, ?> dataMap = tensor.getDataAsMap();
                        if (dataMap != null && dataMap.containsKey(RESPONSE_FIELD)) {
                            results.put(STEP_RESULT_FIELD, (String) dataMap.get(RESPONSE_FIELD));
                        }
                }
            });
            resultMap.put(sops.get(currentStep), results.get(STEP_RESULT_FIELD));
            executeStep(parameters, sops, currentStep + 1, listener, resultMap);
        }, e -> {

        }));
    }

    @Override
    public void setInputParser(Parser<?, ?> parser) {
        Tool.super.setInputParser(parser);
    }

    @Override
    public void setOutputParser(Parser<?, ?> parser) {
        Tool.super.setOutputParser(parser);
    }

    @Override
    public String getType() {
        return "";
    }

    @Override
    public String getVersion() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public void setAttributes(Map<String, Object> attributes) {

    }

    @Override
    public void setDescription(String description) {

    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return false;
    }

    @Override
    public boolean end(String input, Map<String, String> toolParameters) {
        return Tool.super.end(input, toolParameters);
    }

    @Override
    public boolean useOriginalInput() {
        return Tool.super.useOriginalInput();
    }
}
