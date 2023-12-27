/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.AI_RESPONSE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
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
        List<MLToolSpec> toolSpecs = mlAgent.getTools();
        String memoryType = mlAgent.getMemory().getType();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String appType = mlAgent.getAppType();
        String title = params.get(MLAgentExecutor.QUESTION);

        ConversationIndexMemory.Factory conversationIndexMemoryFactory = (ConversationIndexMemory.Factory) memoryFactoryMap.get(memoryType);
        conversationIndexMemoryFactory.create(title, memoryId, appType, ActionListener.<ConversationIndexMemory>wrap(memory -> {
            memory.getMessages(ActionListener.<List<Interaction>>wrap(r -> {
                List<Message> messageList = new ArrayList<>();
                Iterator<Interaction> iterator = r.iterator();
                while (iterator.hasNext()) {
                    Interaction next = iterator.next();
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

                runAgent(mlAgent, params, listener, toolSpecs, memory, memory.getConversationId());
            }, e -> {
                log.error("Failed to get chat history", e);
                listener.onFailure(e);
            }));
        }, e -> { listener.onFailure(e); }));
    }

    private void runAgent(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        List<MLToolSpec> toolSpecs,
        Memory memory,
        String sessionId
    ) {
        Map<String, Tool> tools = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
        for (int i = 0; i < toolSpecs.size(); i++) {
            MLToolSpec toolSpec = toolSpecs.get(i);
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
        boolean verbose = parameters.containsKey("verbose") ? Boolean.parseBoolean(parameters.get("verbose")) : false;
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
        String promptPrefix = parameters.containsKey("prompt.prefix")
            ? parameters.get("prompt.prefix")
            : PromptTemplate.PROMPT_TEMPLATE_PREFIX;
        tmpParameters.put("prompt.prefix", promptPrefix);

        String promptSuffix = parameters.containsKey("prompt.suffix")
            ? parameters.get("prompt.suffix")
            : PromptTemplate.PROMPT_TEMPLATE_SUFFIX;
        tmpParameters.put("prompt.suffix", promptSuffix);

        String promptFormatInstruction = parameters.containsKey("prompt.format_instruction")
            ? parameters.get("prompt.format_instruction")
            : PromptTemplate.PROMPT_FORMAT_INSTRUCTION;
        tmpParameters.put("prompt.format_instruction", promptFormatInstruction);
        if (!tmpParameters.containsKey("prompt.tool_response")) {
            tmpParameters.put("prompt.tool_response", PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE);
        }
        String promptToolResponse = parameters.containsKey("prompt.tool_response")
            ? parameters.get("prompt.tool_response")
            : PromptTemplate.PROMPT_TEMPLATE_TOOL_RESPONSE;
        tmpParameters.put("prompt.tool_response", promptToolResponse);

        StringSubstitutor promptSubstitutor = new StringSubstitutor(tmpParameters, "${parameters.", "}");
        prompt = promptSubstitutor.replace(prompt);

        final List<String> inputTools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = Optional.ofNullable(entry.getValue().getName()).orElse(entry.getValue().getType());
            inputTools.add(toolName);
        }

        prompt = addPrefixSuffixToPrompt(parameters, prompt);
        prompt = addToolsToPrompt(tools, parameters, inputTools, prompt);
        prompt = addIndicesToPrompt(parameters, prompt);
        prompt = addExamplesToPrompt(parameters, prompt);
        prompt = addChatHistoryToPrompt(parameters, prompt);
        prompt = addContextToPrompt(parameters, prompt);

        tmpParameters.put(PROMPT, prompt);

        List<ModelTensors> modelTensors = new ArrayList<>();

        List<ModelTensors> cotModelTensors = new ArrayList<>();
        cotModelTensors
            .add(
                ModelTensors
                    .builder()
                    .mlModelTensors(
                        Arrays
                            .asList(
                                ModelTensor.builder().name(MLAgentExecutor.MEMORY_ID).result(sessionId).build(),
                                ModelTensor.builder().name(MLAgentExecutor.PARENT_INTERACTION_ID).result(parentInteractionId).build()
                            )
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

        StepListener firstListener = null;
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
                StringBuilder sessionMsgAnswerBuilder = new StringBuilder("");
                if (finalI % 2 == 0) {
                    MLTaskResponse llmResponse = (MLTaskResponse) output;
                    ModelTensorOutput tmpModelTensorOutput = (ModelTensorOutput) llmResponse.getOutput();
                    Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();

                    String thought = dataAsMap.get("thought") + "";
                    String action = dataAsMap.get("action") + "";
                    String actionInput = dataAsMap.get("action_input") + "";
                    String finalAnswer = (String) dataAsMap.get("final_answer");
                    if (!dataAsMap.containsKey("thought")) {
                        String response = (String) dataAsMap.get("response");
                        Pattern pattern = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
                        Matcher matcher = pattern.matcher(response);
                        if (matcher.find()) {
                            String jsonBlock = matcher.group(1);
                            Map map = gson.fromJson(jsonBlock, Map.class);
                            thought = map.get("thought") + "";
                            action = map.get("action") + "";
                            actionInput = map.get("action_input") + "";
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
                                    Arrays.asList(ModelTensor.builder().name("response").result(sessionMsgAnswerBuilder.toString()).build())
                                )
                                .build()
                        );
                    // TODO: check if verbose
                    modelTensors.addAll(tmpModelTensorOutput.getMlModelOutputs());

                    if (conversationIndexMemory != null) {
                        String finalThought = thought;
                        ConversationIndexMessage msgTemp = ConversationIndexMessage
                            .conversationIndexMessageBuilder()
                            .type("ReAct")
                            .question(question)
                            .response(finalThought)
                            .finalAnswer(false)
                            .sessionId(sessionId)
                            .build();
                        conversationIndexMemory.save(msgTemp, parentInteractionId, traceNumber.addAndGet(1), null);
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
                                    }, e -> { log.error("Failed to update root interaction", e); })
                                );
                        }
                        cotModelTensors
                            .add(
                                ModelTensors
                                    .builder()
                                    .mlModelTensors(Arrays.asList(ModelTensor.builder().name("response").result(finalAnswer).build()))
                                    .build()
                            );

                        List<ModelTensors> finalModelTensors = new ArrayList<>();
                        finalModelTensors
                            .add(
                                ModelTensors
                                    .builder()
                                    .mlModelTensors(
                                        Arrays
                                            .asList(
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
                                            Arrays
                                                .asList(
                                                    ModelTensor
                                                        .builder()
                                                        .name("response")
                                                        .dataAsMap(ImmutableMap.of("response", thought))
                                                        .build()
                                                )
                                        )
                                        .build()
                                );
                            listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(finalModelTensors).build());
                        }
                    }

                    lastAction.set(action);
                    lastActionInput.set(actionInput);

                    String toolName = action;
                    if (action != null) {
                        for (String key : tools.keySet()) {
                            if (action.toLowerCase().contains(key.toLowerCase())) {
                                toolName = key;
                            }
                        }
                    }
                    action = toolName;

                    if (action != null && tools.containsKey(action) && inputTools.contains(action)) {
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
                        if (action != null) {
                            ((ActionListener<Object>) nextStepListener).onResponse("no access to this tool: " + action);
                            lastActionResult.set("no access to this tool " + action);
                        } else {
                            String stopWhenNoToolFound = tmpParameters.get("stop_when_no_tool_found");
                            if ("true".equalsIgnoreCase(stopWhenNoToolFound)) {
                                log.info("tools not found, end this cot earlier");
                                int indexOfFinalAnswer = thought.indexOf("Final Answer:");
                                String answer = indexOfFinalAnswer >= 0 ? thought.substring(indexOfFinalAnswer + 13) : thought;
                                if (answer.contains("\n\nQuestion:")) {
                                    answer = answer.substring(0, answer.indexOf("\n\nQuestion:"));
                                }
                                if (answer.contains("\nHuman:")) {
                                    answer = answer.substring(0, answer.indexOf("\nHuman:"));
                                }
                                cotModelTensors
                                    .add(
                                        ModelTensors
                                            .builder()
                                            .mlModelTensors(Arrays.asList(ModelTensor.builder().name("response").result(answer).build()))
                                            .build()
                                    );
                                List<ModelTensors> finalModelTensors = new ArrayList<>();
                                finalModelTensors
                                    .add(
                                        ModelTensors
                                            .builder()
                                            .mlModelTensors(
                                                Arrays
                                                    .asList(
                                                        ModelTensor
                                                            .builder()
                                                            .name("response")
                                                            .dataAsMap(ImmutableMap.of("response", answer))
                                                            .build()
                                                    )
                                            )
                                            .build()
                                    );
                                listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(cotModelTensors).build());
                                return;
                            }
                            ((ActionListener<Object>) nextStepListener).onResponse("tool not found");
                            lastActionResult.set("tool not found");
                        }

                        StringSubstitutor substitutor = new StringSubstitutor(
                            ImmutableMap.of(SCRATCHPAD, scratchpadBuilder.toString()),
                            "${parameters.",
                            "}"
                        );
                        newPrompt.set(substitutor.replace(finalPrompt));
                        tmpParameters.put(PROMPT, newPrompt.get());
                    }
                } else {
                    Object result = output;
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
                                    Arrays
                                        .asList(
                                            ModelTensor
                                                .builder()
                                                .dataAsMap(ImmutableMap.of("response", lastThought.get() + "\nObservation: " + result))
                                                .build()
                                        )
                                )
                                .build()
                        );

                    String toolResponse = tmpParameters.get("prompt.tool_response");
                    StringSubstitutor toolResponseSubstitutor = new StringSubstitutor(
                        ImmutableMap.of("observation", result),
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
                            .response((String) result)
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

                    sessionMsgAnswerBuilder.append("\nObservation: ").append(result);
                    cotModelTensors
                        .add(
                            ModelTensors
                                .builder()
                                .mlModelTensors(
                                    Arrays.asList(ModelTensor.builder().name("response").result(sessionMsgAnswerBuilder.toString()).build())
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
                                            Arrays
                                                .asList(
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

    private String addPrefixSuffixToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> prefixMap = new HashMap<>();
        String prefix = parameters.containsKey(PROMPT_PREFIX) ? parameters.get(PROMPT_PREFIX) : "";
        String suffix = parameters.containsKey(PROMPT_SUFFIX) ? parameters.get(PROMPT_SUFFIX) : "";
        prefixMap.put(PROMPT_PREFIX, prefix);
        prefixMap.put(PROMPT_SUFFIX, suffix);
        StringSubstitutor substitutor = new StringSubstitutor(prefixMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    private String addToolsToPrompt(Map<String, Tool> tools, Map<String, String> parameters, List<String> inputTools, String prompt) {
        StringBuilder toolsBuilder = new StringBuilder();
        StringBuilder toolNamesBuilder = new StringBuilder();

        String toolsPrefix = Optional
            .ofNullable(parameters.get("agent.tools.prefix"))
            .orElse("You have access to the following tools defined in <tools>: \n" + "<tools>\n");
        String toolsSuffix = Optional.ofNullable(parameters.get("agent.tools.suffix")).orElse("</tools>\n");
        String toolPrefix = Optional.ofNullable(parameters.get("agent.tools.tool.prefix")).orElse("<tool>\n");
        String toolSuffix = Optional.ofNullable(parameters.get("agent.tools.tool.suffix")).orElse("\n</tool>\n");
        toolsBuilder.append(toolsPrefix);
        for (String toolName : inputTools) {
            if (!tools.containsKey(toolName)) {
                throw new IllegalArgumentException("Tool [" + toolName + "] not registered for model");
            }
            toolsBuilder.append(toolPrefix).append(toolName).append(": ").append(tools.get(toolName).getDescription()).append(toolSuffix);
            toolNamesBuilder.append(toolName).append(", ");
        }
        toolsBuilder.append(toolsSuffix);
        Map<String, String> toolsPromptMap = new HashMap<>();
        toolsPromptMap.put(TOOL_DESCRIPTIONS, toolsBuilder.toString());
        toolsPromptMap.put(TOOL_NAMES, toolNamesBuilder.substring(0, toolNamesBuilder.length() - 1));

        if (parameters.containsKey(TOOL_DESCRIPTIONS)) {
            toolsPromptMap.put(TOOL_DESCRIPTIONS, parameters.get(TOOL_DESCRIPTIONS));
        }
        if (parameters.containsKey(TOOL_NAMES)) {
            toolsPromptMap.put(TOOL_NAMES, parameters.get(TOOL_NAMES));
        }
        StringSubstitutor substitutor = new StringSubstitutor(toolsPromptMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    private String addIndicesToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> indicesMap = new HashMap<>();
        if (parameters.containsKey(OS_INDICES)) {
            String indices = parameters.get(OS_INDICES);
            List<String> indicesList = gson.fromJson(indices, List.class);
            StringBuilder indicesBuilder = new StringBuilder();
            String indicesPrefix = Optional
                .ofNullable(parameters.get("opensearch_indices.prefix"))
                .orElse("You have access to the following OpenSearch Index defined in <opensearch_indexes>: \n" + "<opensearch_indexes>\n");
            String indicesSuffix = Optional.ofNullable(parameters.get("opensearch_indices.suffix")).orElse("</opensearch_indexes>\n");
            String indexPrefix = Optional.ofNullable(parameters.get("opensearch_indices.index.prefix")).orElse("<index>\n");
            String indexSuffix = Optional.ofNullable(parameters.get("opensearch_indices.index.suffix")).orElse("\n</index>\n");
            indicesBuilder.append(indicesPrefix);
            for (String e : indicesList) {
                indicesBuilder.append(indexPrefix).append(e).append(indexSuffix);
            }
            indicesBuilder.append(indicesSuffix);
            indicesMap.put(OS_INDICES, indicesBuilder.toString());
        } else {
            indicesMap.put(OS_INDICES, "");
        }
        StringSubstitutor substitutor = new StringSubstitutor(indicesMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    private String addExamplesToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> examplesMap = new HashMap<>();
        if (parameters.containsKey(EXAMPLES)) {
            String examples = parameters.get(EXAMPLES);
            List<String> exampleList = gson.fromJson(examples, List.class);
            StringBuilder exampleBuilder = new StringBuilder();
            exampleBuilder.append("EXAMPLES\n--------\n");
            String examplesPrefix = Optional
                .ofNullable(parameters.get("examples.prefix"))
                .orElse("You should follow and learn from examples defined in <examples>: \n" + "<examples>\n");
            String examplesSuffix = Optional.ofNullable(parameters.get("examples.suffix")).orElse("</examples>\n");
            exampleBuilder.append(examplesPrefix);

            String examplePrefix = Optional.ofNullable(parameters.get("examples.example.prefix")).orElse("<example>\n");
            String exampleSuffix = Optional.ofNullable(parameters.get("examples.example.suffix")).orElse("\n</example>\n");
            for (int i = 0; i < exampleList.size(); i++) {
                String example = exampleList.get(i);
                exampleBuilder.append(examplePrefix).append(example).append(exampleSuffix);
            }
            exampleBuilder.append(examplesSuffix);
            examplesMap.put(EXAMPLES, exampleBuilder.toString());
        } else {
            examplesMap.put(EXAMPLES, "");
        }
        StringSubstitutor substitutor = new StringSubstitutor(examplesMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    private String addChatHistoryToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> chatHistoryMap = new HashMap<>();
        String chatHistory = parameters.containsKey(CHAT_HISTORY) ? parameters.get(CHAT_HISTORY) : "";
        chatHistoryMap.put(CHAT_HISTORY, chatHistory);
        parameters.put(CHAT_HISTORY, chatHistory);
        if (chatHistoryMap.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(chatHistoryMap, "${parameters.", "}");
            return substitutor.replace(prompt);
        }
        return prompt;
    }

    private String addContextToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> contextMap = new HashMap<>();
        if (parameters.containsKey(CONTEXT)) {
            contextMap.put(CONTEXT, parameters.get(CONTEXT));
        } else {
            contextMap.put(CONTEXT, "");
        }
        parameters.put(CONTEXT, contextMap.get(CONTEXT));
        if (contextMap.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(contextMap, "${parameters.", "}");
            return substitutor.replace(prompt);
        }
        return prompt;
    }

}
