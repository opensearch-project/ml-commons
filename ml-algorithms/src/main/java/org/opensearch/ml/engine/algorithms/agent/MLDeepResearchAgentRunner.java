package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ActionConstants.AI_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.addToolsToFunctionCalling;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getToolNames;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.saveTraceData;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_PLANNER_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_PLANNER_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_PLANNER_WITH_HISTORY_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_RESPONSE_FORMAT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_REVALUATION_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEEP_RESEARCH_REVAL_PROMPT_TEMPLATE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import joptsimple.internal.Strings;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLDeepResearchAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;

    // defaults
    private static final String DEFAULT_DEEP_RESEARCH_SYSTEM_PROMPT = "Always respond in JSON format.";
    private static final String DEFAULT_REACT_SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final String DEFAULT_NO_ESCAPE_PARAMS = "tool_configs,_tools";

    // fields
    public static final String PROMPT_FIELD = "prompt";
    public static final String USER_PROMPT_FIELD = "user_prompt";
    public static final String STEPS_FIELD = "steps";
    public static final String COMPLETED_STEPS_FIELD = "completed_steps";
    public static final String PLANNER_PROMPT_FIELD = "planner_prompt";
    public static final String REVAL_PROMPT_FIELD = "reval_prompt";
    public static final String DEEP_RESEARCH_RESPONSE_FORMAT_FIELD = "deep_research_response_format";
    public static final String PROMPT_TEMPLATE_FIELD = "prompt_template";
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    public static final String QUESTION_FIELD = "question";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String TENANT_ID_FIELD = "tenant_id";
    public static final String RESULT_FIELD = "result";
    public static final String RESPONSE_FIELD = "response";
    public static final String STEP_RESULT_FIELD = "step_result";
    public static final String REACT_AGENT_ID_FIELD = "reAct_agent_id";
    public static final String REACT_AGENT_MEMORY_ID_FIELD = "reAct_agent_memory_id";
    public static final String NO_ESCAPE_PARAMS_FIELD = "no_escape_params";
    public static final String DEFAULT_PROMPT_TOOLS_FIELD = "tools_prompt";

    public MLDeepResearchAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry registry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = registry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
    }

    private void setupPromptParameters(Map<String, String> params) {
        // populated depending on whether LLM is asked to plan or re-evaluate
        // removed here, so that error is thrown in case this field is not populated
        params.remove(PROMPT_FIELD);

        String userPrompt = params.get(QUESTION_FIELD);
        params.put(USER_PROMPT_FIELD, userPrompt);

        String userSystemPrompt = params.getOrDefault(SYSTEM_PROMPT_FIELD, "");
        params.put(SYSTEM_PROMPT_FIELD, userSystemPrompt + DEFAULT_DEEP_RESEARCH_SYSTEM_PROMPT);

        params.put(PLANNER_PROMPT_FIELD, DEEP_RESEARCH_PLANNER_PROMPT);
        params.put(REVAL_PROMPT_FIELD, DEEP_RESEARCH_REVALUATION_PROMPT);
        params.put(DEEP_RESEARCH_RESPONSE_FORMAT_FIELD, DEEP_RESEARCH_RESPONSE_FORMAT);

        // empty by default, will be populated if model doesn't support function calling
        params.put(DEFAULT_PROMPT_TOOLS_FIELD, "");
    }

    private void usePlannerPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, DEEP_RESEARCH_PLANNER_PROMPT_TEMPLATE);
        populatePrompt(params);
    }

    private void useRevalPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, DEEP_RESEARCH_REVAL_PROMPT_TEMPLATE);
        populatePrompt(params);
    }

    private void usePlannerWithHistoryPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, DEEP_RESEARCH_PLANNER_WITH_HISTORY_PROMPT_TEMPLATE);
        populatePrompt(params);
    }

    private void populatePrompt(Map<String, String> allParams) {
        String promptTemplate = allParams.get(PROMPT_TEMPLATE_FIELD);
        StringSubstitutor promptSubstitutor = new StringSubstitutor(allParams, "${parameters.", "}");
        String prompt = promptSubstitutor.replace(promptTemplate);
        allParams.put(PROMPT_FIELD, prompt);
        log.info("Prompt used: {}", prompt);
    }

    // todo: handle default behavior without function calling
    private void setupToolsInterface(Map<String, String> params) {
        FunctionCallingFactory factory = new FunctionCallingFactory();
        if (!params.containsKey(LLM_INTERFACE) || params.get(LLM_INTERFACE).isEmpty()) {
            // default behavior - add to prompt
            return;
        }

        try {
            FunctionCalling functionCalling = factory.create(params.get(LLM_INTERFACE));
            functionCalling.configure(params);
            params.put(NO_ESCAPE_PARAMS_FIELD, DEFAULT_NO_ESCAPE_PARAMS);
        } catch (MLException e) {
            // adds tools to prompt if tool interface is invalid
            log.error("Invalid tool interface: {}", params.get(LLM_INTERFACE));
            params.remove(LLM_INTERFACE);
        }
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> apiParams, ActionListener<Object> listener) {
        Map<String, String> allParams = new HashMap<>();
        allParams.putAll(apiParams);
        allParams.putAll(mlAgent.getParameters());

        setupPromptParameters(allParams);
        setupToolsInterface(allParams);

        // planner prompt for the first call
        usePlannerPromptTemplate(allParams);

        String memoryId = allParams.get(MEMORY_ID_FIELD);
        String memoryType = mlAgent.getMemory().getType();
        String appType = mlAgent.getAppType();
        // toDo: get limit from somewhere
        int messageHistoryLimit = 10;

        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap.get(memoryType);
        conversationIndexMemoryFactory
            .create(apiParams.get(USER_PROMPT_FIELD), memoryId, appType, ActionListener.<ConversationIndexMemory>wrap(memory -> {
                memory.getMessages(ActionListener.<List<Interaction>>wrap(interactions -> {
                    List<String> completedSteps = new ArrayList<>();
                    for (Interaction interaction : interactions) {
                        String question = interaction.getInput();
                        String response = interaction.getResponse();

                        if (Strings.isNullOrEmpty(response)) {
                            continue;
                        }

                        completedSteps.add(question);
                        completedSteps.add(response);
                    }

                    if (!completedSteps.isEmpty()) {
                        addSteps(completedSteps, allParams, COMPLETED_STEPS_FIELD);
                        usePlannerWithHistoryPromptTemplate(allParams);
                    }

                    runAgent(mlAgent, allParams, completedSteps, memory, memory.getConversationId(), listener);
                }, e -> {
                    log.error("Failed to get chat history", e);
                    listener.onFailure(e);
                }), messageHistoryLimit);
            }, listener::onFailure));
    }

    private void runAgent(
        MLAgent mlAgent,
        Map<String, String> allParams,
        List<String> completedSteps,
        Memory memory,
        String conversationId,
        ActionListener<Object> finalListener
    ) {
        List<MLToolSpec> toolSpecs = getMlToolSpecs(mlAgent, allParams, client);

        Map<String, Tool> tools = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
        createTools(toolFactories, allParams, toolSpecs, tools, toolSpecMap, mlAgent);
        populateTools(tools, getToolNames(tools), allParams);

        executePlanningLoop(mlAgent.getLlm(), allParams, completedSteps, memory, conversationId, finalListener);
    }

    private void executePlanningLoop(
        LLMSpec llm,
        Map<String, String> allParams,
        List<String> completedSteps,
        Memory memory,
        String conversationId,
        ActionListener<Object> finalListener
    ) {

        AtomicInteger traceNumber = new AtomicInteger(0);
        String parentInteractionId = allParams.get(MLAgentExecutor.PARENT_INTERACTION_ID);

        MLPredictionTaskRequest request = new MLPredictionTaskRequest(
            llm.getModelId(),
            RemoteInferenceMLInput
                .builder()
                .algorithm(FunctionName.REMOTE)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(allParams).build())
                .build(),
            null,
            allParams.get(TENANT_ID_FIELD)
        );

        StepListener<MLTaskResponse> planListener = new StepListener<>();

        planListener.whenComplete(llmOutput -> {
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) llmOutput.getOutput();
            Map<String, String> parseLLMOutput = parseLLMOutput(allParams, modelTensorOutput);

            if (parseLLMOutput.get(RESULT_FIELD) != null) {
                String finalResult = parseLLMOutput.get(RESULT_FIELD);
                saveAndReturnFinalResult((ConversationIndexMemory) memory, parentInteractionId, finalResult, finalListener);
            } else {
                // todo: optimize double conversion of steps (string to list to string)
                List<String> steps = Arrays.stream(parseLLMOutput.get(STEPS_FIELD).split(", ")).toList();
                addSteps(steps, allParams, STEPS_FIELD);

                String stepToExecute = steps.getFirst();
                String reActAgentId = allParams.get(REACT_AGENT_ID_FIELD);
                Map<String, String> reactParams = new HashMap<>();
                reactParams.put(QUESTION_FIELD, stepToExecute);
                if (allParams.containsKey(REACT_AGENT_MEMORY_ID_FIELD)) {
                    reactParams.put(MEMORY_ID_FIELD, allParams.get(REACT_AGENT_MEMORY_ID_FIELD));
                }

                reactParams.put(SYSTEM_PROMPT_FIELD, DEFAULT_REACT_SYSTEM_PROMPT);

                AgentMLInput agentInput = AgentMLInput
                    .AgentMLInputBuilder()
                    .agentId(reActAgentId)
                    .functionName(FunctionName.AGENT)
                    .inputDataset(RemoteInferenceInputDataSet.builder().parameters(reactParams).build())
                    .build();

                MLExecuteTaskRequest executeRequest = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);

                client.execute(MLExecuteTaskAction.INSTANCE, executeRequest, ActionListener.wrap(executeResponse -> {
                    ModelTensorOutput reactResult = (ModelTensorOutput) executeResponse.getOutput();

                    // Navigate through the structure to get the response
                    Map<String, String> results = new HashMap<>();

                    // Process tensors in a single stream
                    reactResult.getMlModelOutputs().stream().flatMap(output -> output.getMlModelTensors().stream()).forEach(tensor -> {
                        if (MEMORY_ID_FIELD.equals(tensor.getName())) {
                            results.put(MEMORY_ID_FIELD, tensor.getResult());
                        } else {
                            Map<String, ?> dataMap = tensor.getDataAsMap();
                            if (dataMap != null && dataMap.containsKey(RESPONSE_FIELD)) {
                                results.put(STEP_RESULT_FIELD, (String) dataMap.get(RESPONSE_FIELD));
                            }
                        }
                    });

                    if (!results.containsKey(STEP_RESULT_FIELD)) {
                        throw new IllegalStateException("No valid response found in ReAct agent output");
                    }

                    // Only add memory_id to params if it exists and is not empty
                    String reActMemoryId = results.get(MEMORY_ID_FIELD);
                    if (reActMemoryId != null && !reActMemoryId.isEmpty()) {
                        allParams.put(REACT_AGENT_MEMORY_ID_FIELD, reActMemoryId);
                    }

                    completedSteps.add(stepToExecute);
                    completedSteps.add(results.get(STEP_RESULT_FIELD));

                    saveTraceData(
                        (ConversationIndexMemory) memory,
                        memory.getType(),
                        stepToExecute,
                        results.get(STEP_RESULT_FIELD),
                        conversationId,
                        false,
                        parentInteractionId,
                        traceNumber,
                        "DeepResearch LLM"
                    );

                    // 2. Then add completed steps to params
                    addSteps(completedSteps, allParams, COMPLETED_STEPS_FIELD);

                    useRevalPromptTemplate(allParams);

                    executePlanningLoop(llm, allParams, completedSteps, memory, conversationId, finalListener);
                }, e -> {
                    log.error("Failed to execute ReAct agent", e);
                    finalListener.onFailure(e);
                }));
            }
        }, e -> {
            log.error("Failed to run deep research agent", e);
            finalListener.onFailure(e);
        });

        client.execute(MLPredictionTaskAction.INSTANCE, request, planListener);
    }

    private Map<String, String> parseLLMOutput(Map<String, String> allParams, ModelTensorOutput modelTensorOutput) {
        Map<String, String> modelOutput = new HashMap<>();
        Map<String, ?> dataAsMap = modelTensorOutput.getMlModelOutputs().getFirst().getMlModelTensors().getFirst().getDataAsMap();
        String llmResponse;
        if (dataAsMap.size() == 1 && dataAsMap.containsKey(RESPONSE_FIELD)) {
            llmResponse = (String) dataAsMap.get(RESPONSE_FIELD);
        } else {
            llmResponse = JsonPath.read(dataAsMap, allParams.get(LLM_RESPONSE_FILTER));
        }

        String json = extractJsonFromModelResponse(llmResponse);
        if (!StringUtils.isJson(json)) {
            json = extractJsonFromMarkdown(llmResponse);
        }

        Map<String, Object> parsedJson = StringUtils.fromJson(json, RESPONSE_FIELD);

        if (!parsedJson.containsKey(STEPS_FIELD) && !parsedJson.containsKey(RESULT_FIELD)) {
            throw new IllegalArgumentException("Missing required fields 'steps' and 'result' in JSON response");
        }

        if (parsedJson.containsKey(STEPS_FIELD)) {
            List<String> steps = (List<String>) parsedJson.get(STEPS_FIELD);
            modelOutput.put(STEPS_FIELD, String.join(", ", steps));
        }

        if (parsedJson.containsKey(RESULT_FIELD)) {
            String result = (String) parsedJson.get(RESULT_FIELD);
            if (!result.isEmpty()) {
                modelOutput.put(RESULT_FIELD, result);
            }
        }

        return modelOutput;
    }

    private String extractJsonFromModelResponse(String response) {
        response = response.trim();
        if (!isJson(response)) {
            throw new IllegalStateException("Failed to parse LLM output due to invalid JSON");
        }
        return response;
    }

    private String extractJsonFromMarkdown(String response) {
        if (response.contains("```json")) {
            response = response.substring(response.indexOf("```json") + "```json".length());
            if (response.contains("```")) {
                response = response.substring(0, response.lastIndexOf("```"));
            }
        }

        response = response.trim();
        if (!isJson(response)) {
            throw new IllegalStateException("Failed to parse LLM output due to invalid JSON");
        }

        return response;
    }

    // todo: handle population for default
    private void populateTools(Map<String, Tool> tools, List<String> inputTools, Map<String, String> allParams) {
        if (allParams.containsKey(LLM_INTERFACE) && !allParams.get(LLM_INTERFACE).isEmpty()) {
            addToolsToFunctionCalling(tools, allParams, inputTools, "");
            return;
        }

        addToolsToPrompt(tools, allParams);
    }

    private void addToolsToPrompt(Map<String, Tool> tools, Map<String, String> allParams) {
        StringBuilder toolsPrompt = new StringBuilder("In this environment, you have access to the below tools: \n");
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            String toolDescription = entry.getValue().getDescription();
            toolsPrompt.append("- ").append(toolName).append(": ").append(toolDescription).append("\n").append("\n");
        }

        allParams.put(DEFAULT_PROMPT_TOOLS_FIELD, toolsPrompt.toString());
        populatePrompt(allParams);
    }

    private void addSteps(List<String> steps, Map<String, String> allParams, String field) {
        allParams.put(field, String.join(", ", steps));
    }

    private void saveAndReturnFinalResult(
        ConversationIndexMemory memory,
        String parentInteractionId,
        String finalResult,
        ActionListener<Object> finalListener
    ) {
        memory
            .getMemoryManager()
            .updateInteraction(parentInteractionId, Map.of(AI_RESPONSE_FIELD, finalResult), ActionListener.wrap(res -> {
                List<ModelTensors> finalModelTensors = createModelTensors(memory.getConversationId(), parentInteractionId);
                finalModelTensors
                    .add(
                        ModelTensors
                            .builder()
                            .mlModelTensors(
                                List.of(ModelTensor.builder().name(RESPONSE_FIELD).dataAsMap(Map.of(RESPONSE_FIELD, finalResult)).build())
                            )
                            .build()
                    );

                finalListener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
            }, e -> {
                log.error("Failed to update interaction with final result", e);
                finalListener.onFailure(e);
            }));
    }

    // Add helper method from MLChatAgentRunner
    private static List<ModelTensors> createModelTensors(String sessionId, String parentInteractionId) {
        List<ModelTensors> modelTensors = new ArrayList<>();
        modelTensors
            .add(
                ModelTensors
                    .builder()
                    .mlModelTensors(
                        List
                            .of(
                                ModelTensor.builder().name(MLAgentExecutor.MEMORY_ID).result(sessionId).build(),
                                ModelTensor.builder().name(MLAgentExecutor.PARENT_INTERACTION_ID).result(parentInteractionId).build()
                            )
                    )
                    .build()
            );
        return modelTensors;
    }
}
