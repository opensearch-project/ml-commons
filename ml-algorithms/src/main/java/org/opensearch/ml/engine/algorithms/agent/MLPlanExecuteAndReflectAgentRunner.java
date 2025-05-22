/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.cleanUpResource;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMcpToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.MAX_ITERATION;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.saveTraceData;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_WITH_HISTORY_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_REFLECT_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_REFLECT_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import joptsimple.internal.Strings;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLPlanExecuteAndReflectAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;

    // prompts
    private String plannerPrompt;
    private String plannerPromptTemplate;
    private String reflectPrompt;
    private String reflectPromptTemplate;
    private String plannerWithHistoryPromptTemplate;

    // defaults
    private static final String DEFAULT_PLANNER_SYSTEM_PROMPT =
        "You are part of an OpenSearch cluster. When you deliver your final result, include a comprehensive report. This report MUST:\\n1. List every analysis or step you performed.\\n2. Summarize the inputs, methods, tools, and data used at each step.\\n3. Include key findings from all intermediate steps — do NOT omit them.\\n4. Clearly explain how the steps led to your final conclusion.\\n5. Return the full analysis and conclusion in the 'result' field, even if some of this was mentioned earlier.\\n\\nThe final response should be fully self-contained and detailed, allowing a user to understand the full investigation without needing to reference prior messages. Always respond in JSON format.";
    private static final String DEFAULT_EXECUTOR_SYSTEM_PROMPT =
        "You are a dedicated helper agent working as part of a plan‑execute‑reflect framework. Your role is to receive a discrete task, execute all necessary internal reasoning or tool calls, and return a single, final response that fully addresses the task. You must never return an empty response. If you are unable to complete the task or retrieve meaningful information, you must respond with a clear explanation of the issue or what was missing. Under no circumstances should you end your reply with a question or ask for more information. If you search any index, always include the raw documents in the final result instead of summarizing the content. This is critical to give visibility into what the query retrieved.";
    private static final String DEFAULT_NO_ESCAPE_PARAMS = "tool_configs,_tools";
    private static final String DEFAULT_MAX_STEPS_EXECUTED = "20";
    private static final int DEFAULT_MESSAGE_HISTORY_LIMIT = 10;
    private static final String DEFAULT_REACT_MAX_ITERATIONS = "20";

    // fields
    public static final String PROMPT_FIELD = "prompt";
    public static final String USER_PROMPT_FIELD = "user_prompt";
    public static final String EXECUTOR_SYSTEM_PROMPT_FIELD = "executor_system_prompt";
    public static final String STEPS_FIELD = "steps";
    public static final String COMPLETED_STEPS_FIELD = "completed_steps";
    public static final String PLANNER_PROMPT_FIELD = "planner_prompt";
    public static final String REFLECT_PROMPT_FIELD = "reflect_prompt";
    public static final String PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT_FIELD = "plan_execute_reflect_response_format";
    public static final String PROMPT_TEMPLATE_FIELD = "prompt_template";
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    public static final String QUESTION_FIELD = "question";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String PARENT_INTERACTION_ID_FIELD = "parent_interaction_id";
    public static final String TENANT_ID_FIELD = "tenant_id";
    public static final String RESULT_FIELD = "result";
    public static final String RESPONSE_FIELD = "response";
    public static final String STEP_RESULT_FIELD = "step_result";
    public static final String EXECUTOR_AGENT_ID_FIELD = "executor_agent_id";
    public static final String EXECUTOR_AGENT_MEMORY_ID_FIELD = "executor_agent_memory_id";
    public static final String EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD = "executor_agent_parent_interaction_id";
    public static final String NO_ESCAPE_PARAMS_FIELD = "no_escape_params";
    public static final String DEFAULT_PROMPT_TOOLS_FIELD = "tools_prompt";
    public static final String MAX_STEPS_EXECUTED_FIELD = "max_steps";
    public static final String PLANNER_PROMPT_TEMPLATE_FIELD = "planner_prompt_template";
    public static final String REFLECT_PROMPT_TEMPLATE_FIELD = "reflect_prompt_template";
    public static final String PLANNER_WITH_HISTORY_TEMPLATE_FIELD = "planner_with_history_template";
    public static final String EXECUTOR_MAX_ITERATIONS_FIELD = "executor_max_iterations";

    public MLPlanExecuteAndReflectAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry registry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = registry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.plannerPrompt = DEFAULT_PLANNER_PROMPT;
        this.plannerPromptTemplate = DEFAULT_PLANNER_PROMPT_TEMPLATE;
        this.reflectPrompt = DEFAULT_REFLECT_PROMPT;
        this.reflectPromptTemplate = DEFAULT_REFLECT_PROMPT_TEMPLATE;
        this.plannerWithHistoryPromptTemplate = DEFAULT_PLANNER_WITH_HISTORY_PROMPT_TEMPLATE;
    }

    private void setupPromptParameters(Map<String, String> params) {
        // populated depending on whether LLM is asked to plan or re-evaluate
        // removed here, so that error is thrown in case this field is not populated
        params.remove(PROMPT_FIELD);

        String userPrompt = params.get(QUESTION_FIELD);
        params.put(USER_PROMPT_FIELD, userPrompt);
        params.put(SYSTEM_PROMPT_FIELD, params.getOrDefault(SYSTEM_PROMPT_FIELD, DEFAULT_PLANNER_SYSTEM_PROMPT));

        if (params.get(PLANNER_PROMPT_FIELD) != null) {
            this.plannerPrompt = params.get(PLANNER_PROMPT_FIELD);
        }
        params.put(PLANNER_PROMPT_FIELD, this.plannerPrompt);

        if (params.get(PLANNER_PROMPT_TEMPLATE_FIELD) != null) {
            this.plannerPromptTemplate = params.get(PLANNER_PROMPT_TEMPLATE_FIELD);
        }

        if (params.get(REFLECT_PROMPT_FIELD) != null) {
            this.reflectPrompt = params.get(REFLECT_PROMPT_FIELD);
        }
        params.put(REFLECT_PROMPT_FIELD, this.reflectPrompt);

        if (params.get(REFLECT_PROMPT_TEMPLATE_FIELD) != null) {
            this.reflectPromptTemplate = params.get(REFLECT_PROMPT_TEMPLATE_FIELD);
        }

        if (params.get(PLANNER_WITH_HISTORY_TEMPLATE_FIELD) != null) {
            this.plannerWithHistoryPromptTemplate = params.get(PLANNER_WITH_HISTORY_TEMPLATE_FIELD);
        }

        params.put(PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT_FIELD, PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT);

        params.put(NO_ESCAPE_PARAMS_FIELD, DEFAULT_NO_ESCAPE_PARAMS);

        // setting defaults for llm response
        if (params.containsKey(LLM_INTERFACE) && (!params.containsKey(LLM_RESPONSE_FILTER) || params.get(LLM_RESPONSE_FILTER).isEmpty())) {
            String llmInterface = params.get(LLM_INTERFACE);
            String llmResponseFilter = switch (llmInterface.trim().toLowerCase(Locale.ROOT)) {
                case LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE, LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1 ->
                    "$.output.message.content[0].text";
                case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS -> "$.choices[0].message.content";
                default -> throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
            };

            params.put(LLM_RESPONSE_FILTER, llmResponseFilter);
        }
    }

    private void usePlannerPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.plannerPromptTemplate);
        populatePrompt(params);
    }

    private void useReflectPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.reflectPromptTemplate);
        populatePrompt(params);
    }

    private void usePlannerWithHistoryPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.plannerWithHistoryPromptTemplate);
        populatePrompt(params);
    }

    private void populatePrompt(Map<String, String> allParams) {
        String promptTemplate = allParams.get(PROMPT_TEMPLATE_FIELD);
        StringSubstitutor promptSubstitutor = new StringSubstitutor(allParams, "${parameters.", "}");
        String prompt = promptSubstitutor.replace(promptTemplate);
        allParams.put(PROMPT_FIELD, prompt);
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> apiParams, ActionListener<Object> listener) {
        Map<String, String> allParams = new HashMap<>();
        allParams.putAll(apiParams);
        allParams.putAll(mlAgent.getParameters());

        setupPromptParameters(allParams);

        // planner prompt for the first call
        usePlannerPromptTemplate(allParams);

        String memoryId = allParams.get(MEMORY_ID_FIELD);
        String memoryType = mlAgent.getMemory().getType();
        String appType = mlAgent.getAppType();
        int messageHistoryLimit = DEFAULT_MESSAGE_HISTORY_LIMIT;

        // todo: use chat history instead of completed steps
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

                    setToolsAndRunAgent(mlAgent, allParams, completedSteps, memory, memory.getConversationId(), listener);
                }, e -> {
                    log.error("Failed to get chat history", e);
                    listener.onFailure(e);
                }), messageHistoryLimit);
            }, listener::onFailure));
    }

    private void setToolsAndRunAgent(
        MLAgent mlAgent,
        Map<String, String> allParams,
        List<String> completedSteps,
        Memory memory,
        String conversationId,
        ActionListener<Object> finalListener
    ) {
        List<MLToolSpec> toolSpecs = getMlToolSpecs(mlAgent, allParams);

        // Create a common method to handle both success and failure cases
        Consumer<List<MLToolSpec>> processTools = (allToolSpecs) -> {
            Map<String, Tool> tools = new HashMap<>();
            Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
            createTools(toolFactories, allParams, allToolSpecs, tools, toolSpecMap, mlAgent);
            addToolsToPrompt(tools, allParams);

            AtomicInteger traceNumber = new AtomicInteger(0);

            executePlanningLoop(mlAgent.getLlm(), allParams, completedSteps, memory, conversationId, 0, traceNumber, finalListener);
        };

        // Fetch MCP tools and handle both success and failure cases
        getMcpToolSpecs(mlAgent, client, sdkClient, encryptor, ActionListener.wrap(mcpTools -> {
            toolSpecs.addAll(mcpTools);
            processTools.accept(toolSpecs);
        }, e -> {
            log.warn("Failed to get MCP tools, continuing with base tools only", e);
            processTools.accept(toolSpecs);
        }));
    }

    private void executePlanningLoop(
        LLMSpec llm,
        Map<String, String> allParams,
        List<String> completedSteps,
        Memory memory,
        String conversationId,
        int stepsExecuted,
        AtomicInteger traceNumber,
        ActionListener<Object> finalListener
    ) {
        int maxSteps = Integer.parseInt(allParams.getOrDefault(MAX_STEPS_EXECUTED_FIELD, DEFAULT_MAX_STEPS_EXECUTED));
        String parentInteractionId = allParams.get(MLAgentExecutor.PARENT_INTERACTION_ID);

        // completedSteps stores the step and its result, hence divide by 2 to find total steps completed
        // on reaching max iteration, update parent interaction question with last executed step rather than task to allow continue using
        // memory_id
        if (stepsExecuted >= maxSteps) {
            String finalResult = String
                .format(
                    "Max Steps Limit Reached. Use memory_id with same task to restart. \n "
                        + "Last executed step: %s, \n "
                        + "Last executed step result: %s",
                    completedSteps.get(completedSteps.size() - 2),
                    completedSteps.getLast()
                );
            saveAndReturnFinalResult(
                (ConversationIndexMemory) memory,
                parentInteractionId,
                finalResult,
                completedSteps.get(completedSteps.size() - 2),
                allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD),
                allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD),
                finalListener
            );
            return;
        }

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
                saveAndReturnFinalResult(
                    (ConversationIndexMemory) memory,
                    parentInteractionId,
                    allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD),
                    allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD),
                    finalResult,
                    null,
                    finalListener
                );
            } else {
                // todo: optimize double conversion of steps (string to list to string)
                List<String> steps = Arrays.stream(parseLLMOutput.get(STEPS_FIELD).split(", ")).toList();
                addSteps(steps, allParams, STEPS_FIELD);

                String stepToExecute = steps.getFirst();
                String reActAgentId = allParams.get(EXECUTOR_AGENT_ID_FIELD);
                Map<String, String> reactParams = new HashMap<>();
                reactParams.put(QUESTION_FIELD, stepToExecute);
                if (allParams.containsKey(EXECUTOR_AGENT_MEMORY_ID_FIELD)) {
                    reactParams.put(MEMORY_ID_FIELD, allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD));
                }

                reactParams.put(SYSTEM_PROMPT_FIELD, allParams.getOrDefault(EXECUTOR_SYSTEM_PROMPT_FIELD, DEFAULT_EXECUTOR_SYSTEM_PROMPT));
                reactParams.put(LLM_RESPONSE_FILTER, allParams.get(LLM_RESPONSE_FILTER));
                reactParams.put(MAX_ITERATION, allParams.getOrDefault(EXECUTOR_MAX_ITERATIONS_FIELD, DEFAULT_REACT_MAX_ITERATIONS));

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

                    if (!results.containsKey(STEP_RESULT_FIELD)) {
                        throw new IllegalStateException("No valid response found in ReAct agent output");
                    }

                    // Only add memory_id to params if it exists and is not empty
                    String reActMemoryId = results.get(MEMORY_ID_FIELD);
                    if (reActMemoryId != null && !reActMemoryId.isEmpty()) {
                        allParams.put(EXECUTOR_AGENT_MEMORY_ID_FIELD, reActMemoryId);
                    }

                    String reActParentInteractionId = results.get(PARENT_INTERACTION_ID_FIELD);
                    if (reActParentInteractionId != null && !reActParentInteractionId.isEmpty()) {
                        allParams.put(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD, reActParentInteractionId);
                    }

                    completedSteps.add(String.format("\nStep: %s\n", stepToExecute));
                    completedSteps.add(String.format("\nStep Result: %s\n", results.get(STEP_RESULT_FIELD)));

                    saveTraceData(
                        (ConversationIndexMemory) memory,
                        memory.getType(),
                        stepToExecute,
                        results.get(STEP_RESULT_FIELD),
                        conversationId,
                        false,
                        parentInteractionId,
                        traceNumber,
                        "PlanExecuteReflect Agent"
                    );

                    addSteps(completedSteps, allParams, COMPLETED_STEPS_FIELD);

                    useReflectPromptTemplate(allParams);

                    executePlanningLoop(
                        llm,
                        allParams,
                        completedSteps,
                        memory,
                        conversationId,
                        stepsExecuted + 1,
                        traceNumber,
                        finalListener
                    );
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
            llmResponse = ((String) dataAsMap.get(RESPONSE_FIELD)).trim();
        } else {
            if (!allParams.containsKey(LLM_RESPONSE_FILTER) || allParams.get(LLM_RESPONSE_FILTER).isEmpty()) {
                throw new IllegalArgumentException("llm_response_filter not found. Please provide the path to the model output.");
            }

            llmResponse = ((String) JsonPath.read(dataAsMap, allParams.get(LLM_RESPONSE_FILTER))).trim();
        }

        // if response is not a pure json, check if it is returned as markdown and fetch that
        String json = StringUtils.isJson(llmResponse) ? llmResponse : extractJsonFromMarkdown(llmResponse);

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

    private String extractJsonFromMarkdown(String response) {
        response = response.trim();
        if (response.contains("```json")) {
            response = response.substring(response.indexOf("```json") + "```json".length());
            if (response.contains("```")) {
                response = response.substring(0, response.lastIndexOf("```"));
            }
        } else {
            // extract content from {} block
            if (response.contains("{") && response.contains("}")) {
                response = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }
        }

        response = response.trim();
        if (!isJson(response)) {
            throw new IllegalStateException("Failed to parse LLM output due to invalid JSON");
        }

        return response;
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
        cleanUpResource(tools);
    }

    private void addSteps(List<String> steps, Map<String, String> allParams, String field) {
        allParams.put(field, String.join(", ", steps));
    }

    private void saveAndReturnFinalResult(
        ConversationIndexMemory memory,
        String parentInteractionId,
        String reactAgentMemoryId,
        String reactParentInteractionId,
        String finalResult,
        String input,
        ActionListener<Object> finalListener
    ) {
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put(INTERACTIONS_RESPONSE_FIELD, finalResult);

        if (input != null) {
            updateContent.put(INTERACTIONS_INPUT_FIELD, input);
        }

        memory.getMemoryManager().updateInteraction(parentInteractionId, updateContent, ActionListener.wrap(res -> {
            List<ModelTensors> finalModelTensors = createModelTensors(
                memory.getConversationId(),
                parentInteractionId,
                reactAgentMemoryId,
                reactParentInteractionId
            );
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

    private static List<ModelTensors> createModelTensors(
        String sessionId,
        String parentInteractionId,
        String reactAgentMemoryId,
        String reactParentInteractionId
    ) {
        List<ModelTensors> modelTensors = new ArrayList<>();
        modelTensors
            .add(
                ModelTensors
                    .builder()
                    .mlModelTensors(
                        List
                            .of(
                                ModelTensor.builder().name(MLAgentExecutor.MEMORY_ID).result(sessionId).build(),
                                ModelTensor.builder().name(MLAgentExecutor.PARENT_INTERACTION_ID).result(parentInteractionId).build(),
                                ModelTensor.builder().name(EXECUTOR_AGENT_MEMORY_ID_FIELD).result(reactAgentMemoryId).build(),
                                ModelTensor
                                    .builder()
                                    .name(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD)
                                    .result(reactParentInteractionId)
                                    .build()
                            )
                    )
                    .build()
            );
        return modelTensors;
    }
}
