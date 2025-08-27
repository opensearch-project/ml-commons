/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.cleanUpResource;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getCurrentDateTime;
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
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.EXECUTOR_RESPONSIBILITY;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.FINAL_RESULT_RESPONSE_INSTRUCTIONS;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.PLANNER_RESPONSIBILITY;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.StepListener;
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
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory;
import org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemoryRecord;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.JsonPath;

import joptsimple.internal.Strings;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLPlanExecuteAndReflectAgentRunner implements MLAgentRunner {

    // CRITICAL FIX: Cache BedrockAgentCoreMemory configuration to persist across internal calls
    private static final Map<String, Map<String, String>> bedrockMemoryConfigCache = new ConcurrentHashMap<>();

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

    // CRITICAL FIX: Master session ID to maintain conversation continuity across all agent calls
    private String masterSessionId = null;

    // CRITICAL FIX: Executor memory ID shared across all executor calls for BedrockAgentCoreMemory
    private String executorMemoryId = null;

    // prompts
    private String plannerPrompt;
    private String plannerPromptTemplate;
    private String reflectPrompt;
    private String reflectPromptTemplate;
    private String plannerWithHistoryPromptTemplate;

    @VisibleForTesting
    static final String DEFAULT_PLANNER_SYSTEM_PROMPT = PLANNER_RESPONSIBILITY + PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT
        + FINAL_RESULT_RESPONSE_INSTRUCTIONS;

    @VisibleForTesting
    static final String DEFAULT_EXECUTOR_SYSTEM_PROMPT = EXECUTOR_RESPONSIBILITY;

    private static final String DEFAULT_NO_ESCAPE_PARAMS = "tool_configs,_tools";
    private static final String DEFAULT_MAX_STEPS_EXECUTED = "20";
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

    // controls how many messages (last x) from planner memory are passed as context during planning phase
    // these messages are added as completed steps in the reflect prompt
    public static final String PLANNER_MESSAGE_HISTORY_LIMIT = "message_history_limit";
    private static final String DEFAULT_MESSAGE_HISTORY_LIMIT = "10";

    // controls how many messages from executor memory are passed as context during step execution
    public static final String EXECUTOR_MESSAGE_HISTORY_LIMIT = "executor_message_history_limit";
    private static final String DEFAULT_EXECUTOR_MESSAGE_HISTORY_LIMIT = "10";

    public static final String INJECT_DATETIME_FIELD = "inject_datetime";
    public static final String DATETIME_FORMAT_FIELD = "datetime_format";

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

    @VisibleForTesting
    void setupPromptParameters(Map<String, String> params) {
        // populated depending on whether LLM is asked to plan or re-evaluate
        // removed here, so that error is thrown in case this field is not populated
        params.remove(PROMPT_FIELD);

        String userPrompt = params.get(QUESTION_FIELD);
        params.put(USER_PROMPT_FIELD, userPrompt);

        boolean injectDate = Boolean.parseBoolean(params.getOrDefault(INJECT_DATETIME_FIELD, "false"));
        String dateFormat = params.get(DATETIME_FORMAT_FIELD);
        String currentDateTime = injectDate ? getCurrentDateTime(dateFormat) : "";

        String plannerSystemPrompt = params.getOrDefault(SYSTEM_PROMPT_FIELD, DEFAULT_PLANNER_SYSTEM_PROMPT);
        if (injectDate) {
            plannerSystemPrompt = String.format("%s\n\n%s", plannerSystemPrompt, currentDateTime);
        }
        params.put(SYSTEM_PROMPT_FIELD, plannerSystemPrompt);

        String executorSystemPrompt = params.getOrDefault(EXECUTOR_SYSTEM_PROMPT_FIELD, DEFAULT_EXECUTOR_SYSTEM_PROMPT);
        if (injectDate) {
            executorSystemPrompt = String.format("%s\n\n%s", executorSystemPrompt, currentDateTime);
        }
        params.put(EXECUTOR_SYSTEM_PROMPT_FIELD, executorSystemPrompt);

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
        Map<String, String> allParams = setupAllParameters(mlAgent, apiParams);
        String memoryType = configureMemoryType(mlAgent, allParams);

        String memoryId = allParams.get(MEMORY_ID_FIELD);
        String appType = mlAgent.getAppType();
        int messageHistoryLimit = Integer.parseInt(allParams.getOrDefault(PLANNER_MESSAGE_HISTORY_LIMIT, DEFAULT_MESSAGE_HISTORY_LIMIT));

        // If no memory type is available, use default
        if (memoryType == null) {
            log.info("No memory type found in agent configuration or parameters, using default conversation_index");
            memoryType = "conversation_index";
        }

        // CRITICAL FIX: Initialize session IDs once for BedrockAgentCoreMemory to maintain conversation continuity
        if ("bedrock_agentcore_memory".equals(memoryType) && masterSessionId == null) {
            masterSessionId = "bedrock-session-" + System.currentTimeMillis();
            executorMemoryId = "bedrock-executor-" + System.currentTimeMillis();
            log.info("DEBUG: Created master session ID for BedrockAgentCoreMemory: {}", masterSessionId);
            log.info("DEBUG: Created executor memory ID for BedrockAgentCoreMemory: {}", executorMemoryId);
        }

        // Handle different memory types
        Object memoryFactory = memoryFactoryMap.get(memoryType);
        if (memoryFactory instanceof ConversationIndexMemory.Factory) {
            ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactory;
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
        } else if (memoryFactory instanceof BedrockAgentCoreMemory.Factory) {
            BedrockAgentCoreMemory.Factory bedrockMemoryFactory = (BedrockAgentCoreMemory.Factory) memoryFactory;

            // Build parameters for BedrockAgentCoreMemory from request parameters
            Map<String, Object> memoryParams = new HashMap<>();

            // Extract memory configuration from parameters passed by MLAgentExecutor
            String memoryArn = allParams.get("memory_arn");
            String memoryRegion = allParams.get("memory_region");
            String accessKey = allParams.get("memory_access_key");
            String secretKey = allParams.get("memory_secret_key");
            String sessionToken = allParams.get("memory_session_token");

            if (memoryArn != null) {
                memoryParams.put("memory_arn", memoryArn);
            }
            if (memoryRegion != null) {
                memoryParams.put("region", memoryRegion);
            }

            // Use masterSessionId for BedrockAgentCoreMemory to maintain conversation continuity
            String sessionIdToUse = masterSessionId != null ? masterSessionId : memoryId;
            if (sessionIdToUse != null) {
                memoryParams.put("session_id", sessionIdToUse);
                log.info("DEBUG: Using session ID for BedrockAgentCoreMemory: {}", sessionIdToUse);
            }

            // Use agent ID from parameters (the actual agent execution ID) as agent_id - MANDATORY
            String agentIdToUse = allParams.get("agent_id");
            if (agentIdToUse == null) {
                throw new IllegalArgumentException(
                    "Agent ID is mandatory but not found in parameters. This indicates a configuration issue - please check agent setup."
                );
            }
            memoryParams.put("agent_id", agentIdToUse);
            log.info("DEBUG: Using mandatory agent ID for BedrockAgentCoreMemory actorId: {}", agentIdToUse);

            // Add credentials if available
            if (accessKey != null && secretKey != null) {
                Map<String, String> credentials = new HashMap<>();
                credentials.put("access_key", accessKey);
                credentials.put("secret_key", secretKey);
                if (sessionToken != null) {
                    credentials.put("session_token", sessionToken);
                }
                memoryParams.put("credentials", credentials);
            }

            log.info("Creating BedrockAgentCoreMemory with params: memory_arn={}, region={}", memoryArn, memoryRegion);

            bedrockMemoryFactory.create(memoryParams, ActionListener.wrap(bedrockMemory -> {
                // Get conversation history from Bedrock AgentCore using master session ID
                String sessionForHistory = masterSessionId != null ? masterSessionId : memoryId;
                bedrockMemory.getConversationHistory(sessionForHistory, ActionListener.wrap(records -> {
                    List<String> completedSteps = new ArrayList<>();

                    // Convert BedrockAgentCoreMemoryRecords to completed steps format (similar to ConversationIndexMemory)
                    for (BedrockAgentCoreMemoryRecord record : records) {
                        if (record != null && record.getContent() != null && record.getResponse() != null) {
                            completedSteps.add(record.getContent());    // Question
                            completedSteps.add(record.getResponse());   // Response
                        }
                    }

                    if (!completedSteps.isEmpty()) {
                        addSteps(completedSteps, allParams, COMPLETED_STEPS_FIELD);
                        usePlannerWithHistoryPromptTemplate(allParams);
                    }

                    setToolsAndRunAgent(
                        mlAgent,
                        allParams,
                        completedSteps,
                        bedrockMemory,
                        masterSessionId != null ? masterSessionId : memoryId,
                        listener
                    );
                }, e -> {
                    log.warn("Failed to get conversation history from BedrockAgentCoreMemory, proceeding without history", e);
                    List<String> completedSteps = new ArrayList<>();
                    setToolsAndRunAgent(
                        mlAgent,
                        allParams,
                        completedSteps,
                        bedrockMemory,
                        masterSessionId != null ? masterSessionId : memoryId,
                        listener
                    );
                }));
            }, listener::onFailure));
        } else {
            // For other memory types, skip chat history
            log.info("Skipping chat history for memory type: {}", memoryType);
            List<String> completedSteps = new ArrayList<>();
            setToolsAndRunAgent(mlAgent, allParams, completedSteps, null, memoryId, listener);
        }
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
                memory,
                parentInteractionId,
                allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD),
                allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD),
                finalResult,
                allParams.get(QUESTION_FIELD), // Use question if available, null otherwise for backward compatibility
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
                    memory,
                    parentInteractionId,
                    allParams.get(EXECUTOR_AGENT_MEMORY_ID_FIELD),
                    allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD),
                    finalResult,
                    allParams.get(QUESTION_FIELD), // Use question if available, null otherwise for backward compatibility
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
                reactParams
                    .put(
                        MLAgentExecutor.MESSAGE_HISTORY_LIMIT,
                        allParams.getOrDefault(EXECUTOR_MESSAGE_HISTORY_LIMIT, DEFAULT_EXECUTOR_MESSAGE_HISTORY_LIMIT)
                    );

                // CRITICAL FIX: Preserve BedrockAgentCoreMemory parameters for subsequent agent calls
                if ("bedrock_agentcore_memory".equals(allParams.get("memory_type"))) {
                    reactParams.put("memory_type", allParams.get("memory_type"));
                    reactParams.put("memory_arn", allParams.get("memory_arn"));
                    reactParams.put("memory_region", allParams.get("memory_region"));
                    reactParams.put("memory_access_key", allParams.get("memory_access_key"));
                    reactParams.put("memory_secret_key", allParams.get("memory_secret_key"));
                    reactParams.put("memory_session_token", allParams.get("memory_session_token"));

                    // CRITICAL FIX: Pass executor memory ID to executor agents
                    if (executorMemoryId != null) {
                        reactParams.put("executor_memory_id", executorMemoryId);
                        allParams.put("executor_memory_id", executorMemoryId); // Track executor memory ID
                    }

                    // CRITICAL FIX: Use executor memory ID for executor agent calls to maintain shared conversation context
                    if (executorMemoryId != null) {
                        reactParams.put("session_id", executorMemoryId);
                        log.info("DEBUG: Using executor memory ID for executor conversation context: {}", executorMemoryId);
                    } else {
                        // Fallback: check if session_id exists in allParams
                        String existingSessionId = allParams.get("session_id");
                        if (existingSessionId != null) {
                            reactParams.put("session_id", existingSessionId);
                            log.info("DEBUG: Reusing existing session_id for conversation context: {}", existingSessionId);
                        } else {
                            // Last resort: generate new session_id only if none exists
                            String sessionId = "bedrock-session-" + System.currentTimeMillis();
                            reactParams.put("session_id", sessionId);
                            allParams.put("session_id", sessionId);
                            log.info("DEBUG: Generated new session_id: {}", sessionId);
                        }
                    }
                }

                AgentMLInput agentInput = AgentMLInput
                    .AgentMLInputBuilder()
                    .agentId(reActAgentId)
                    .functionName(FunctionName.AGENT)
                    .inputDataset(RemoteInferenceInputDataSet.builder().parameters(reactParams).build())
                    .build();

                // CRITICAL FIX: Set memory field for BedrockAgentCoreMemory
                if ("bedrock_agentcore_memory".equals(allParams.get("memory_type"))) {
                    Map<String, Object> memoryConfig = new HashMap<>();
                    memoryConfig.put("type", allParams.get("memory_type"));
                    memoryConfig.put("memory_arn", allParams.get("memory_arn"));
                    memoryConfig.put("region", allParams.get("memory_region"));

                    // Add credentials if present
                    Map<String, String> credentials = new HashMap<>();
                    if (allParams.get("memory_access_key") != null) {
                        credentials.put("access_key", allParams.get("memory_access_key"));
                    }
                    if (allParams.get("memory_secret_key") != null) {
                        credentials.put("secret_key", allParams.get("memory_secret_key"));
                    }
                    if (allParams.get("memory_session_token") != null) {
                        credentials.put("session_token", allParams.get("memory_session_token"));
                    }
                    if (!credentials.isEmpty()) {
                        memoryConfig.put("credentials", credentials);
                    }

                    agentInput.setMemory(memoryConfig);
                }

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
                        memoryUpdates
                            .put(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD, allParams.get(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD));
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

                    completedSteps.add(String.format("\nStep %d: %s\n", stepsExecuted + 1, stepToExecute));
                    completedSteps.add(String.format("\nStep %d Result: %s\n", stepsExecuted + 1, results.get(STEP_RESULT_FIELD)));

                    if (memory instanceof ConversationIndexMemory) {
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
                    }

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

    @VisibleForTesting
    Map<String, String> parseLLMOutput(Map<String, String> allParams, ModelTensorOutput modelTensorOutput) {
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

    @VisibleForTesting
    String extractJsonFromMarkdown(String response) {
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

    @VisibleForTesting
    void addToolsToPrompt(Map<String, Tool> tools, Map<String, String> allParams) {
        StringBuilder toolsPrompt = new StringBuilder(
            "In this environment, you have access to the tools listed below. Use these tools to create your plan, and do not reference or use any tools not listed here.\n"
        );
        int toolNumber = 0;
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            String toolDescription = entry.getValue().getDescription();
            toolsPrompt.append(String.format("Tool %d - %s: %s\n\n", ++toolNumber, toolName, toolDescription));
        }
        toolsPrompt.append("No other tools are available. Do not invent tools. Only use tools to create the plan.\n\n");
        allParams.put(DEFAULT_PROMPT_TOOLS_FIELD, toolsPrompt.toString());
        populatePrompt(allParams);
        cleanUpResource(tools);
    }

    @VisibleForTesting
    void addSteps(List<String> steps, Map<String, String> allParams, String field) {
        allParams.put(field, String.join(", ", steps));
    }

    @VisibleForTesting
    void saveAndReturnFinalResult(
        Memory memory,
        String parentInteractionId,
        String reactAgentMemoryId,
        String reactParentInteractionId,
        String finalResult,
        String input,
        ActionListener<Object> finalListener
    ) {
        log
            .info(
                "saveAndReturnFinalResult called with memory: {}, parentInteractionId: {}",
                memory != null ? memory.getClass().getSimpleName() : "null",
                parentInteractionId
            );

        if (memory == null) {
            log.warn("Memory is null in saveAndReturnFinalResult, skipping interaction save");
            List<ModelTensors> finalModelTensors = createModelTensors(
                reactAgentMemoryId,
                parentInteractionId,
                reactAgentMemoryId,
                reactParentInteractionId,
                finalResult
            );
            finalListener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
            return;
        }

        if (memory instanceof ConversationIndexMemory) {
            ConversationIndexMemory conversationMemory = (ConversationIndexMemory) memory;
            Map<String, Object> updateContent = new HashMap<>();
            updateContent.put(INTERACTIONS_RESPONSE_FIELD, finalResult);

            if (input != null) {
                updateContent.put(INTERACTIONS_INPUT_FIELD, input);
            }

            conversationMemory.getMemoryManager().updateInteraction(parentInteractionId, updateContent, ActionListener.wrap(res -> {
                List<ModelTensors> finalModelTensors = createModelTensors(
                    conversationMemory.getConversationId(),
                    parentInteractionId,
                    reactAgentMemoryId,
                    reactParentInteractionId,
                    finalResult
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
            }, finalListener::onFailure));
        } else if (memory instanceof BedrockAgentCoreMemory) {
            BedrockAgentCoreMemory bedrockMemory = (BedrockAgentCoreMemory) memory;

            log.info("Saving interaction to BedrockAgentCoreMemory main memory with sessionId: {}", bedrockMemory.getSessionId());

            // Save interaction to Bedrock AgentCore main memory (not executor memory)
            BedrockAgentCoreMemoryRecord record = new BedrockAgentCoreMemoryRecord();
            record.setSessionId(bedrockMemory.getSessionId());  // Use main memory session ID
            record.setContent(input);
            record.setResponse(finalResult);

            bedrockMemory.save(bedrockMemory.getSessionId(), record, ActionListener.wrap(saveResult -> {
                log.info("Successfully saved interaction to BedrockAgentCoreMemory");
                List<ModelTensors> finalModelTensors = createModelTensors(
                    reactAgentMemoryId,
                    parentInteractionId,
                    reactAgentMemoryId,
                    reactParentInteractionId,
                    null // Don't add response in createModelTensors for BedrockAgentCore
                );
                // Add response as separate ModelTensors for BedrockAgentCoreMemory (like ConversationIndexMemory)
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
            }, saveError -> {
                log.error("Failed to save interaction to BedrockAgentCoreMemory", saveError);
                // Still return results even if save fails
                List<ModelTensors> finalModelTensors = createModelTensors(
                    reactAgentMemoryId,
                    parentInteractionId,
                    reactAgentMemoryId,
                    reactParentInteractionId,
                    null
                );
                // Add response even on save failure
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
            }));
        } else {
            // For other memory types, skip saving interaction
            log.info("Skipping interaction save for memory type: {}", memory.getClass().getSimpleName());
            List<ModelTensors> finalModelTensors = createModelTensors(
                reactAgentMemoryId,
                parentInteractionId,
                reactAgentMemoryId,
                reactParentInteractionId,
                finalResult
            );
            finalListener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
        }
    }

    @VisibleForTesting
    static List<ModelTensors> createModelTensors(
        String sessionId,
        String parentInteractionId,
        String reactAgentMemoryId,
        String reactParentInteractionId
    ) {
        return createModelTensors(sessionId, parentInteractionId, reactAgentMemoryId, reactParentInteractionId, null);
    }

    static List<ModelTensors> createModelTensors(
        String sessionId,
        String parentInteractionId,
        String reactAgentMemoryId,
        String reactParentInteractionId,
        String finalResult
    ) {
        List<ModelTensors> modelTensors = new ArrayList<>();
        List<ModelTensor> tensors = new ArrayList<>();

        tensors.add(ModelTensor.builder().name(MLAgentExecutor.MEMORY_ID).result(sessionId).build());
        tensors.add(ModelTensor.builder().name(MLAgentExecutor.PARENT_INTERACTION_ID).result(parentInteractionId).build());

        if (reactAgentMemoryId != null && !reactAgentMemoryId.isEmpty()) {
            tensors.add(ModelTensor.builder().name(EXECUTOR_AGENT_MEMORY_ID_FIELD).result(reactAgentMemoryId).build());
        }

        if (reactParentInteractionId != null && !reactParentInteractionId.isEmpty()) {
            tensors.add(ModelTensor.builder().name(EXECUTOR_AGENT_PARENT_INTERACTION_ID_FIELD).result(reactParentInteractionId).build());
        }

        // Add the actual agent response/result only for BedrockAgentCoreMemory
        // ConversationIndexMemory adds this separately to maintain backward compatibility
        if (finalResult != null && !finalResult.isEmpty()) {
            // Only add response tensor for non-ConversationIndexMemory cases
            // ConversationIndexMemory handles this in the calling method
        }

        modelTensors.add(ModelTensors.builder().mlModelTensors(tensors).build());
        return modelTensors;
    }

    @VisibleForTesting
    Map<String, Object> getTaskUpdates() {
        return taskUpdates;
    }

    private Map<String, String> setupAllParameters(MLAgent mlAgent, Map<String, String> apiParams) {
        Map<String, String> allParams = new HashMap<>();
        allParams.putAll(apiParams);
        allParams.putAll(mlAgent.getParameters());

        setupPromptParameters(allParams);

        // planner prompt for the first call
        usePlannerPromptTemplate(allParams);

        return allParams;
    }

    private String configureMemoryType(MLAgent mlAgent, Map<String, String> allParams) {
        String memoryType = null;

        // Get memory type from agent configuration (with null check)
        if (mlAgent.getMemory() != null) {
            memoryType = mlAgent.getMemory().getType();
            log.debug("Using memory type from agent configuration: {}", memoryType);
        } else {
            log.warn("Agent configuration has no memory specification - this may indicate incomplete agent setup");
        }

        // DEBUG: Log all parameters available in MLPlanExecuteAndReflectAgentRunner
        log.info("DEBUG: MLPlanExecuteAndReflectAgentRunner allParams keys: {}", allParams.keySet());

        // Check if memory parameters indicate BedrockAgentCoreMemory (from internal calls)
        String memoryTypeFromParams = allParams.get("memory_type");
        if ("bedrock_agentcore_memory".equals(memoryTypeFromParams)) {
            memoryType = memoryTypeFromParams;
            log.info("Using BedrockAgentCoreMemory from parameters in internal call");
            cacheBedrockMemoryConfig(mlAgent, allParams);
        } else if (mlAgent.getMemory() != null && "bedrock_agentcore_memory".equals(mlAgent.getMemory().getType())) {
            memoryType = "bedrock_agentcore_memory";
            log.info("DEBUG: Agent has bedrock_agentcore_memory but parameters missing - restoring from cache");
            restoreBedrockMemoryConfig(mlAgent, allParams);
        }
        return memoryType;
    }

    private void cacheBedrockMemoryConfig(MLAgent mlAgent, Map<String, String> allParams) {
        String cacheKey = mlAgent.getName() + "_bedrock_config";
        Map<String, String> bedrockConfig = new HashMap<>();
        bedrockConfig.put("memory_type", "bedrock_agentcore_memory");
        bedrockConfig.put("memory_arn", allParams.get("memory_arn"));
        bedrockConfig.put("memory_region", allParams.get("memory_region"));
        bedrockConfig.put("memory_access_key", allParams.get("memory_access_key"));
        bedrockConfig.put("memory_secret_key", allParams.get("memory_secret_key"));
        bedrockConfig.put("memory_session_token", allParams.get("memory_session_token"));
        bedrockMemoryConfigCache.put(cacheKey, bedrockConfig);
        log.info("DEBUG: Cached BedrockAgentCoreMemory config for agent: {}", mlAgent.getName());
    }

    private void restoreBedrockMemoryConfig(MLAgent mlAgent, Map<String, String> allParams) {
        String cacheKey = mlAgent.getName() + "_bedrock_config";
        Map<String, String> cachedConfig = bedrockMemoryConfigCache.get(cacheKey);

        if (cachedConfig != null) {
            allParams.put("memory_type", cachedConfig.get("memory_type"));
            allParams.put("memory_arn", cachedConfig.get("memory_arn"));
            allParams.put("memory_region", cachedConfig.get("memory_region"));
            allParams.put("memory_access_key", cachedConfig.get("memory_access_key"));
            allParams.put("memory_secret_key", cachedConfig.get("memory_secret_key"));
            allParams.put("memory_session_token", cachedConfig.get("memory_session_token"));
            log.info("DEBUG: Restored BedrockAgentCoreMemory parameters to allParams for subsequent calls");
        } else {
            log.info("DEBUG: No cached BedrockAgentCoreMemory config found - subsequent call will fail");
        }
    }
}
