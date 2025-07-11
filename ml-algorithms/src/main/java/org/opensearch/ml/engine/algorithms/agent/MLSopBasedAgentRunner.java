package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMcpToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.MAX_ITERATION;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.saveTraceData;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.addSteps;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.addToolsToPrompt;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.saveAndReturnFinalResult;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_PLANNER_WITH_HISTORY_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_REFLECT_PROMPT;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.DEFAULT_REFLECT_PROMPT_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
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
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.utils.SOP;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import joptsimple.internal.Strings;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLSopBasedAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;
    // flag to track if task has been updated with executor memory ids or not
    private boolean taskUpdated = false;
    private final Map<String, Object> taskUpdates = new HashMap<>();

    // prompts
    private String plannerPrompt;
    private String plannerPromptTemplate;
    private String reflectPrompt;
    private String reflectPromptTemplate;
    private String plannerWithHistoryPromptTemplate;

    // defaults
    private static final String DEFAULT_PLANNER_SYSTEM_PROMPT =
        "You are part of an OpenSearch cluster. When you deliver your final result, include a comprehensive report. This report MUST:\\n1. List every analysis or step you performed.\\n2. Summarize the inputs, methods, tools, and data used at each step.\\n3. Include key findings from all intermediate steps — do NOT omit them.\\n4. Clearly explain how the steps led to your final conclusion.\\n5. Return the full analysis and conclusion in the 'result' field, even if some of this was mentioned earlier.\\n\\nThe final response should be fully self-contained and detailed, allowing a user to understand the full investigation without needing to reference prior messages. Always respond in JSON format.";
    public static String DEFAULT_EXECUTOR_SYSTEM_PROMPT =
        "Today is ##--TODAY--##. You are a dedicated helper agent working as part of a plan‑execute‑reflect framework. Your role is to receive a discrete task, execute all necessary internal reasoning or tool calls, and return a single, final response that fully addresses the task. You must never return an empty response. If you are unable to complete the task or retrieve meaningful information, you must respond with a clear explanation of the issue or what was missing. Under no circumstances should you end your reply with a question or ask for more information. If you search any index, always include the raw documents in the final result instead of summarizing the content. This is critical to give visibility into what the query retrieved.";
    private static final String DEFAULT_NO_ESCAPE_PARAMS = "tool_configs,_tools";
    private static final String DEFAULT_MAX_STEPS_EXECUTED = "20";
    private static final int DEFAULT_MESSAGE_HISTORY_LIMIT = 10;
    public static final String DEFAULT_REACT_MAX_ITERATIONS = "20";

    private static final String DEFALUT_SUMMARIZE_PROMPT =
        "You are a dedicated helper agent working on summarize the interaction. We just finish an interaction process targeting on the user's question. You should answer user's question based on the interaction process. \\n Here is the interaction: ${allParams.completed_steps} \\n And here is the user's question. ${allParams.user_prompt} \\n Please give a short summarization and give the answer";

    private static final String DEFAULT_FIND_NEXT_STEP_PROMPT =
        "You are a dedicated helper agent working on choosing the next step. We need you to do some decisions after some interactions. \\n Here is the interaction: ${allParams.completed_steps}. \\n And here is the next steps with its entrance conditions: ${allParams.next_step_description}. \\n You should return your answer with some reasoning. Then wrap your final step number inside <next_option> </next_option>. You should always gives a option number according to your judgement. For example, <next_option> 3 </next_option>";

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
    public static final String SUMMARIZE_MODEL_ID_FIELD = "summarize_model_id";
    public static final String NEXT_STEP_DESCRIPTION_FIELD = "next_step_description";

    public MLSopBasedAgentRunner(
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

    @VisibleForTesting
    void setupPromptParameters(Map<String, String> params) {
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

    @VisibleForTesting
    void usePlannerPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.plannerPromptTemplate);
        populatePrompt(params);
    }

    @VisibleForTesting
    void useReflectPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.reflectPromptTemplate);
        populatePrompt(params);
    }

    @VisibleForTesting
    void usePlannerWithHistoryPromptTemplate(Map<String, String> params) {
        params.put(PROMPT_TEMPLATE_FIELD, this.plannerWithHistoryPromptTemplate);
        populatePrompt(params);
    }

    @VisibleForTesting
    void populatePrompt(Map<String, String> allParams) {
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
            String sopString = allParams.getOrDefault("sop", "");
            Map<String, Object> map = gson.fromJson(sopString, Map.class);
            SOP sop = new SOP(map);
            executePlanningLoop(mlAgent.getLlm(), allParams, completedSteps, memory, conversationId, traceNumber, sop, finalListener);
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
        AtomicInteger traceNumber,
        SOP sop,
        ActionListener<Object> finalListener
    ) {
        int maxSteps = Integer.parseInt(allParams.getOrDefault(MAX_STEPS_EXECUTED_FIELD, DEFAULT_MAX_STEPS_EXECUTED));
        String parentInteractionId = allParams.get(MLAgentExecutor.PARENT_INTERACTION_ID);

        // completedSteps stores the step and its result, hence divide by 2 to find total steps completed
        // on reaching max iteration, update parent interaction question with last executed step rather than task to allow continue using
        // memory_id
        if (Objects.isNull(sop) || sop.getNextSteps().size() == 0) { // in the end, do summarize
            StringSubstitutor substitutor = new StringSubstitutor(allParams, "${allParams.", "}");
            String summarizePrompt = substitutor.replace(DEFALUT_SUMMARIZE_PROMPT);
            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
                .builder()
                .parameters(Map.of("prompt", summarizePrompt))
                .build();
            MLPredictionTaskRequest request = new MLPredictionTaskRequest(
                allParams.get(SUMMARIZE_MODEL_ID_FIELD),
                RemoteInferenceMLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(),
                null,
                allParams.get(TENANT_ID_FIELD)
            );

            client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(mlTaskResponse -> {
                ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
                ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
                ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
                Map<String, Object> dataAsMap = (Map<String, Object>) modelTensor.getDataAsMap();
                String finalResult = ((Map<Object, String>) ((List) dataAsMap.get("content")).get(0)).get("text");
                saveAndReturnFinalResult(
                    (ConversationIndexMemory) memory,
                    parentInteractionId,
                    allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD),
                    allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD),
                    finalResult,
                    null,
                    finalListener
                );
            }, e -> {
                log.info("balaba");
                finalListener.onFailure(e);
            }));

            return;
        }
        String stepToExecute = sop.getCurrentStep();
        String reActAgentId = allParams.get(EXECUTOR_AGENT_ID_FIELD);
        Map<String, String> reactParams = new HashMap<>();
        reactParams.put(QUESTION_FIELD, stepToExecute);
        if (allParams.containsKey(EXECUTOR_AGENT_MEMORY_ID_FIELD)) {
            reactParams.put(MEMORY_ID_FIELD, allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD));
        }
        String formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DEFAULT_EXECUTOR_SYSTEM_PROMPT = DEFAULT_EXECUTOR_SYSTEM_PROMPT.replace("##--TODAY--##", formattedDate);
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

            Map<String, Object> memoryUpdates = new HashMap<>();
            if (allParams.containsKey(EXECUTOR_AGENT_MEMORY_ID_FIELD)) {
                memoryUpdates.put(EXECUTOR_AGENT_MEMORY_ID_FIELD, allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD));
            }

            if (allParams.containsKey(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD)) {
                memoryUpdates.put(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD, allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD));
            }

            String taskId = allParams.get(TASK_ID_FIELD);
            if (taskId != null && !taskUpdated) {
                taskUpdates.put(STATE_FIELD, MLTaskState.RUNNING);
                taskUpdates.put(RESPONSE_FIELD, memoryUpdates);
                updateMLTaskDirectly(taskId, taskUpdates, client, ActionListener.wrap(updateResponse -> {
                    log.info("Updated task {} with executor memory ID", taskId);
                    taskUpdated = true;
                }, e -> log.error("Failed to update task {} with executor memory ID", taskId, e)));
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
            if (sop.getNextSteps().size() <= 1) {
                executePlanningLoop(
                    llm,
                    allParams,
                    completedSteps,
                    memory,
                    conversationId,
                    traceNumber,
                    sop.getNextSteps().getFirst(),
                    finalListener
                );
            } else {
                chooseNextStep(llm, allParams, completedSteps, memory, conversationId, traceNumber, sop, finalListener);
            }

        }, e -> {}));

    }

    private void chooseNextStep(
        LLMSpec llm,
        Map<String, String> allParams,
        List<String> completedSteps,
        Memory memory,
        String conversationId,
        AtomicInteger traceNumber,
        SOP sop,
        ActionListener<Object> finalListener
    ) {
        String nextStepDescription = sop.formatNextStep();
        allParams.put(NEXT_STEP_DESCRIPTION_FIELD, nextStepDescription);
        String parentInteractionId = allParams.get(MLAgentExecutor.PARENT_INTERACTION_ID);
        StringSubstitutor substitutor = new StringSubstitutor(allParams, "${allParams.", "}");
        String summarizePrompt = substitutor.replace(DEFAULT_FIND_NEXT_STEP_PROMPT);
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("prompt", summarizePrompt))
            .build();

        MLPredictionTaskRequest request = new MLPredictionTaskRequest(
            allParams.get(SUMMARIZE_MODEL_ID_FIELD),
            RemoteInferenceMLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(),
            null,
            allParams.get(TENANT_ID_FIELD)
        );

        client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(mlTaskResponse -> {
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
            ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            Map<String, Object> dataAsMap = (Map<String, Object>) modelTensor.getDataAsMap();
            String finalResult = ((Map<Object, String>) ((List) dataAsMap.get("content")).get(0)).get("text");
            saveTraceData(
                (ConversationIndexMemory) memory,
                memory.getType(),
                summarizePrompt,
                finalResult,
                conversationId,
                false,
                parentInteractionId,
                traceNumber,
                "Step choosing Agent"
            );
            int nextStep = parseNextStep(finalResult);
            executePlanningLoop(
                llm,
                allParams,
                completedSteps,
                memory,
                conversationId,
                traceNumber,
                sop.getNextSteps().get(nextStep - 1),
                finalListener
            );

        }, e -> { finalListener.onFailure(e); }));
    }

    private int parseNextStep(String result) {
        String realResult = result.split("<next_option>")[1].split("</next_option>")[0].strip();
        return Integer.parseInt(realResult);
    }

}
