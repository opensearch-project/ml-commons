/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.AI_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.extractModelResponseJson;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.StepListener;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
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
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.ml.repackage.com.google.common.collect.Lists;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
public class MLChatAgentRunner implements MLAgentRunner {

    public static final String SESSION_ID = "session_id";
    public static final String PROMPT_PREFIX = "prompt_prefix";
    public static final String LLM_TOOL_PROMPT_PREFIX = "LanguageModelTool.prompt_prefix";
    public static final String LLM_TOOL_PROMPT_SUFFIX = "LanguageModelTool.prompt_suffix";
    public static final String PROMPT_SUFFIX = "prompt_suffix";
    public static final String TOOLS = "tools";
    public static final String TOOL_DESCRIPTIONS = "tool_descriptions";
    public static final String TOOL_NAMES = "tool_names";
    public static final String OS_INDICES = "opensearch_indices";
    public static final String EXAMPLES = "examples";
    public static final String SCRATCHPAD = "scratchpad";
    public static final String CHAT_HISTORY = "chat_history";
    public static final String CONTEXT = "context";
    public static final String PROMPT = "prompt";
    public static final String LLM_RESPONSE = "llm_response";

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;

    public MLChatAgentRunner(
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
        String memoryType = mlAgent.getMemory().getType();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String appType = mlAgent.getAppType();
        String title = params.get(MLAgentExecutor.QUESTION);

        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap.get(memoryType);
        conversationIndexMemoryFactory.create(title, memoryId, appType, ActionListener.<ConversationIndexMemory>wrap(memory -> {
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

                StringBuilder chatHistoryBuilder = new StringBuilder();
                if (messageList.size() > 0) {
                    chatHistoryBuilder.append("Below is Chat History between Human and AI which sorted by time with asc order:\n");
                    for (Message message : messageList) {
                        chatHistoryBuilder.append(message.toString()).append("\n");
                    }
                    params.put(CHAT_HISTORY, chatHistoryBuilder.toString());
                }

                runAgent(mlAgent, params, listener, memory, memory.getConversationId());
            }, e -> {
                log.error("Failed to get chat history", e);
                listener.onFailure(e);
            }));
        }, listener::onFailure));
    }

    private void runAgent(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, Memory memory, String sessionId) {
        List<MLToolSpec> toolSpecs = mlAgent.getTools();
        Map<String, Tool> tools = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
        for (MLToolSpec toolSpec : toolSpecs) {
            Map<String, String> toolParams = new HashMap<>();
            Map<String, String> executeParams = new HashMap<>();
            if (toolSpec.getParameters() != null) {
                toolParams.putAll(toolSpec.getParameters());
                executeParams.putAll(toolSpec.getParameters());
            }
            for (String key : params.keySet()) {
                if (key.startsWith(toolSpec.getType() + ".")) {
                    executeParams.put(key.replace(toolSpec.getType() + ".", ""), params.get(key));
                }
            }
            log.info("Fetching tool for type: " + toolSpec.getType());
            Tool tool = toolFactories.get(toolSpec.getType()).create(executeParams);
            if (toolSpec.getName() != null) {
                tool.setName(toolSpec.getName());
            }

            if (toolSpec.getDescription() != null) {
                tool.setDescription(toolSpec.getDescription());
            }
            String toolName = Optional.ofNullable(tool.getName()).orElse(toolSpec.getType());
            tools.put(toolName, tool);
            toolSpecMap.put(toolName, toolSpec);
        }

        runReAct(mlAgent.getLlm(), tools, toolSpecMap, params, memory, sessionId, listener);
    }

    private void runReAct(
        LLMSpec llm,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        Map<String, String> parameters,
        Memory memory,
        String sessionId,
        ActionListener<Object> listener
    ) {
        String question = parameters.get(MLAgentExecutor.QUESTION);
        String parentInteractionId = parameters.get(MLAgentExecutor.PARENT_INTERACTION_ID);
        boolean verbose = parameters.containsKey("verbose") && Boolean.parseBoolean(parameters.get("verbose"));
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

        String prompt = parameters.get(PROMPT);
        if (prompt == null) {
            prompt = PromptTemplate.PROMPT_TEMPLATE;
        }
        String promptPrefix = parameters.getOrDefault("prompt.prefix", PromptTemplate.PROMPT_TEMPLATE_PREFIX);
        tmpParameters.put("prompt.prefix", promptPrefix);

        String promptSuffix = parameters.getOrDefault("prompt.suffix", PromptTemplate.PROMPT_TEMPLATE_SUFFIX);
        tmpParameters.put("prompt.suffix", promptSuffix);

        String promptFormatInstruction = parameters.getOrDefault("prompt.format_instruction", PromptTemplate.PROMPT_FORMAT_INSTRUCTION);
        tmpParameters.put("prompt.format_instruction", promptFormatInstruction);
        if (!tmpParameters.containsKey("prompt.tool_response")) {
            tmpParameters.put("prompt.tool_response", PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE);
        }
        String promptToolResponse = parameters.getOrDefault("prompt.tool_response", PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE);
        tmpParameters.put("prompt.tool_response", promptToolResponse);

        StringSubstitutor promptSubstitutor = new StringSubstitutor(tmpParameters, "${parameters.", "}");
        prompt = promptSubstitutor.replace(prompt);

        final List<String> inputTools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = Optional.ofNullable(entry.getValue().getName()).orElse(entry.getValue().getType());
            inputTools.add(toolName);
        }

        prompt = AgentUtils.addPrefixSuffixToPrompt(parameters, prompt);
        prompt = AgentUtils.addToolsToPrompt(tools, parameters, inputTools, prompt);
        prompt = AgentUtils.addIndicesToPrompt(parameters, prompt);
        prompt = AgentUtils.addExamplesToPrompt(parameters, prompt);
        prompt = AgentUtils.addChatHistoryToPrompt(parameters, prompt);
        prompt = AgentUtils.addContextToPrompt(parameters, prompt);

        tmpParameters.put(PROMPT, prompt);

        List<ModelTensors> modelTensors = new ArrayList<>();

        List<ModelTensors> cotModelTensors = new ArrayList<>();
        cotModelTensors
            .add(
                ModelTensors
                    .builder()
                    .mlModelTensors(
                        Collections.singletonList(ModelTensor.builder().name(MLAgentExecutor.MEMORY_ID).result(sessionId).build())
                    )
                    .build()
            );

        StringBuilder scratchpadBuilder = new StringBuilder();
        StringSubstitutor tmpSubstitutor = new StringSubstitutor(
            ImmutableMap.of(SCRATCHPAD, scratchpadBuilder.toString()),
            "${parameters.",
            "}"
        );
        AtomicReference<String> newPrompt = new AtomicReference<>(tmpSubstitutor.replace(prompt));
        tmpParameters.put(PROMPT, newPrompt.get());

        String maxIteration = Optional.ofNullable(tmpParameters.get("max_iteration")).orElse("3");

        // Create root interaction.
        ConversationIndexMemory conversationIndexMemory = (ConversationIndexMemory) memory;

        // Trace number
        AtomicInteger traceNumber = new AtomicInteger(0);

        StepListener firstListener;
        AtomicReference<StepListener<MLTaskResponse>> lastLlmListener = new AtomicReference<>();
        AtomicReference<StepListener<Object>> lastToolListener = new AtomicReference<>();
        AtomicBoolean getFinalAnswer = new AtomicBoolean(false);
        AtomicReference<String> lastTool = new AtomicReference<>();
        AtomicReference<String> lastThought = new AtomicReference<>();
        AtomicReference<String> lastAction = new AtomicReference<>();
        AtomicReference<String> lastActionInput = new AtomicReference<>();
        AtomicReference<String> lastActionResult = new AtomicReference<>();
        Map<String, Object> additionalInfo = new ConcurrentHashMap<>();

        StepListener<?> lastStepListener = null;
        int maxIterations = Integer.parseInt(maxIteration) * 2;

        String finalPrompt = prompt;

        firstListener = new StepListener<MLTaskResponse>();
        lastLlmListener.set(firstListener);
        lastStepListener = firstListener;
        for (int i = 0; i < maxIterations; i++) {
            int finalI = i;
            StepListener<?> nextStepListener = new StepListener<>();

            lastStepListener.whenComplete(output -> {
                StringBuilder sessionMsgAnswerBuilder = new StringBuilder();
                if (finalI % 2 == 0) {
                    MLTaskResponse llmResponse = (MLTaskResponse) output;
                    ModelTensorOutput tmpModelTensorOutput = (ModelTensorOutput) llmResponse.getOutput();
                    Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
                    if (dataAsMap.size() == 1 && dataAsMap.containsKey("response")) {
                        String response = (String) dataAsMap.get("response");
                        String thoughtResponse = extractModelResponseJson(response);
                        dataAsMap = gson.fromJson(thoughtResponse, Map.class);
                    }
                    String thought = String.valueOf(dataAsMap.get("thought"));
                    String action = String.valueOf(dataAsMap.get("action"));
                    String actionInput = String.valueOf(dataAsMap.get("action_input"));
                    String finalAnswer = (String) dataAsMap.get("final_answer");
                    if (!dataAsMap.containsKey("thought")) {
                        String response = (String) dataAsMap.get("response");
                        Pattern pattern = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
                        Matcher matcher = pattern.matcher(response);
                        if (matcher.find()) {
                            String jsonBlock = matcher.group(1);
                            Map map = gson.fromJson(jsonBlock, Map.class);
                            thought = String.valueOf(map.get("thought"));
                            action = String.valueOf(map.get("action"));
                            actionInput = String.valueOf(map.get("action_input"));
                            finalAnswer = (String) map.get("final_answer");
                        } else {
                            finalAnswer = response;
                        }
                    }

                    if (finalI == 0 && !thought.contains("Thought:")) {
                        sessionMsgAnswerBuilder.append("Thought: ");
                    }
                    sessionMsgAnswerBuilder.append(thought);
                    lastThought.set(thought);
                    cotModelTensors
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
                    // TODO: check if verbose
                    modelTensors.addAll(tmpModelTensorOutput.getMlModelOutputs());

                    if (conversationIndexMemory != null) {
                        ConversationIndexMessage msgTemp = ConversationIndexMessage
                            .conversationIndexMessageBuilder()
                            .type("ReAct")
                            .question(question)
                            .response(thought)
                            .finalAnswer(false)
                            .sessionId(sessionId)
                            .build();
                        conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), null);
                    }
                    if (finalAnswer != null) {
                        finalAnswer = finalAnswer.trim();
                        if (conversationIndexMemory != null) {
                            // Create final trace message.
                            ConversationIndexMessage msgTemp = ConversationIndexMessage
                                .conversationIndexMessageBuilder()
                                .type("ReAct")
                                .question(question)
                                .response(finalAnswer)
                                .finalAnswer(true)
                                .sessionId(sessionId)
                                .build();
                            conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), null);
                        }
                    }
                    if (finalAnswer != null) {
                        finalAnswer = finalAnswer.trim();
                        if (conversationIndexMemory != null) {
                            String finalAnswer1 = finalAnswer;
                            // Create final trace message.
                            ConversationIndexMessage msgTemp = ConversationIndexMessage
                                .conversationIndexMessageBuilder()
                                .type("ReAct")
                                .question(question)
                                .response(finalAnswer1)
                                .finalAnswer(true)
                                .sessionId(sessionId)
                                .build();
                            conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), null);
                            // Update root interaction.
                            conversationIndexMemory
                                .getMemoryManager()
                                .updateInteraction(
                                    parentInteractionId,
                                    ImmutableMap.of(AI_RESPONSE_FIELD, finalAnswer1, ADDITIONAL_INFO_FIELD, additionalInfo),
                                    ActionListener.<UpdateResponse>wrap(updateResponse -> {
                                        log.info("Updated final answer into interaction id: {}", parentInteractionId);
                                        log.info("Final answer: {}", finalAnswer1);
                                    }, e -> log.error("Failed to update root interaction", e))
                                );
                        }
                        cotModelTensors
                            .add(
                                ModelTensors
                                    .builder()
                                    .mlModelTensors(
                                        Collections.singletonList(ModelTensor.builder().name("response").result(finalAnswer).build())
                                    )
                                    .build()
                            );

                        List<ModelTensors> finalModelTensors = new ArrayList<>();
                        finalModelTensors
                            .add(
                                ModelTensors
                                    .builder()
                                    .mlModelTensors(
                                        Collections
                                            .singletonList(
                                                ModelTensor
                                                    .builder()
                                                    .name("response")
                                                    .dataAsMap(
                                                        ImmutableMap.of("response", finalAnswer, ADDITIONAL_INFO_FIELD, additionalInfo)
                                                    )
                                                    .build()
                                            )
                                    )
                                    .build()
                            );
                        getFinalAnswer.set(true);
                        if (verbose) {
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(cotModelTensors).build());
                        } else {
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
                        }
                        return;
                    }

                    lastAction.set(action);
                    lastActionInput.set(actionInput);

                    String toolName = action;
                    for (String key : tools.keySet()) {
                        if (action.toLowerCase().contains(key.toLowerCase())) {
                            toolName = key;
                        }
                    }
                    action = toolName;

                    if (tools.containsKey(action) && inputTools.contains(action)) {
                        Map<String, String> toolParams = new HashMap<>();
                        toolParams.put("input", actionInput);
                        if (tools.get(action).validate(toolParams)) {
                            if (tools.get(action) instanceof MLModelTool) {
                                Map<String, String> llmToolTmpParameters = new HashMap<>();
                                llmToolTmpParameters.putAll(tmpParameters);
                                llmToolTmpParameters.putAll(toolSpecMap.get(action).getParameters());
                                // TODO: support tool parameter override : langauge_model_tool.prompt
                                llmToolTmpParameters.put(MLAgentExecutor.QUESTION, actionInput);
                                tools.get(action).run(llmToolTmpParameters, nextStepListener); // run tool
                            } else {
                                tools.get(action).run(toolParams, nextStepListener); // run tool
                            }
                        } else {
                            lastActionResult.set("Tool " + action + " can't work for input: " + actionInput);
                            lastTool.set(action);
                            String res = "Tool " + action + " can't work for input: " + actionInput;
                            ((ActionListener<Object>) nextStepListener).onResponse(res);
                        }
                    } else {
                        lastTool.set(null);
                        lastToolListener.set(null);
                        ((ActionListener<Object>) nextStepListener).onResponse("no access to this tool ");
                        lastActionResult.set("no access to this tool ");

                        StringSubstitutor substitutor = new StringSubstitutor(
                            ImmutableMap.of(SCRATCHPAD, scratchpadBuilder.toString()),
                            "${parameters.",
                            "}"
                        );
                        newPrompt.set(substitutor.replace(finalPrompt));
                        tmpParameters.put(PROMPT, newPrompt.get());
                    }
                } else {
                    MLToolSpec toolSpec = toolSpecMap.get(lastAction.get());
                    if (toolSpec != null && toolSpec.isIncludeOutputInAgentResponse()) {
                        String outputString = output instanceof String
                            ? (String) output
                            : AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(output));

                        String toolOutputKey = String.format("%s.output", toolSpec.getType());
                        if (additionalInfo.get(toolOutputKey) != null) {
                            List<String> list = (List<String>) additionalInfo.get(toolOutputKey);
                            list.add(outputString);
                        } else {
                            additionalInfo.put(toolOutputKey, Lists.newArrayList(outputString));
                        }

                    }
                    modelTensors
                        .add(
                            ModelTensors
                                .builder()
                                .mlModelTensors(
                                    Collections
                                        .singletonList(
                                            ModelTensor
                                                .builder()
                                                .dataAsMap(ImmutableMap.of("response", lastThought.get() + "\nObservation: " + output))
                                                .build()
                                        )
                                )
                                .build()
                        );

                    String toolResponse = tmpParameters.get("prompt.tool_response");
                    StringSubstitutor toolResponseSubstitutor = new StringSubstitutor(
                        ImmutableMap.of("observation", output),
                        "${parameters.",
                        "}"
                    );
                    toolResponse = toolResponseSubstitutor.replace(toolResponse);
                    scratchpadBuilder.append(toolResponse).append("\n\n");
                    if (conversationIndexMemory != null) {
                        // String res = "Action: " + lastAction.get() + "\nAction Input: " + lastActionInput + "\nObservation: " + result;
                        ConversationIndexMessage msgTemp = ConversationIndexMessage
                            .conversationIndexMessageBuilder()
                            .type("ReAct")
                            .question(lastActionInput.get())
                            .response((String) output)
                            .finalAnswer(false)
                            .sessionId(sessionId)
                            .build();
                        conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), lastAction.get());

                    }
                    StringSubstitutor substitutor = new StringSubstitutor(
                        ImmutableMap.of(SCRATCHPAD, scratchpadBuilder.toString()),
                        "${parameters.",
                        "}"
                    );
                    newPrompt.set(substitutor.replace(finalPrompt));
                    tmpParameters.put(PROMPT, newPrompt.get());

                    sessionMsgAnswerBuilder.append("\nObservation: ").append(output);
                    cotModelTensors
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

                    ActionRequest request = new MLPredictionTaskRequest(
                        llm.getModelId(),
                        RemoteInferenceMLInput
                            .builder()
                            .algorithm(FunctionName.REMOTE)
                            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(tmpParameters).build())
                            .build()
                    );
                    client.execute(MLPredictionTaskAction.INSTANCE, request, (ActionListener<MLTaskResponse>) nextStepListener);
                    if (finalI == maxIterations - 1) {
                        if (verbose) {
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(cotModelTensors).build());
                        } else {
                            List<ModelTensors> finalModelTensors = new ArrayList<>();
                            finalModelTensors
                                .add(
                                    ModelTensors
                                        .builder()
                                        .mlModelTensors(
                                            Collections
                                                .singletonList(
                                                    ModelTensor
                                                        .builder()
                                                        .name("response")
                                                        .dataAsMap(ImmutableMap.of("response", lastThought.get()))
                                                        .build()
                                                )
                                        )
                                        .build()
                                );
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
                        }
                    } else {
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
                .build()
        );
        client.execute(MLPredictionTaskAction.INSTANCE, request, firstListener);
    }

}
