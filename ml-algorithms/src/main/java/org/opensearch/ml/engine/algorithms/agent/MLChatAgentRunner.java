/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.AI_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.common.utils.ToolUtils.filterToolOutput;
import static org.opensearch.ml.common.utils.ToolUtils.getToolName;
import static org.opensearch.ml.common.utils.ToolUtils.parseResponse;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DISABLE_TRACE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTIONS_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_CHAT_HISTORY_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_SUFFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.RESPONSE_FORMAT_INSTRUCTION;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESPONSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.VERBOSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.cleanUpResource;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.constructToolParams;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getCurrentDateTime;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMcpToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMessageHistoryLimit;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getToolNames;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.outputToOutputString;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.parseLLMOutput;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.substitute;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.CHAT_HISTORY_PREFIX;
import static org.opensearch.ml.engine.tools.ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY;

import java.security.PrivilegedActionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.memory.Message;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.ml.engine.function_calling.LLMMessage;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.ml.repackage.com.google.common.collect.Lists;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
public class MLChatAgentRunner implements MLAgentRunner {

    public static final String SESSION_ID = "session_id";
    public static final String LLM_TOOL_PROMPT_PREFIX = "LanguageModelTool.prompt_prefix";
    public static final String LLM_TOOL_PROMPT_SUFFIX = "LanguageModelTool.prompt_suffix";
    public static final String TOOLS = "tools";
    public static final String TOOL_DESCRIPTIONS = "tool_descriptions";
    public static final String TOOL_NAMES = "tool_names";
    public static final String OS_INDICES = "opensearch_indices";
    public static final String EXAMPLES = "examples";
    public static final String SCRATCHPAD = "scratchpad";
    public static final String CHAT_HISTORY = "chat_history";
    public static final String NEW_CHAT_HISTORY = "_chat_history";
    public static final String CONTEXT = "context";
    public static final String PROMPT = "prompt";
    public static final String LLM_RESPONSE = "llm_response";
    public static final String MAX_ITERATION = "max_iteration";
    public static final String THOUGHT = "thought";
    public static final String ACTION = "action";
    public static final String ACTION_INPUT = "action_input";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String THOUGHT_RESPONSE = "thought_response";
    public static final String INTERACTIONS = "_interactions";
    public static final String INTERACTION_TEMPLATE_TOOL_RESPONSE = "interaction_template.tool_response";
    public static final String CHAT_HISTORY_QUESTION_TEMPLATE = "chat_history_template.user_question";
    public static final String CHAT_HISTORY_RESPONSE_TEMPLATE = "chat_history_template.ai_response";
    public static final String CHAT_HISTORY_MESSAGE_PREFIX = "${_chat_history.message.";
    public static final String LLM_INTERFACE = "_llm_interface";
    public static final String INJECT_DATETIME_FIELD = "inject_datetime";
    public static final String DATETIME_FORMAT_FIELD = "datetime_format";
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";

    private static final String DEFAULT_MAX_ITERATIONS = "10";
    private static final String MAX_ITERATIONS_MESSAGE = "Agent reached maximum iterations (%d) without completing the task";

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;
    private StreamingWrapper streamingWrapper;

    public MLChatAgentRunner(
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
    public void run(MLAgent mlAgent, Map<String, String> inputParams, ActionListener<Object> listener, TransportChannel channel) {
        this.streamingWrapper = new StreamingWrapper(channel, client);

        Map<String, String> params = new HashMap<>();
        if (mlAgent.getParameters() != null) {
            params.putAll(mlAgent.getParameters());
            for (String key : mlAgent.getParameters().keySet()) {
                if (key.startsWith("_")) {
                    params.put(key, mlAgent.getParameters().get(key));
                }
            }
        }

        params.putAll(inputParams);

        String llmInterface = params.get(LLM_INTERFACE);
        FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
        if (functionCalling != null) {
            functionCalling.configure(params);
        }

        String memoryType = mlAgent.getMemory().getType();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String appType = mlAgent.getAppType();
        String title = params.get(MLAgentExecutor.QUESTION);
        String chatHistoryPrefix = params.getOrDefault(PROMPT_CHAT_HISTORY_PREFIX, CHAT_HISTORY_PREFIX);
        String chatHistoryQuestionTemplate = params.get(CHAT_HISTORY_QUESTION_TEMPLATE);
        String chatHistoryResponseTemplate = params.get(CHAT_HISTORY_RESPONSE_TEMPLATE);
        int messageHistoryLimit = getMessageHistoryLimit(params);

        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap.get(memoryType);
        conversationIndexMemoryFactory.create(title, memoryId, appType, ActionListener.<ConversationIndexMemory>wrap(memory -> {
            // TODO: call runAgent directly if messageHistoryLimit == 0
            memory.getMessages(ActionListener.<List<Interaction>>wrap(r -> {
                List<Message> messageList = new ArrayList<>();
                for (Interaction next : r) {
                    String question = next.getInput();
                    String response = next.getResponse();
                    // As we store the conversation with empty response first and then update when have final answer,
                    // filter out those in-flight requests when run in parallel
                    if (Strings.isNullOrEmpty(response)) {
                        continue;
                    }
                    messageList
                        .add(
                            ConversationIndexMessage
                                .conversationIndexMessageBuilder()
                                .sessionId(memory.getConversationId())
                                .question(question)
                                .response(response)
                                .build()
                        );
                }
                if (!messageList.isEmpty()) {
                    if (chatHistoryQuestionTemplate == null) {
                        StringBuilder chatHistoryBuilder = new StringBuilder();
                        chatHistoryBuilder.append(chatHistoryPrefix);
                        for (Message message : messageList) {
                            chatHistoryBuilder.append(message.toString()).append("\n");
                        }
                        params.put(CHAT_HISTORY, chatHistoryBuilder.toString());

                        // required for MLChatAgentRunnerTest.java, it requires chatHistory to be added to input params to validate
                        inputParams.put(CHAT_HISTORY, chatHistoryBuilder.toString());
                    } else {
                        List<String> chatHistory = new ArrayList<>();
                        for (Message message : messageList) {
                            Map<String, String> messageParams = new HashMap<>();
                            messageParams.put("question", processTextDoc(((ConversationIndexMessage) message).getQuestion()));

                            StringSubstitutor substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                            String chatQuestionMessage = substitutor.replace(chatHistoryQuestionTemplate);
                            chatHistory.add(chatQuestionMessage);

                            messageParams.clear();
                            messageParams.put("response", processTextDoc(((ConversationIndexMessage) message).getResponse()));
                            substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                            String chatResponseMessage = substitutor.replace(chatHistoryResponseTemplate);
                            chatHistory.add(chatResponseMessage);
                        }
                        params.put(CHAT_HISTORY, String.join(", ", chatHistory) + ", ");
                        params.put(NEW_CHAT_HISTORY, String.join(", ", chatHistory) + ", ");

                        // required for MLChatAgentRunnerTest.java, it requires chatHistory to be added to input params to validate
                        inputParams.put(CHAT_HISTORY, String.join(", ", chatHistory) + ", ");
                    }
                }

                runAgent(mlAgent, params, listener, memory, memory.getConversationId(), functionCalling);
            }, e -> {
                log.error("Failed to get chat history", e);
                listener.onFailure(e);
            }), messageHistoryLimit);
        }, listener::onFailure));
    }

    private void runAgent(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        Memory memory,
        String sessionId,
        FunctionCalling functionCalling
    ) {
        log.debug("AG-UI Debug: runAgent called with params keys: {}", params.keySet());

        // Check if this is an AG-UI request with tool call results
        String aguiToolCallResults = params.get("agui_tool_call_results");
        log.debug("AG-UI Debug: agui_tool_call_results = {}", aguiToolCallResults != null ? "present" : "null");
        if (aguiToolCallResults != null && !aguiToolCallResults.isEmpty()) {
            log.info("AG-UI Debug: Processing tool call results");
            // Process tool call results from frontend
            processAGUIToolResults(mlAgent, params, listener, memory, sessionId, functionCalling, aguiToolCallResults);
            return;
        }

        // NEW UNIFIED APPROACH: Always combine frontend and backend tools
        log.info("AG-UI Debug: Using unified tool approach - combining frontend and backend tools");

        // Parse frontend tools if present
        String aguiTools = params.get("agui_tools");
        List<Map<String, Object>> frontendTools = new ArrayList<>();
        if (aguiTools != null && !aguiTools.isEmpty() && !aguiTools.trim().equals("[]")) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {
                }.getType();
                List<Map<String, Object>> parsedTools = gson.fromJson(aguiTools, listType);
                if (parsedTools != null) {
                    frontendTools.addAll(parsedTools);
                }
                log.info("AG-UI Debug: Parsed {} frontend tools", frontendTools.size());
            } catch (Exception e) {
                log.warn("AG-UI Debug: Failed to parse frontend tools: {}", e.getMessage());
            }
        }

        // Process with unified tools (both frontend and backend)
        processUnifiedTools(mlAgent, params, listener, memory, sessionId, functionCalling, frontendTools);
    }

    private void runReAct(
        LLMSpec llm,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        Map<String, String> parameters,
        Memory memory,
        String sessionId,
        String tenantId,
        ActionListener<Object> listener,
        FunctionCalling functionCalling
    ) {
        Map<String, String> tmpParameters = constructLLMParams(llm, parameters);
        String prompt = constructLLMPrompt(tools, tmpParameters);
        tmpParameters.put(PROMPT, prompt);
        final String finalPrompt = prompt;

        String question = tmpParameters.get(MLAgentExecutor.QUESTION);
        String parentInteractionId = tmpParameters.get(MLAgentExecutor.PARENT_INTERACTION_ID);
        boolean verbose = Boolean.parseBoolean(tmpParameters.getOrDefault(VERBOSE, "false"));
        boolean traceDisabled = tmpParameters.containsKey(DISABLE_TRACE) && Boolean.parseBoolean(tmpParameters.get(DISABLE_TRACE));

        // Create root interaction.
        ConversationIndexMemory conversationIndexMemory = (ConversationIndexMemory) memory;

        // Trace number
        AtomicInteger traceNumber = new AtomicInteger(0);

        AtomicReference<StepListener<MLTaskResponse>> lastLlmListener = new AtomicReference<>();
        AtomicReference<String> lastThought = new AtomicReference<>();
        AtomicReference<String> lastAction = new AtomicReference<>();
        AtomicReference<String> lastActionInput = new AtomicReference<>();
        AtomicReference<String> lastToolSelectionResponse = new AtomicReference<>();
        Map<String, Object> additionalInfo = new ConcurrentHashMap<>();
        Map<String, String> lastToolParams = new ConcurrentHashMap<>();

        StepListener firstListener = new StepListener<MLTaskResponse>();
        lastLlmListener.set(firstListener);
        StepListener<?> lastStepListener = firstListener;

        StringBuilder scratchpadBuilder = new StringBuilder();
        List<String> interactions = new CopyOnWriteArrayList<>();

        StringSubstitutor tmpSubstitutor = new StringSubstitutor(Map.of(SCRATCHPAD, scratchpadBuilder.toString()), "${parameters.", "}");
        AtomicReference<String> newPrompt = new AtomicReference<>(tmpSubstitutor.replace(prompt));
        tmpParameters.put(PROMPT, newPrompt.get());
        List<ModelTensors> traceTensors = createModelTensors(sessionId, parentInteractionId);
        int maxIterations = Integer.parseInt(tmpParameters.getOrDefault(MAX_ITERATION, DEFAULT_MAX_ITERATIONS));
        for (int i = 0; i < maxIterations; i++) {
            int finalI = i;
            StepListener<?> nextStepListener = (i == maxIterations - 1) ? null : new StepListener<>();

            lastStepListener.whenComplete(output -> {
                StringBuilder sessionMsgAnswerBuilder = new StringBuilder();
                if (finalI % 2 == 0) {
                    MLTaskResponse llmResponse = (MLTaskResponse) output;
                    ModelTensorOutput tmpModelTensorOutput = (ModelTensorOutput) llmResponse.getOutput();
                    List<String> llmResponsePatterns = gson.fromJson(tmpParameters.get("llm_response_pattern"), List.class);
                    Map<String, String> modelOutput = parseLLMOutput(
                        parameters,
                        tmpModelTensorOutput,
                        llmResponsePatterns,
                        tools.keySet(),
                        interactions,
                        functionCalling
                    );

                    streamingWrapper.fixInteractionRole(interactions);
                    String thought = String.valueOf(modelOutput.get(THOUGHT));
                    String toolCallId = String.valueOf(modelOutput.get("tool_call_id"));
                    String action = String.valueOf(modelOutput.get(ACTION));
                    String actionInput = String.valueOf(modelOutput.get(ACTION_INPUT));
                    String thoughtResponse = modelOutput.get(THOUGHT_RESPONSE);
                    String finalAnswer = modelOutput.get(FINAL_ANSWER);

                    if (finalAnswer != null) {
                        finalAnswer = finalAnswer.trim();
                        sendFinalAnswer(
                            sessionId,
                            listener,
                            question,
                            parentInteractionId,
                            verbose,
                            traceDisabled,
                            traceTensors,
                            conversationIndexMemory,
                            traceNumber,
                            additionalInfo,
                            finalAnswer
                        );
                        cleanUpResource(tools);
                        return;
                    }

                    sessionMsgAnswerBuilder.append(thought);
                    lastThought.set(thought);
                    lastAction.set(action);
                    lastActionInput.set(actionInput);
                    lastToolSelectionResponse.set(thoughtResponse);

                    traceTensors
                        .add(
                            ModelTensors
                                .builder()
                                .mlModelTensors(List.of(ModelTensor.builder().name("response").result(thoughtResponse).build()))
                                .build()
                        );

                    saveTraceData(
                        conversationIndexMemory,
                        memory.getType(),
                        question,
                        thoughtResponse,
                        sessionId,
                        traceDisabled,
                        parentInteractionId,
                        traceNumber,
                        "LLM"
                    );

                    if (nextStepListener == null) {
                        handleMaxIterationsReached(
                            sessionId,
                            listener,
                            question,
                            parentInteractionId,
                            verbose,
                            traceDisabled,
                            traceTensors,
                            conversationIndexMemory,
                            traceNumber,
                            additionalInfo,
                            lastThought,
                            maxIterations,
                            tools
                        );
                        return;
                    }

                    if (tools.containsKey(action)) {
                        // Check if this is a frontend tool
                        Tool tool = tools.get(action);
                        boolean isFrontendTool = false;
                        if (tool.getAttributes() != null && "frontend".equals(tool.getAttributes().get("source"))) {
                            isFrontendTool = true;
                        }

                        log
                            .info(
                                "AG-UI: Tool execution request - action: {}, isFrontendTool: {}, toolAttributes: {}",
                                action,
                                isFrontendTool,
                                tool.getAttributes()
                            );

                        if (isFrontendTool) {
                            // Frontend tools: generate AG-UI events immediately and return them
                            log
                                .info(
                                    "AG-UI: Detected frontend tool call - generating AG-UI events for: {} with input: {}",
                                    action,
                                    actionInput
                                );

                            // Create a ModelTensorOutput with tool call events for AG-UI processing
                            Map<String, Object> toolCallData = Map
                                .of(
                                    "tool_calls",
                                    List
                                        .of(
                                            Map
                                                .of(
                                                    "id",
                                                    toolCallId,
                                                    "type",
                                                    "function",
                                                    "function",
                                                    Map.of("name", action, "arguments", actionInput)
                                                )
                                        )
                                );

                            ModelTensor responseTensor = ModelTensor.builder().name("response").dataAsMap(toolCallData).build();

                            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(List.of(responseTensor)).build();

                            ModelTensorOutput frontendToolResponse = ModelTensorOutput
                                .builder()
                                .mlModelOutputs(List.of(modelTensors))
                                .build();

                            log.info("AG-UI: Generated ModelTensorOutput with frontend tool call events for tool: {}", action);
                            log.info("AG-UI: Bypassing ReAct loop and returning frontend tool calls directly to MLAGUIAgentRunner");

                            // Exit ReAct loop early and return frontend tool calls directly to main listener (MLAGUIAgentRunner)
                            listener.onResponse(frontendToolResponse);
                            return;
                        } else {
                            // Handle backend tool normally
                            Map<String, String> toolParams = constructToolParams(
                                tools,
                                toolSpecMap,
                                question,
                                lastActionInput,
                                action,
                                actionInput
                            );
                            lastToolParams.clear();
                            lastToolParams.putAll(toolParams);
                            runTool(
                                tools,
                                toolSpecMap,
                                tmpParameters,
                                (ActionListener<Object>) nextStepListener,
                                action,
                                actionInput,
                                toolParams,
                                interactions,
                                toolCallId,
                                functionCalling
                            );
                        }

                    } else {
                        String res = String.format(Locale.ROOT, "Failed to run the tool %s which is unsupported.", action);
                        StringSubstitutor substitutor = new StringSubstitutor(
                            Map.of(SCRATCHPAD, scratchpadBuilder.toString()),
                            "${parameters.",
                            "}"
                        );
                        newPrompt.set(substitutor.replace(finalPrompt));
                        tmpParameters.put(PROMPT, newPrompt.get());
                        ((ActionListener<Object>) nextStepListener).onResponse(res);
                    }
                } else {
                    Object filteredOutput = filterToolOutput(lastToolParams, output);
                    addToolOutputToAddtionalInfo(toolSpecMap, lastAction, additionalInfo, filteredOutput);

                    String toolResponse = constructToolResponse(
                        tmpParameters,
                        lastAction,
                        lastActionInput,
                        lastToolSelectionResponse,
                        filteredOutput
                    );
                    scratchpadBuilder.append(toolResponse).append("\n\n");

                    saveTraceData(
                        conversationIndexMemory,
                        "ReAct",
                        lastActionInput.get(),
                        outputToOutputString(filteredOutput),
                        sessionId,
                        traceDisabled,
                        parentInteractionId,
                        traceNumber,
                        lastAction.get()
                    );

                    StringSubstitutor substitutor = new StringSubstitutor(Map.of(SCRATCHPAD, scratchpadBuilder), "${parameters.", "}");
                    newPrompt.set(substitutor.replace(finalPrompt));
                    tmpParameters.put(PROMPT, newPrompt.get());
                    if (!interactions.isEmpty()) {
                        tmpParameters.put(INTERACTIONS, ", " + String.join(", ", interactions));
                    }

                    sessionMsgAnswerBuilder.append(outputToOutputString(filteredOutput));
                    streamingWrapper.sendToolResponse(outputToOutputString(filteredOutput), sessionId, parentInteractionId);
                    traceTensors
                        .add(
                            ModelTensors
                                .builder()
                                .mlModelTensors(
                                    Collections
                                        .singletonList(
                                            ModelTensor.builder().name("response").result(sessionMsgAnswerBuilder.toString()).build()
                                        )
                                )
                                .build()
                        );

                    if (finalI == maxIterations - 1) {
                        handleMaxIterationsReached(
                            sessionId,
                            listener,
                            question,
                            parentInteractionId,
                            verbose,
                            traceDisabled,
                            traceTensors,
                            conversationIndexMemory,
                            traceNumber,
                            additionalInfo,
                            lastThought,
                            maxIterations,
                            tools
                        );
                        return;
                    }
                    ActionRequest request = streamingWrapper.createPredictionRequest(llm, tmpParameters, tenantId);
                    streamingWrapper.executeRequest(request, (ActionListener<MLTaskResponse>) nextStepListener);
                }
            }, e -> {
                log.error("Failed to run chat agent", e);
                listener.onFailure(e);
            });
            if (nextStepListener != null) {
                lastStepListener = nextStepListener;
            }
        }

        ActionRequest request = streamingWrapper.createPredictionRequest(llm, tmpParameters, tenantId);
        streamingWrapper.executeRequest(request, firstListener);
    }

    private static List<ModelTensors> createFinalAnswerTensors(List<ModelTensors> sessionId, List<ModelTensor> lastThought) {
        List<ModelTensors> finalModelTensors = sessionId;
        finalModelTensors.add(ModelTensors.builder().mlModelTensors(lastThought).build());
        return finalModelTensors;
    }

    private static String constructToolResponse(
        Map<String, String> tmpParameters,
        AtomicReference<String> lastAction,
        AtomicReference<String> lastActionInput,
        AtomicReference<String> lastToolSelectionResponse,
        Object output
    ) throws PrivilegedActionException {
        String toolResponse = tmpParameters.get(TOOL_RESPONSE);
        StringSubstitutor toolResponseSubstitutor = new StringSubstitutor(
            Map
                .of(
                    "llm_tool_selection_response",
                    lastToolSelectionResponse.get(),
                    "tool_name",
                    lastAction.get(),
                    "tool_input",
                    lastActionInput.get(),
                    "observation",
                    outputToOutputString(output)
                ),
            "${parameters.",
            "}"
        );
        toolResponse = toolResponseSubstitutor.replace(toolResponse);
        return toolResponse;
    }

    private static void addToolOutputToAddtionalInfo(
        Map<String, MLToolSpec> toolSpecMap,
        AtomicReference<String> lastAction,
        Map<String, Object> additionalInfo,
        Object output
    ) throws PrivilegedActionException {
        MLToolSpec toolSpec = toolSpecMap.get(lastAction.get());
        if (toolSpec != null && toolSpec.isIncludeOutputInAgentResponse()) {
            String outputString = outputToOutputString(output);
            String toolOutputKey = String.format("%s.output", getToolName(toolSpec));
            if (additionalInfo.get(toolOutputKey) != null) {
                List<String> list = (List<String>) additionalInfo.get(toolOutputKey);
                list.add(outputString);
            } else {
                additionalInfo.put(toolOutputKey, Lists.newArrayList(outputString));
            }
        }
    }

    private static void runTool(
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        Map<String, String> tmpParameters,
        ActionListener<Object> nextStepListener,
        String action,
        String actionInput,
        Map<String, String> toolParams,
        List<String> interactions,
        String toolCallId,
        FunctionCalling functionCalling
    ) {
        if (tools.get(action).validate(toolParams)) {
            try {
                String finalAction = action;
                ActionListener<Object> toolListener = ActionListener.wrap(r -> {
                    if (functionCalling != null) {
                        String outputResponse = parseResponse(filterToolOutput(toolParams, r));
                        List<Map<String, Object>> toolResults = List
                            .of(Map.of(TOOL_CALL_ID, toolCallId, TOOL_RESULT, Map.of("text", outputResponse)));
                        List<LLMMessage> llmMessages = functionCalling.supply(toolResults);
                        // TODO: support multiple tool calls at the same time so that multiple LLMMessages can be generated here
                        interactions.add(llmMessages.getFirst().getResponse());
                    } else {
                        interactions
                            .add(
                                substitute(
                                    tmpParameters.get(INTERACTION_TEMPLATE_TOOL_RESPONSE),
                                    Map.of(TOOL_CALL_ID, toolCallId, "tool_response", processTextDoc(StringUtils.toJson(r))),
                                    INTERACTIONS_PREFIX
                                )
                            );
                    }
                    nextStepListener.onResponse(r);
                }, e -> {
                    interactions
                        .add(
                            substitute(
                                tmpParameters.get(INTERACTION_TEMPLATE_TOOL_RESPONSE),
                                Map.of(TOOL_CALL_ID, toolCallId, "tool_response", "Tool " + action + " failed: " + e.getMessage()),
                                INTERACTIONS_PREFIX
                            )
                        );
                    nextStepListener
                        .onResponse(
                            String
                                .format(
                                    Locale.ROOT,
                                    "Failed to run the tool %s with the error message %s.",
                                    finalAction,
                                    e.getMessage().replaceAll("\\n", "\n")
                                )
                        );
                });
                if (tools.get(action) instanceof MLModelTool) {
                    Map<String, String> llmToolTmpParameters = new HashMap<>();
                    llmToolTmpParameters.putAll(tmpParameters);
                    llmToolTmpParameters.putAll(toolSpecMap.get(action).getParameters());
                    llmToolTmpParameters.put(MLAgentExecutor.QUESTION, actionInput);
                    tools.get(action).run(llmToolTmpParameters, toolListener); // run tool
                    updateParametersAcrossTools(tmpParameters, llmToolTmpParameters);
                } else {
                    Map<String, String> parameters = new HashMap<>();
                    parameters.putAll(tmpParameters);
                    parameters.putAll(toolParams);
                    tools.get(action).run(parameters, toolListener); // run tool
                    updateParametersAcrossTools(tmpParameters, parameters);
                }
            } catch (Exception e) {
                log.error("Failed to run tool {}", action, e);
                nextStepListener
                    .onResponse(String.format(Locale.ROOT, "Failed to run the tool %s with the error message %s.", action, e.getMessage()));
            }
        } else { // TODO: add failure to interaction to let LLM regenerate ?
            String res = String.format(Locale.ROOT, "Failed to run the tool %s due to wrong input %s.", action, actionInput);
            nextStepListener.onResponse(res);
        }
    }

    /**
     * In each tool runs, it copies agent parameters, which is tmpParameters into a new set of parameter llmToolTmpParameters,
     * after the tool runs, normally llmToolTmpParameters will be discarded, but for some special parameters like SCRATCHPAD_NOTES_KEY,
     * some new llmToolTmpParameters produced by the tool run can opt to be copied back to tmpParameters to share across tools in the same interaction
     * @param tmpParameters
     * @param llmToolTmpParameters
     */
    private static void updateParametersAcrossTools(Map<String, String> tmpParameters, Map<String, String> llmToolTmpParameters) {
        // update the tmpParameters if the tool run produce new scratch pad
        if (llmToolTmpParameters.containsKey(SCRATCHPAD_NOTES_KEY) && llmToolTmpParameters.get(SCRATCHPAD_NOTES_KEY) != "[]") {
            tmpParameters.put(SCRATCHPAD_NOTES_KEY, llmToolTmpParameters.getOrDefault(SCRATCHPAD_NOTES_KEY, "[]"));
        }
    }

    public static void saveTraceData(
        ConversationIndexMemory conversationIndexMemory,
        String memory,
        String question,
        String thoughtResponse,
        String sessionId,
        boolean traceDisabled,
        String parentInteractionId,
        AtomicInteger traceNumber,
        String origin
    ) {
        if (conversationIndexMemory != null) {
            ConversationIndexMessage msgTemp = ConversationIndexMessage
                .conversationIndexMessageBuilder()
                .type(memory)
                .question(question)
                .response(thoughtResponse)
                .finalAnswer(false)
                .sessionId(sessionId)
                .build();
            if (!traceDisabled) {
                conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), origin);
            }
        }
    }

    private void sendFinalAnswer(
        String sessionId,
        ActionListener<Object> listener,
        String question,
        String parentInteractionId,
        boolean verbose,
        boolean traceDisabled,
        List<ModelTensors> cotModelTensors,
        ConversationIndexMemory conversationIndexMemory,
        AtomicInteger traceNumber,
        Map<String, Object> additionalInfo,
        String finalAnswer
    ) {
        // Send completion chunk for streaming
        streamingWrapper.sendCompletionChunk(sessionId, parentInteractionId);

        if (conversationIndexMemory != null) {
            String copyOfFinalAnswer = finalAnswer;
            ActionListener saveTraceListener = ActionListener.wrap(r -> {
                conversationIndexMemory
                    .getMemoryManager()
                    .updateInteraction(
                        parentInteractionId,
                        Map.of(AI_RESPONSE_FIELD, copyOfFinalAnswer, ADDITIONAL_INFO_FIELD, additionalInfo),
                        ActionListener.wrap(res -> {
                            returnFinalResponse(
                                sessionId,
                                listener,
                                parentInteractionId,
                                verbose,
                                cotModelTensors,
                                additionalInfo,
                                copyOfFinalAnswer
                            );
                        }, e -> { listener.onFailure(e); })
                    );
            }, e -> { listener.onFailure(e); });
            saveMessage(
                conversationIndexMemory,
                question,
                finalAnswer,
                sessionId,
                parentInteractionId,
                traceNumber,
                true,
                traceDisabled,
                saveTraceListener
            );
        } else {
            streamingWrapper
                .sendFinalResponse(sessionId, listener, parentInteractionId, verbose, cotModelTensors, additionalInfo, finalAnswer);
        }
    }

    public static List<ModelTensors> createModelTensors(String sessionId, String parentInteractionId) {
        List<ModelTensors> cotModelTensors = new ArrayList<>();

        cotModelTensors
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
        return cotModelTensors;
    }

    private static String constructLLMPrompt(Map<String, Tool> tools, Map<String, String> tmpParameters) {
        String prompt = tmpParameters.getOrDefault(PROMPT, PromptTemplate.PROMPT_TEMPLATE);
        StringSubstitutor promptSubstitutor = new StringSubstitutor(tmpParameters, "${parameters.", "}");
        prompt = promptSubstitutor.replace(prompt);
        prompt = AgentUtils.addPrefixSuffixToPrompt(tmpParameters, prompt);
        prompt = AgentUtils.addToolsToPrompt(tools, tmpParameters, getToolNames(tools), prompt);
        prompt = AgentUtils.addIndicesToPrompt(tmpParameters, prompt);
        prompt = AgentUtils.addExamplesToPrompt(tmpParameters, prompt);
        prompt = AgentUtils.addChatHistoryToPrompt(tmpParameters, prompt);
        prompt = AgentUtils.addContextToPrompt(tmpParameters, prompt);
        return prompt;
    }

    @VisibleForTesting
    static Map<String, String> constructLLMParams(LLMSpec llm, Map<String, String> parameters) {
        Map<String, String> tmpParameters = new HashMap<>();
        if (llm.getParameters() != null) {
            tmpParameters.putAll(llm.getParameters());
        }
        tmpParameters.putAll(parameters);
        if (!tmpParameters.containsKey("stop")) {
            tmpParameters.put("stop", gson.toJson(new String[] { "\nObservation:", "\n\tObservation:" }));
        }
        if (!tmpParameters.containsKey("stop_sequences")) {
            tmpParameters
                .put(
                    "stop_sequences",
                    gson
                        .toJson(
                            new String[] {
                                "\n\nHuman:",
                                "\nObservation:",
                                "\n\tObservation:",
                                "\nObservation",
                                "\n\tObservation",
                                "\n\nQuestion" }
                        )
                );
        }

        boolean injectDate = Boolean.parseBoolean(tmpParameters.getOrDefault(INJECT_DATETIME_FIELD, "false"));
        if (injectDate) {
            String dateFormat = tmpParameters.get(DATETIME_FORMAT_FIELD);
            String currentDateTime = getCurrentDateTime(dateFormat);
            // If system_prompt exists, inject datetime into it
            if (tmpParameters.containsKey(SYSTEM_PROMPT_FIELD)) {
                String systemPrompt = tmpParameters.get(SYSTEM_PROMPT_FIELD);
                systemPrompt = systemPrompt + "\n\n" + currentDateTime;
                tmpParameters.put(SYSTEM_PROMPT_FIELD, systemPrompt);
            } else {
                // Otherwise inject datetime into prompt_prefix
                String promptPrefix = tmpParameters.getOrDefault(PROMPT_PREFIX, PromptTemplate.PROMPT_TEMPLATE_PREFIX);
                promptPrefix = promptPrefix + "\n\n" + currentDateTime;
                tmpParameters.put(PROMPT_PREFIX, promptPrefix);
            }
        }

        tmpParameters.putIfAbsent(PROMPT_PREFIX, PromptTemplate.PROMPT_TEMPLATE_PREFIX);
        tmpParameters.putIfAbsent(PROMPT_SUFFIX, PromptTemplate.PROMPT_TEMPLATE_SUFFIX);
        tmpParameters.putIfAbsent(RESPONSE_FORMAT_INSTRUCTION, PromptTemplate.PROMPT_FORMAT_INSTRUCTION);
        tmpParameters.putIfAbsent(TOOL_RESPONSE, PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE);
        return tmpParameters;
    }

    public static void returnFinalResponse(
        String sessionId,
        ActionListener<Object> listener,
        String parentInteractionId,
        boolean verbose,
        List<ModelTensors> cotModelTensors, // AtomicBoolean getFinalAnswer,
        Map<String, Object> additionalInfo,
        String finalAnswer2
    ) {
        cotModelTensors
            .add(
                ModelTensors.builder().mlModelTensors(List.of(ModelTensor.builder().name("response").result(finalAnswer2).build())).build()
            );

        List<ModelTensors> finalModelTensors = createFinalAnswerTensors(
            createModelTensors(sessionId, parentInteractionId),
            List
                .of(
                    ModelTensor
                        .builder()
                        .name("response")
                        .dataAsMap(ImmutableMap.of("response", finalAnswer2, ADDITIONAL_INFO_FIELD, additionalInfo))
                        .build()
                )
        );
        log
            .info(
                "AG-UI: ReAct loop completing, returning final result. Verbose: {}, cotModelTensors size: {}, finalModelTensors size: {}",
                verbose,
                cotModelTensors != null ? cotModelTensors.size() : 0,
                finalModelTensors != null ? finalModelTensors.size() : 0
            );

        if (verbose) {
            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(cotModelTensors).build());
        } else {
            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
        }
    }

    private void handleMaxIterationsReached(
        String sessionId,
        ActionListener<Object> listener,
        String question,
        String parentInteractionId,
        boolean verbose,
        boolean traceDisabled,
        List<ModelTensors> traceTensors,
        ConversationIndexMemory conversationIndexMemory,
        AtomicInteger traceNumber,
        Map<String, Object> additionalInfo,
        AtomicReference<String> lastThought,
        int maxIterations,
        Map<String, Tool> tools
    ) {
        String incompleteResponse = (lastThought.get() != null && !lastThought.get().isEmpty() && !"null".equals(lastThought.get()))
            ? String.format("%s. Last thought: %s", String.format(MAX_ITERATIONS_MESSAGE, maxIterations), lastThought.get())
            : String.format(MAX_ITERATIONS_MESSAGE, maxIterations);
        sendFinalAnswer(
            sessionId,
            listener,
            question,
            parentInteractionId,
            verbose,
            traceDisabled,
            traceTensors,
            conversationIndexMemory,
            traceNumber,
            additionalInfo,
            incompleteResponse
        );
        cleanUpResource(tools);
    }

    private void saveMessage(
        ConversationIndexMemory memory,
        String question,
        String finalAnswer,
        String sessionId,
        String parentInteractionId,
        AtomicInteger traceNumber,
        boolean isFinalAnswer,
        boolean traceDisabled,
        ActionListener listener
    ) {
        ConversationIndexMessage msgTemp = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type(memory.getType())
            .question(question)
            .response(finalAnswer)
            .finalAnswer(isFinalAnswer)
            .sessionId(sessionId)
            .build();
        if (traceDisabled) {
            listener.onResponse(true);
        } else {
            memory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), "LLM", listener);
        }
    }

    /**
     * Process unified tools - combines frontend and backend tools for LLM visibility
     */
    private void processUnifiedTools(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        Memory memory,
        String sessionId,
        FunctionCalling functionCalling,
        List<Map<String, Object>> frontendTools
    ) {
        log.info("AG-UI Debug: Processing unified tools - {} frontend tools", frontendTools.size());

        // Always get backend tools
        List<MLToolSpec> backendToolSpecs = getMlToolSpecs(mlAgent, params);

        // Handle backend tool loading with MCP tools
        getMcpToolSpecs(mlAgent, client, sdkClient, encryptor, ActionListener.wrap(mcpTools -> {
            // Add MCP tools to backend tools
            backendToolSpecs.addAll(mcpTools);

            // Create backend tools map
            Map<String, Tool> backendToolsMap = new HashMap<>();
            Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
            createTools(toolFactories, params, backendToolSpecs, backendToolsMap, toolSpecMap, mlAgent);

            log.info("AG-UI Debug: Created {} backend tools", backendToolsMap.size());

            // Create unified tool list for function calling (frontend + backend)
            processUnifiedToolsWithBackend(
                mlAgent,
                params,
                listener,
                memory,
                sessionId,
                functionCalling,
                frontendTools,
                backendToolsMap,
                toolSpecMap
            );
        }, e -> {
            // Even if MCP tools fail, continue with base backend tools
            log.warn("Failed to get MCP tools, continuing with base backend tools only", e);

            Map<String, Tool> backendToolsMap = new HashMap<>();
            Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
            createTools(toolFactories, params, backendToolSpecs, backendToolsMap, toolSpecMap, mlAgent);

            log.info("AG-UI Debug: Created {} backend tools (no MCP)", backendToolsMap.size());

            processUnifiedToolsWithBackend(
                mlAgent,
                params,
                listener,
                memory,
                sessionId,
                functionCalling,
                frontendTools,
                backendToolsMap,
                toolSpecMap
            );
        }));
    }

    /**
     * Process unified tools with both frontend and backend tools ready
     */
    private void processUnifiedToolsWithBackend(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        Memory memory,
        String sessionId,
        FunctionCalling functionCalling,
        List<Map<String, Object>> frontendTools,
        Map<String, Tool> backendToolsMap,
        Map<String, MLToolSpec> toolSpecMap
    ) {
        log.info("AG-UI Debug: Combining {} frontend + {} backend tools for LLM", frontendTools.size(), backendToolsMap.size());

        // Create unified tool map by adding frontend tools as Tool objects
        Map<String, Tool> unifiedToolsMap = new HashMap<>(backendToolsMap);

        // Add frontend tools as proper Tool objects
        for (Map<String, Object> frontendTool : frontendTools) {
            String toolName = (String) frontendTool.get("name");
            String toolDescription = (String) frontendTool.get("description");

            // Create frontend tool object with source marker
            Map<String, Object> toolAttributes = new HashMap<>();
            toolAttributes.put("source", "frontend");
            toolAttributes.put("tool_definition", frontendTool); // Store original definition for runtime lookup

            // Add input_schema for function calling template substitution
            Object parameters = frontendTool.get("parameters");
            if (parameters != null) {
                toolAttributes.put("input_schema", gson.toJson(parameters));
                log.debug("AG-UI Debug: Added input_schema for frontend tool {}: {}", toolName, parameters);
            } else {
                // Provide empty schema if no parameters
                Map<String, Object> emptySchema = Map.of("type", "object", "properties", Map.of());
                toolAttributes.put("input_schema", gson.toJson(emptySchema));
                log.debug("AG-UI Debug: Added empty input_schema for frontend tool {}", toolName);
            }

            // Create a simple Tool implementation for frontend tools
            Tool frontendToolObj = new Tool() {
                @Override
                public String getName() {
                    return toolName;
                }

                @Override
                public void setName(String name) { /* Not needed for frontend tools */ }

                @Override
                public String getDescription() {
                    return toolDescription;
                }

                @Override
                public void setDescription(String description) { /* Not needed for frontend tools */ }

                @Override
                public Map<String, Object> getAttributes() {
                    return toolAttributes;
                }

                @Override
                public void setAttributes(Map<String, Object> attributes) { /* Not needed for frontend tools */ }

                @Override
                @SuppressWarnings("unchecked")
                public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
                    // Frontend tools return a placeholder result that allows ReAct to continue
                    // The actual AG-UI events will be generated when MLAGUIAgentRunner processes the LLM response
                    log.info("AG-UI: Frontend tool {} executed with parameters: {}", toolName, parameters);
                    // Frontend tools should not be executed during ReAct - they should be called by the LLM in its final response
                    // Return an error to indicate this tool should be called differently
                    String errorResult = String
                        .format(
                            "Error: Tool '%s' is a frontend tool and should be called via function calling in the final response, not during ReAct execution.",
                            toolName
                        );
                    listener.onResponse((T) errorResult);
                }

                @Override
                public boolean validate(Map<String, String> parameters) {
                    // Frontend tools are always valid - validation happens in browser
                    return true;
                }

                @Override
                public String getType() {
                    return "AGUIFrontendTool";
                }

                @Override
                public String getVersion() {
                    return "1.0.0";
                }
            };

            unifiedToolsMap.put(toolName, frontendToolObj);
            log.debug("AG-UI Debug: Added frontend tool as Tool object: {}", toolName);
        }

        // Store frontend tools mapping for runtime lookup
        if (!frontendTools.isEmpty()) {
            params.put("frontend_tools_json", gson.toJson(frontendTools));
        }

        log
            .info(
                "AG-UI Debug: Created unified tool map with {} total tools ({} frontend + {} backend)",
                unifiedToolsMap.size(),
                frontendTools.size(),
                backendToolsMap.size()
            );

        // Call runReAct with unified tools - both frontend and backend tools will be visible to LLM
        runReAct(
            mlAgent.getLlm(),
            unifiedToolsMap,
            toolSpecMap,
            params,
            memory,
            sessionId,
            mlAgent.getTenantId(),
            listener,
            functionCalling
        );
    }

    /**
     * Process AG-UI tool call results from frontend execution
     */
    private void processAGUIToolResults(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        Memory memory,
        String sessionId,
        FunctionCalling functionCalling,
        String aguiToolCallResults
    ) {
        try {
            log.info("Processing AG-UI tool call results for agent: {}", mlAgent.getName());

            // Parse tool call results from frontend
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {
            }.getType();
            List<Map<String, String>> toolResults = gson.fromJson(aguiToolCallResults, listType);

            if (functionCalling != null && !toolResults.isEmpty()) {
                // Convert to format expected by FunctionCalling
                List<Map<String, Object>> formattedResults = new ArrayList<>();
                for (Map<String, String> result : toolResults) {
                    Map<String, Object> formattedResult = new HashMap<>();
                    formattedResult.put(TOOL_CALL_ID, result.get("tool_call_id"));
                    formattedResult.put(TOOL_RESULT, Map.of("text", result.get("content")));
                    formattedResults.add(formattedResult);
                }

                // Supply tool results to function calling for LLM processing
                List<LLMMessage> llmMessages = functionCalling.supply(formattedResults);

                // Create LLM parameters for final response generation
                Map<String, String> llmParams = constructLLMParams(mlAgent.getLlm(), params);

                // Call LLM with tool results to generate final response
                if (!llmMessages.isEmpty()) {
                    // Use the existing runReAct pattern for LLM execution
                    Map<String, Tool> emptyTools = new HashMap<>();
                    Map<String, MLToolSpec> emptyToolSpecs = new HashMap<>();

                    // Update parameters with the processed tool results
                    Map<String, String> updatedParams = new HashMap<>(params);
                    // The tool results have already been processed by functionCalling.supply()
                    // Now we can proceed with standard LLM execution

                    runReAct(
                        mlAgent.getLlm(),
                        emptyTools,
                        emptyToolSpecs,
                        updatedParams,
                        memory,
                        sessionId,
                        mlAgent.getTenantId(),
                        listener,
                        functionCalling
                    );
                } else {
                    listener.onFailure(new RuntimeException("No LLM messages generated from tool results"));
                }
            } else {
                listener.onFailure(new RuntimeException("No function calling interface or empty tool results"));
            }
        } catch (Exception e) {
            log.error("Error processing AG-UI tool call results", e);
            listener.onFailure(e);
        }
    }

    /**
     * Process AG-UI tool call generation (frontend tools)
     */
    private void processAGUIToolCall(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        Memory memory,
        String sessionId,
        FunctionCalling functionCalling,
        String aguiTools
    ) {
        try {
            log.info("AG-UI Debug: Processing frontend tools for agent: {}", mlAgent.getName());
            log.debug("AG-UI Debug: Frontend tools JSON: {}", aguiTools);

            if (functionCalling != null) {
                log.debug("AG-UI Debug: FunctionCalling is available: {}", functionCalling.getClass().getSimpleName());

                // Parse frontend tools
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {
                }.getType();
                List<Map<String, Object>> frontendTools = gson.fromJson(aguiTools, listType);
                log.info("AG-UI Debug: Parsed {} frontend tools", frontendTools.size());

                for (int i = 0; i < frontendTools.size(); i++) {
                    Map<String, Object> tool = frontendTools.get(i);
                    log.debug("AG-UI Debug: Tool {}: name={}, description={}", i, tool.get("name"), tool.get("description"));
                }

                // Create LLM parameters first
                Map<String, String> llmParams = constructLLMParams(mlAgent.getLlm(), params);

                // Configure function calling first to set up tool template
                functionCalling.configure(llmParams);
                log.info("AG-UI Debug: Function calling configured, checking tool template");

                // Now get the tool template that was set by function calling
                String toolTemplate = llmParams.get(AgentUtils.TOOL_TEMPLATE);
                log.info("AG-UI Debug: Tool template check - template is: {}", toolTemplate != null ? "present" : "NULL");

                // Convert frontend tools to function calling format and put in _tools parameter
                List<String> toolInfos = new ArrayList<>();
                if (toolTemplate != null) {
                    log.info("AG-UI Debug: Using tool template: {}", toolTemplate);
                    for (Map<String, Object> tool : frontendTools) {
                        Map<String, Object> toolParams = new HashMap<>();
                        toolParams.put("name", tool.get("name"));
                        toolParams.put("description", tool.get("description"));

                        // Map frontend tool parameters to attributes.input_schema for function calling
                        Object parameters = tool.get("parameters");
                        if (parameters != null) {
                            // Convert parameters to JSON string for proper OpenAI function calling format
                            String parametersJson = gson.toJson(parameters);
                            toolParams.put("attributes.input_schema", parametersJson);
                            log.debug("AG-UI Debug: Added input_schema JSON for tool {}: {}", tool.get("name"), parametersJson);
                        }

                        // Add any other attributes if present
                        Object attributesObj = tool.get("attributes");
                        if (attributesObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> attributes = (Map<String, Object>) attributesObj;
                            for (String key : attributes.keySet()) {
                                toolParams.put("attributes." + key, attributes.get(key));
                            }
                        }
                        StringSubstitutor substitutor = new StringSubstitutor(toolParams, "${tool.", "}");
                        String formattedTool = substitutor.replace(toolTemplate);
                        toolInfos.add(formattedTool);
                        log.info("AG-UI Debug: Formatted tool: {}", formattedTool);
                    }
                    llmParams.put(AgentUtils.TOOLS, String.join(", ", toolInfos));
                    log.info("AG-UI Debug: Set _tools parameter with {} tools", toolInfos.size());
                } else {
                    log.error("AG-UI Debug: Tool template is NULL - cannot format frontend tools for function calling!");
                }

                log.info("AG-UI Debug: Final LLM params keys: {}", llmParams.keySet());
                log.info("AG-UI Debug: _tools parameter value: {}", llmParams.get(AgentUtils.TOOLS));
                log.info("AG-UI Debug: tool_configs parameter: {}", llmParams.get("tool_configs"));
                log.info("AG-UI Debug: Tools configured via function calling, not in prompt");

                String question = params.get("question");

                if (question != null && !question.isEmpty()) {
                    // Use the existing runReAct pattern with frontend tools to generate tool calls
                    Map<String, Tool> emptyTools = new HashMap<>();
                    Map<String, MLToolSpec> emptyToolSpecs = new HashMap<>();

                    // The frontend tools are already configured in functionCalling
                    // Now call LLM with frontend tools to generate tool calls
                    log.info("AG-UI Debug: About to call runReAct with llmParams keys: {}", llmParams.keySet());
                    log.info("AG-UI Debug: LLM params _tools: {}", llmParams.get(AgentUtils.TOOLS));
                    runReAct(
                        mlAgent.getLlm(),
                        emptyTools,
                        emptyToolSpecs,
                        llmParams,
                        memory,
                        sessionId,
                        mlAgent.getTenantId(),
                        listener,
                        functionCalling
                    );
                } else {
                    listener.onFailure(new RuntimeException("No question found for AG-UI tool call generation"));
                }
            } else {
                listener.onFailure(new RuntimeException("No function calling interface available"));
            }
        } catch (Exception e) {
            log.error("Error processing AG-UI tool call generation", e);
            listener.onFailure(e);
        }
    }

}
