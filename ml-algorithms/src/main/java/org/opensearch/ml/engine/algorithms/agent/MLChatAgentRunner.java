/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.AI_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DISABLE_TRACE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTIONS_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_CHAT_HISTORY_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_SUFFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.RESPONSE_FORMAT_INSTRUCTION;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_NAME;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESPONSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.VERBOSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.cleanUpResource;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.constructToolParams;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMcpToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMessageHistoryLimit;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getToolName;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getToolNames;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.outputToOutputString;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.parseLLMOutput;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.substitute;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.CHAT_HISTORY_PREFIX;

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
import java.util.function.Consumer;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.memory.Message;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.ml.repackage.com.google.common.collect.Lists;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

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
    public static final String DEFAULT_NO_ESCAPE_PARAMS = "_chat_history,_tools,_interactions,tool_configs";
    public static final String INTERACTION_TEMPLATE_TOOL_RESPONSE = "interaction_template.tool_response";
    public static final String CHAT_HISTORY_QUESTION_TEMPLATE = "chat_history_template.user_question";
    public static final String CHAT_HISTORY_RESPONSE_TEMPLATE = "chat_history_template.ai_response";
    public static final String CHAT_HISTORY_MESSAGE_PREFIX = "${_chat_history.message.";
    public static final String LLM_INTERFACE = "_llm_interface";

    private static final String DEFAULT_MAX_ITERATIONS = "10";

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;

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
    public void run(MLAgent mlAgent, Map<String, String> inputParams, ActionListener<Object> listener) {
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
        // todo: introduce function calling
        // handle parameters based on llmInterface
        if ("openai/v1/chat/completions".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
            }
            params.put(LLM_RESPONSE_FILTER, "$.choices[0].message.content");

            params
                .put(
                    TOOL_TEMPLATE,
                    "{\"type\": \"function\", \"function\": { \"name\": \"${tool.name}\", \"description\": \"${tool.description}\", \"parameters\": ${tool.attributes.input_schema}, \"strict\": ${tool.attributes.strict:-false} } }"
                );
            params.put(TOOL_CALLS_PATH, "$.choices[0].message.tool_calls");
            params.put(TOOL_CALLS_TOOL_NAME, "function.name");
            params.put(TOOL_CALLS_TOOL_INPUT, "function.arguments");
            params.put(TOOL_CALL_ID_PATH, "id");
            params.put("tool_configs", ", \"tools\": [${parameters._tools:-}], \"parallel_tool_calls\": false");

            params.put("tool_choice", "auto");
            params.put("parallel_tool_calls", "false");

            params.put("interaction_template.assistant_tool_calls_path", "$.choices[0].message");
            params
                .put(
                    "interaction_template.tool_response",
                    "{ \"role\": \"tool\", \"tool_call_id\": \"${_interactions.tool_call_id}\", \"content\": \"${_interactions.tool_response}\" }"
                );

            params.put("chat_history_template.user_question", "{\"role\": \"user\",\"content\": \"${_chat_history.message.question}\"}");
            params.put("chat_history_template.ai_response", "{\"role\": \"assistant\",\"content\": \"${_chat_history.message.response}\"}");

            params.put(LLM_FINISH_REASON_PATH, "$.choices[0].finish_reason");
            params.put(LLM_FINISH_REASON_TOOL_USE, "tool_calls");
        } else if ("bedrock/converse/claude".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
            }
            params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");

            params
                .put(
                    TOOL_TEMPLATE,
                    "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}"
                );
            params.put(TOOL_CALLS_PATH, "$.output.message.content[*].toolUse");
            params.put(TOOL_CALLS_TOOL_NAME, "name");
            params.put(TOOL_CALLS_TOOL_INPUT, "input");
            params.put(TOOL_CALL_ID_PATH, "toolUseId");
            params.put("tool_configs", ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}");

            params.put("interaction_template.assistant_tool_calls_path", "$.output.message");
            params
                .put(
                    "interaction_template.tool_response",
                    "{\"role\":\"user\",\"content\":[{\"toolResult\":{\"toolUseId\":\"${_interactions.tool_call_id}\",\"content\":[{\"text\":\"${_interactions.tool_response}\"}]}}]}"
                );

            params
                .put(
                    "chat_history_template.user_question",
                    "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}"
                );
            params
                .put(
                    "chat_history_template.ai_response",
                    "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}"
                );

            params.put(LLM_FINISH_REASON_PATH, "$.stopReason");
            params.put(LLM_FINISH_REASON_TOOL_USE, "tool_use");
        } else if ("bedrock/converse/deepseek_r1".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, "_chat_history,_interactions");
            }
            params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");
            params.put("llm_final_response_post_filter", "$.message.content[0].text");

            params
                .put(
                    TOOL_TEMPLATE,
                    "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}"
                );
            params.put(TOOL_CALLS_PATH, "_llm_response.tool_calls");
            params.put(TOOL_CALLS_TOOL_NAME, "tool_name");
            params.put(TOOL_CALLS_TOOL_INPUT, "input");
            params.put(TOOL_CALL_ID_PATH, "id");

            params.put("interaction_template.assistant_tool_calls_path", "$.output.message");
            params.put("interaction_template.assistant_tool_calls_exclude_path", "[ \"$.output.message.content[?(@.reasoningContent)]\" ]");
            params
                .put(
                    "interaction_template.tool_response",
                    "{\"role\":\"user\",\"content\":[ {\"text\":\"{\\\"tool_call_id\\\":\\\"${_interactions.tool_call_id}\\\",\\\"tool_result\\\": \\\"${_interactions.tool_response}\\\"\"} ]}"
                );

            params
                .put(
                    "chat_history_template.user_question",
                    "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}"
                );
            params
                .put(
                    "chat_history_template.ai_response",
                    "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}"
                );

            params.put(LLM_FINISH_REASON_PATH, "_llm_response.stop_reason");
            params.put(LLM_FINISH_REASON_TOOL_USE, "tool_use");
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

                runAgent(mlAgent, params, listener, memory, memory.getConversationId());
            }, e -> {
                log.error("Failed to get chat history", e);
                listener.onFailure(e);
            }), messageHistoryLimit);
        }, listener::onFailure));
    }

    private void runAgent(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, Memory memory, String sessionId) {
        List<MLToolSpec> toolSpecs = getMlToolSpecs(mlAgent, params);

        // Create a common method to handle both success and failure cases
        Consumer<List<MLToolSpec>> processTools = (allToolSpecs) -> {
            Map<String, Tool> tools = new HashMap<>();
            Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
            createTools(toolFactories, params, allToolSpecs, tools, toolSpecMap, mlAgent);
            runReAct(mlAgent.getLlm(), tools, toolSpecMap, params, memory, sessionId, mlAgent.getTenantId(), listener);
        };

        // Fetch MCP tools and handle both success and failure cases
        getMcpToolSpecs(mlAgent, client, sdkClient, encryptor, ActionListener.wrap(mcpTools -> {
            toolSpecs.addAll(mcpTools);
            processTools.accept(toolSpecs);
        }, e -> {
            log.error("Failed to get MCP tools, continuing with base tools only", e);
            processTools.accept(toolSpecs);
        }));
    }

    private void runReAct(
        LLMSpec llm,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        Map<String, String> parameters,
        Memory memory,
        String sessionId,
        String tenantId,
        ActionListener<Object> listener
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
            StepListener<?> nextStepListener = new StepListener<>();

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
                        interactions
                    );

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

                    if (tools.containsKey(action)) {
                        Map<String, String> toolParams = constructToolParams(
                            tools,
                            toolSpecMap,
                            question,
                            lastActionInput,
                            action,
                            actionInput
                        );
                        runTool(
                            tools,
                            toolSpecMap,
                            tmpParameters,
                            (ActionListener<Object>) nextStepListener,
                            action,
                            actionInput,
                            toolParams,
                            interactions,
                            toolCallId
                        );
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
                    addToolOutputToAddtionalInfo(toolSpecMap, lastAction, additionalInfo, output);

                    String toolResponse = constructToolResponse(
                        tmpParameters,
                        lastAction,
                        lastActionInput,
                        lastToolSelectionResponse,
                        output
                    );
                    scratchpadBuilder.append(toolResponse).append("\n\n");

                    saveTraceData(
                        conversationIndexMemory,
                        "ReAct",
                        lastActionInput.get(),
                        outputToOutputString(output),
                        sessionId,
                        traceDisabled,
                        parentInteractionId,
                        traceNumber,
                        lastAction.get()
                    );

                    StringSubstitutor substitutor = new StringSubstitutor(Map.of(SCRATCHPAD, scratchpadBuilder), "${parameters.", "}");
                    newPrompt.set(substitutor.replace(finalPrompt));
                    tmpParameters.put(PROMPT, newPrompt.get());
                    if (interactions.size() > 0) {
                        tmpParameters.put(INTERACTIONS, ", " + String.join(", ", interactions));
                    }

                    sessionMsgAnswerBuilder.append(outputToOutputString(output));
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
                        if (verbose) {
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(traceTensors).build());
                        } else {
                            List<ModelTensors> finalModelTensors = createFinalAnswerTensors(
                                createModelTensors(sessionId, parentInteractionId),
                                List.of(ModelTensor.builder().name("response").dataAsMap(Map.of("response", lastThought.get())).build())
                            );
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
                        }
                    } else {
                        ActionRequest request = new MLPredictionTaskRequest(
                            llm.getModelId(),
                            RemoteInferenceMLInput
                                .builder()
                                .algorithm(FunctionName.REMOTE)
                                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(tmpParameters).build())
                                .build(),
                            null,
                            tenantId
                        );
                        client.execute(MLPredictionTaskAction.INSTANCE, request, (ActionListener<MLTaskResponse>) nextStepListener);
                    }
                }
            }, e -> {
                log.error("Failed to run chat agent", e);
                listener.onFailure(e);
            });
            if (i < maxIterations - 1) {
                lastStepListener = nextStepListener;
            }
        }

        ActionRequest request = new MLPredictionTaskRequest(
            llm.getModelId(),
            RemoteInferenceMLInput
                .builder()
                .algorithm(FunctionName.REMOTE)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(tmpParameters).build())
                .build(),
            null,
            tenantId
        );
        client.execute(MLPredictionTaskAction.INSTANCE, request, firstListener);
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
        String toolCallId
    ) {
        if (tools.get(action).validate(toolParams)) {
            try {
                String finalAction = action;
                ActionListener<Object> toolListener = ActionListener.wrap(r -> {
                    interactions
                        .add(
                            substitute(
                                tmpParameters.get(INTERACTION_TEMPLATE_TOOL_RESPONSE),
                                Map.of(TOOL_CALL_ID, toolCallId, "tool_response", processTextDoc(StringUtils.toJson(r))),
                                INTERACTIONS_PREFIX
                            )
                        );
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
                            String.format(Locale.ROOT, "Failed to run the tool %s with the error message %s.",
                                    finalAction, e.getMessage().replaceAll("\\n", "\n"))
                        );
                });
                if (tools.get(action) instanceof MLModelTool) {
                    Map<String, String> llmToolTmpParameters = new HashMap<>();
                    llmToolTmpParameters.putAll(tmpParameters);
                    llmToolTmpParameters.putAll(toolSpecMap.get(action).getParameters());
                    llmToolTmpParameters.put(MLAgentExecutor.QUESTION, actionInput);
                    tools.get(action).run(llmToolTmpParameters, toolListener); // run tool
                } else {
                    Map<String, String> parameters = new HashMap<>();
                    parameters.putAll(tmpParameters);
                    parameters.putAll(toolParams);
                    tools.get(action).run(parameters, toolListener); // run tool
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
            returnFinalResponse(sessionId, listener, parentInteractionId, verbose, cotModelTensors, additionalInfo, finalAnswer);
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

    private static Map<String, String> constructLLMParams(LLMSpec llm, Map<String, String> parameters) {
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

        tmpParameters.putIfAbsent(PROMPT_PREFIX, PromptTemplate.PROMPT_TEMPLATE_PREFIX);
        tmpParameters.putIfAbsent(PROMPT_SUFFIX, PromptTemplate.PROMPT_TEMPLATE_SUFFIX);
        tmpParameters.putIfAbsent(RESPONSE_FORMAT_INSTRUCTION, PromptTemplate.PROMPT_FORMAT_INSTRUCTION);
        tmpParameters.putIfAbsent(TOOL_RESPONSE, PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE);
        return tmpParameters;
    }

    private static void returnFinalResponse(
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
        if (verbose) {
            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(cotModelTensors).build());
        } else {
            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
        }
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
}
