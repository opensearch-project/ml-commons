/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.MESSAGE_HISTORY_LIMIT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.ACTION;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.ACTION_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.DEFAULT_NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.EXAMPLES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.FINAL_ANSWER;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.OS_INDICES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.THOUGHT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.THOUGHT_RESPONSE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_DESCRIPTIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_NAMES;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.LAST_N_INTERACTIONS;

import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.ClientMcpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AgentUtils {

    public static final String SELECTED_TOOLS = "selected_tools";
    public static final String PROMPT_PREFIX = "prompt.prefix";
    public static final String PROMPT_SUFFIX = "prompt.suffix";
    public static final String RESPONSE_FORMAT_INSTRUCTION = "prompt.format_instruction";
    public static final String TOOL_RESPONSE = "prompt.tool_response";
    public static final String PROMPT_CHAT_HISTORY_PREFIX = "prompt.chat_history_prefix";
    public static final String DISABLE_TRACE = "disable_trace";
    public static final String VERBOSE = "verbose";
    public static final String LLM_GEN_INPUT = "llm_generated_input";
    public static final String TOOL_CALLS_PATH = "tool_calls_path";
    public static final String TOOL_CALLS_TOOL_NAME = "tool_calls.tool_name";
    public static final String TOOL_CALLS_TOOL_INPUT = "tool_calls.tool_input";
    public static final String TOOL_CALL_ID_PATH = "tool_calls.id_path";

    public static final String NO_ESCAPE_PARAMS = "no_escape_params";
    public static final String TOOLS = "_tools";
    public static final String TOOL_TEMPLATE = "tool_template";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS = "interaction_template.assistant_tool_calls";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH = "interaction_template.assistant_tool_calls_path";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH = "interaction_template.assistant_tool_calls_exclude_path";
    public static final String INTERACTIONS_PREFIX = "${_interactions.";
    public static final String LLM_RESPONSE_FILTER = "llm_response_filter";
    public static final String LLM_FINAL_RESPONSE_POST_FILTER = "llm_final_response_post_filter";
    public static final String LLM_FINISH_REASON_PATH = "llm_finish_reason_path";
    public static final String LLM_FINISH_REASON_TOOL_USE = "llm_finish_reason_tool_use";
    public static final String LLM_RESPONSE_EXCLUDE_PATH = "llm_response_exclude_path";

    public static String addExamplesToPrompt(Map<String, String> parameters, String prompt) {
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
            for (String example : exampleList) {
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

    public static String addPrefixSuffixToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> prefixMap = new HashMap<>();
        String prefix = parameters.getOrDefault(PROMPT_PREFIX, "");
        String suffix = parameters.getOrDefault(PROMPT_SUFFIX, "");
        prefixMap.put(PROMPT_PREFIX, prefix);
        prefixMap.put(PROMPT_SUFFIX, suffix);
        StringSubstitutor substitutor = new StringSubstitutor(prefixMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    public static String addToolsToPrompt(Map<String, Tool> tools, Map<String, String> parameters, List<String> inputTools, String prompt) {
        if (parameters.containsKey(TOOL_TEMPLATE)) {
            return addToolsToFunctionCalling(tools, parameters, inputTools, prompt);
        } else {
            return addToolsToPromptString(tools, parameters, inputTools, prompt);
        }
    }

    public static String addToolsToFunctionCalling(Map<String, Tool> tools, Map<String, String> parameters, List<String> inputTools, String prompt) {
        String toolTemplate = parameters.get("tool_template");
        List<String> toolInfos = new ArrayList<>();
        for (String toolName : inputTools) {
            if (!tools.containsKey(toolName)) {
                throw new IllegalArgumentException("Tool [" + toolName + "] not registered for model");
            }
            Tool tool = tools.get(toolName);
            Map<String, Object> toolParams = new HashMap<>();
            toolParams.put("name", tool.getName());
            toolParams.put("description", tool.getDescription());
            Map<String, ?> attributes = tool.getAttributes();
            if (attributes != null) {
                for (String key : attributes.keySet()) {
                    toolParams.put("attributes." + key, attributes.get(key));
                }
            }
            StringSubstitutor substitutor = new StringSubstitutor(toolParams, "${tool.", "}");
            String chatQuestionMessage = substitutor.replace(toolTemplate);
            toolInfos.add(chatQuestionMessage);
        }
        parameters.put(TOOLS, String.join(", ", toolInfos) );
        return prompt;
    }

    public static String addToolsToPromptString(Map<String, Tool> tools, Map<String, String> parameters, List<String> inputTools, String prompt) {
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

    public static String addIndicesToPrompt(Map<String, String> parameters, String prompt) {
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

    public static String addChatHistoryToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> chatHistoryMap = new HashMap<>();
        String chatHistory = parameters.getOrDefault(CHAT_HISTORY, "");
        chatHistoryMap.put(CHAT_HISTORY, chatHistory);
        parameters.put(CHAT_HISTORY, chatHistory);
        StringSubstitutor substitutor = new StringSubstitutor(chatHistoryMap, "${parameters.", "}");
        return substitutor.replace(prompt);
    }

    public static String addContextToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put(CONTEXT, parameters.getOrDefault(CONTEXT, ""));
        parameters.put(CONTEXT, contextMap.get(CONTEXT));
        if (!contextMap.isEmpty()) {
            StringSubstitutor substitutor = new StringSubstitutor(contextMap, "${parameters.", "}");
            return substitutor.replace(prompt);
        }
        return prompt;
    }

    public static List<String> MODEL_RESPONSE_PATTERNS = List
        .of("\\{\\s*(\"(thought|action|action_input|final_answer)\"\\s*:\\s*\".*?\"\\s*,?\\s*)+\\}");

    public static String extractModelResponseJson(String text) {
        return extractModelResponseJson(text, null);
    }

    public static Map<String, String> parseLLMOutput(
        Map<String, String> parameters,
        ModelTensorOutput tmpModelTensorOutput,
        List<String> llmResponsePatterns,
        Set<String> inputTools,
        List<String> interactions
    ) {
        Map<String, String> modelOutput = new HashMap<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String llmResponseExcludePath = parameters.get(LLM_RESPONSE_EXCLUDE_PATH);
        if (llmResponseExcludePath != null) {
            dataAsMap = removeJsonPath(dataAsMap, llmResponseExcludePath, true);
        }
        if (dataAsMap.size() == 1 && dataAsMap.containsKey("response")) {
            String llmReasoningResponse = (String) dataAsMap.get("response");
            String thoughtResponse = null;
            try {
                thoughtResponse = extractModelResponseJson(llmReasoningResponse, llmResponsePatterns);
                modelOutput.put(THOUGHT_RESPONSE, thoughtResponse);
            } catch (IllegalArgumentException e) {
                modelOutput.put(THOUGHT_RESPONSE, llmReasoningResponse);
                thoughtResponse = llmReasoningResponse;
            }
            parseThoughtResponse(modelOutput, thoughtResponse);
        } else if (parameters.containsKey("tool_calls_path")) {
            modelOutput.put(THOUGHT_RESPONSE, StringUtils.toJson(dataAsMap));
            Object response = JsonPath.read(dataAsMap, parameters.get(LLM_RESPONSE_FILTER));

            String llmFinishReasonPath = parameters.get(LLM_FINISH_REASON_PATH);
            String llmFinishReason = "";
            if (llmFinishReasonPath.startsWith("_llm_response.")) {//TODO: support _llm_response for all other places
                Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), "response");
                llmFinishReason = JsonPath.read(llmResponse, llmFinishReasonPath.substring("_llm_response.".length()));
            } else {
                llmFinishReason = JsonPath.read(dataAsMap, llmFinishReasonPath);
            }
            if (parameters.get(LLM_FINISH_REASON_TOOL_USE).equalsIgnoreCase(llmFinishReason)) {
                List toolCalls = null;
                try {
                    String toolCallsPath = parameters.get(TOOL_CALLS_PATH);
                    if (toolCallsPath.startsWith("_llm_response.")) {
                        Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), "response");
                        toolCalls = JsonPath.read(llmResponse, toolCallsPath.substring("_llm_response.".length()));
                    } else {
                        toolCalls = JsonPath.read(dataAsMap, toolCallsPath);
                    }
                    String toolCallsMsgPath = parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH);
                    String toolCallsMsgExcludePath = parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH);
                    if (toolCallsMsgPath != null) {
                        if (toolCallsMsgExcludePath != null) {

                            Map<String, ?> newDataAsMap = removeJsonPath(dataAsMap, toolCallsMsgExcludePath, false);
                            Object toolCallsMsg = JsonPath.read(newDataAsMap, toolCallsMsgPath);
                            interactions.add(StringUtils.toJson(toolCallsMsg));
                        } else {
                            Object toolCallsMsg = JsonPath.read(dataAsMap, toolCallsMsgPath);
                            interactions.add(StringUtils.toJson(toolCallsMsg));
                        }

                    } else {
                        interactions.add(substitute(parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS), Map.of("tool_calls", StringUtils.toJson(toolCalls)), INTERACTIONS_PREFIX));
                    }
                    String toolName = JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALLS_TOOL_NAME));
                    String toolInput = StringUtils.toJson(JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALLS_TOOL_INPUT)));
                    String toolCallId = JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALL_ID_PATH));
                    modelOutput.put(THOUGHT, "");
                    modelOutput.put(ACTION, toolName);
                    modelOutput.put(ACTION_INPUT, toolInput);
                    modelOutput.put("tool_call_id", toolCallId);
                } catch (PathNotFoundException e) {
                    if (StringUtils.isJson(response.toString())) {
                        Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), "response");
                        modelOutput.put(FINAL_ANSWER, StringUtils.toJson(postFilterFinalAnswer(parameters, llmResponse)));
                    } else {
                        modelOutput.put(FINAL_ANSWER, StringUtils.toJson(response));
                    }
                }
            } else {
                if (StringUtils.isJson(response.toString())) {
                    Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), "response");
                    modelOutput.put(FINAL_ANSWER, StringUtils.toJson(postFilterFinalAnswer(parameters, llmResponse)));
                } else {
                    modelOutput.put(FINAL_ANSWER, StringUtils.toJson(response));
                }

            }
        } else {
            extractParams(modelOutput, dataAsMap, THOUGHT);
            extractParams(modelOutput, dataAsMap, ACTION);
            extractParams(modelOutput, dataAsMap, ACTION_INPUT);
            extractParams(modelOutput, dataAsMap, FINAL_ANSWER);
            try {
                modelOutput.put(THOUGHT_RESPONSE, StringUtils.toJson(dataAsMap));
            } catch (Exception e) {
                log.warn("Failed to parse model response", e);
            }
        }
        String action = modelOutput.get(ACTION);
        if (action != null) {
            String matchedTool = getMatchedTool(inputTools, action);
            if (matchedTool != null) {
                modelOutput.put(ACTION, matchedTool);
            } else {
                modelOutput.remove(ACTION);
            }
        }
        if (!modelOutput.containsKey(ACTION) && !modelOutput.containsKey(FINAL_ANSWER)) {
            modelOutput.put(FINAL_ANSWER, modelOutput.get(THOUGHT_RESPONSE));
        }
        return modelOutput;
    }

    private static String postFilterFinalAnswer(Map<String, String> parameters, Map<String, Object> llmResponse) {
        String filter = parameters.get(LLM_FINAL_RESPONSE_POST_FILTER);
        if (filter != null) {
            return StringUtils.toJson(JsonPath.read(llmResponse, filter));
        }
        return StringUtils.toJson(llmResponse);
    }

    private static Map<String, ?> removeJsonPath(Map<String, ?> json, String excludePaths, boolean inPlace) {
        Type listType = new TypeToken<List<String>>(){}.getType();
        List<String> excludedPath = gson.fromJson(excludePaths, listType);
        return removeJsonPath(json, excludedPath, inPlace);
    }

    private static Map<String, ?> removeJsonPath(Map<String, ?> json, List<String> excludePaths, boolean inPlace) {

        if (json == null || excludePaths == null || excludePaths.isEmpty()) {
            return json;
        }
        if (inPlace) {
            DocumentContext context = JsonPath.parse(json);
            for (String path : excludePaths) {
                try {
                    context.delete(path);
                } catch (PathNotFoundException e) {
                    log.warn("can't find path: {}", path);
                }
            }
            return json;
        } else {
            Map<String, Object> copy = StringUtils.fromJson(gson.toJson(json), "response");
            DocumentContext context = JsonPath.parse(copy);
            for (String path : excludePaths) {
                try {
                    context.delete(path);
                } catch (PathNotFoundException e) {
                    log.warn("can't find path: {}", path);
                }
            }
            return context.json();
        }
    }

    public static String substitute(String template, Map<String, String> params, String prefix) {
        StringSubstitutor substitutor = new StringSubstitutor(params, prefix, "}");
        return substitutor.replace(template);
    }

    public static String getMatchedTool(Collection<String> tools, String action) {
        for (String tool : tools) {
            if (action.toLowerCase(Locale.ROOT).contains(tool.toLowerCase(Locale.ROOT))) {
                return tool;
            }
        }
        return null;
    }

    public static void extractParams(Map<String, String> modelOutput, Map<String, ?> dataAsMap, String paramName) {
        if (dataAsMap.containsKey(paramName)) {
            modelOutput.put(paramName, toJson(dataAsMap.get(paramName)));
        }
    }

    public static String extractModelResponseJson(String text, List<String> llmResponsePatterns) {
        if (text.contains("```json")) {
            text = text.substring(text.indexOf("```json") + "```json".length());
            if (text.contains("```")) {
                text = text.substring(0, text.lastIndexOf("```"));
            }
        }
        text = text.trim();
        if (isJson(text)) {
            return text;
        }
        String matchedPart = null;
        if (llmResponsePatterns != null) {
            matchedPart = findMatchedPart(text, llmResponsePatterns);
            if (matchedPart != null) {
                return matchedPart;
            }
        }
        matchedPart = findMatchedPart(text, MODEL_RESPONSE_PATTERNS);
        if (matchedPart != null) {
            return matchedPart;
        }
        throw new IllegalArgumentException("Model output is invalid");
    }

    public static void parseThoughtResponse(Map<String, String> modelOutput, String thoughtResponse) {
        if (thoughtResponse != null) {
            if (isJson(thoughtResponse)) {
                modelOutput.putAll(getParameterMap(gson.fromJson(thoughtResponse, Map.class)));
            } else {// sometimes LLM return invalid json response
                String thought = extractThought(thoughtResponse);
                String action = extractAction(thoughtResponse);
                String actionInput = extractActionInput(thoughtResponse);
                String finalAnswer = extractFinalAnswer(thoughtResponse);
                if (thought != null) {
                    modelOutput.put(THOUGHT, thought);
                }
                if (action != null) {
                    modelOutput.put(ACTION, action);
                }
                if (actionInput != null) {
                    modelOutput.put(ACTION_INPUT, actionInput);
                }
                if (finalAnswer != null) {
                    modelOutput.put(FINAL_ANSWER, finalAnswer);
                }
            }
        }
    }

    public static String extractFinalAnswer(String text) {
        String result = null;
        if (text.contains("\"final_answer\"")) {
            String pattern = "\"final_answer\"\\s*:\\s*\"(.*)\"";
            Pattern jsonBlockPattern = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher jsonBlockMatcher = jsonBlockPattern.matcher(text);
            if (jsonBlockMatcher.find()) {
                result = jsonBlockMatcher.group(1);
            }
        }
        return result;
    }

    public static String extractThought(String text) {
        String result = null;
        if (text.contains("\"thought\"")) {
            String pattern = "\"thought\"\\s*:\\s*\"(.*?)\"\\s*,\\s*[\"final_answer\"|\"action\"]";
            Pattern jsonBlockPattern = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher jsonBlockMatcher = jsonBlockPattern.matcher(text);
            if (jsonBlockMatcher.find()) {
                result = jsonBlockMatcher.group(1);
            }
        }
        return result;
    }

    public static String extractAction(String text) {
        String result = null;
        if (text.contains("\"action\"")) {
            String pattern = "\"action\"\\s*:\\s*\"(.*?)(?:\"action_input\"|$)";
            Pattern jsonBlockPattern = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher jsonBlockMatcher = jsonBlockPattern.matcher(text);
            if (jsonBlockMatcher.find()) {
                result = jsonBlockMatcher.group(1);
            }
        }
        return result;
    }

    public static String extractActionInput(String text) {
        String result = null;
        if (text.contains("\"action_input\"")) {
            String pattern = "\"action_input\"\\s*:\\s*\"((?:[^\\\"]|\\\")*)\"";
            Pattern jsonBlockPattern = Pattern.compile(pattern, Pattern.DOTALL); // Add Pattern.DOTALL to match across newlines
            Matcher jsonBlockMatcher = jsonBlockPattern.matcher(text);
            if (jsonBlockMatcher.find()) {
                result = jsonBlockMatcher.group(1);
                result = result.replace("\\\"", "\"");
            }
        }
        return result;
    }

    public static String findMatchedPart(String text, List<String> patternList) {
        for (String p : patternList) {
            Pattern pattern = Pattern.compile(p);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    @SuppressWarnings("removal")
    public static String outputToOutputString(Object output) throws PrivilegedActionException {
        String outputString;
        if (output instanceof ModelTensorOutput) {
            ModelTensor outputModel = ((ModelTensorOutput) output).getMlModelOutputs().get(0).getMlModelTensors().get(0);
            if (outputModel.getDataAsMap() != null) {
                outputString = AccessController
                    .doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(outputModel.getDataAsMap()));
            } else {
                outputString = outputModel.getResult();
            }
        } else if (output instanceof String) {
            outputString = (String) output;
        } else {
            outputString = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(output));
        }
        return outputString;
    }

    public static int getMessageHistoryLimit(Map<String, String> params) {
        String messageHistoryLimitStr = params.get(MESSAGE_HISTORY_LIMIT);
        return messageHistoryLimitStr != null ? Integer.parseInt(messageHistoryLimitStr) : LAST_N_INTERACTIONS;
    }

    public static String getToolName(MLToolSpec toolSpec) {
        return toolSpec.getName() != null ? toolSpec.getName() : toolSpec.getType();
    }

    public static List<MLToolSpec> getMlToolSpecs(MLAgent mlAgent, Map<String, String> params) {
        String selectedToolsStr = params.get(SELECTED_TOOLS);
        List<MLToolSpec> toolSpecs = mlAgent.getTools();
        if (!Strings.isEmpty(selectedToolsStr)) {
            List<String> selectedTools = gson.fromJson(selectedToolsStr, List.class);
            Map<String, MLToolSpec> toolNameSpecMap = new HashMap<>();
            for (MLToolSpec toolSpec : toolSpecs) {
                toolNameSpecMap.put(getToolName(toolSpec), toolSpec);
            }
            List<MLToolSpec> selectedToolSpecs = new ArrayList<>();
            for (String tool : selectedTools) {
                if (toolNameSpecMap.containsKey(tool)) {
                    selectedToolSpecs.add(toolNameSpecMap.get(tool));
                }
            }
            toolSpecs = selectedToolSpecs;
        }
        List<MLToolSpec> mcpToolSpecs = getMcpToolSpecs(mlAgent.getParameters());
        toolSpecs.addAll(mcpToolSpecs);
        return toolSpecs;
    }

    public static List<MLToolSpec> getMcpToolSpecs(Map<String, String> params) {
        List<MLToolSpec> mcpToolSpecs = new ArrayList<>();
        if (!params.containsKey("mcp_server_url")) {
            return mcpToolSpecs;
        }
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                String mcpServerUrl = params.get("mcp_server_url");

                // Create a privileged executor service
                ExecutorService executor = Executors.newCachedThreadPool(r -> {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    return thread;
                });

                // Build HTTP client with proper configuration
                HttpClient httpClient = HttpClient.newBuilder()
                        .executor(executor)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                // Create transport with the HTTP client
                ClientMcpTransport transport = new HttpClientSseClientTransport(
                        httpClient,
                        mcpServerUrl,
                        new ObjectMapper()
                );

                // Create and initialize client
                McpSyncClient client = McpClient.sync(transport)
                        .requestTimeout(Duration.ofSeconds(30))
                        .capabilities(McpSchema.ClientCapabilities.builder()
                                .roots(false)
                                .sampling()
                                .build())
                        .sampling(request -> new McpSchema.CreateMessageResult(
                                McpSchema.Role.USER,
                                new McpSchema.TextContent("test"),
                                "Claude3.7",
                                McpSchema.CreateMessageResult.StopReason.END_TURN))
                        .build();

                client.initialize();
                McpSchema.ListToolsResult tools = client.listTools();

                // Process the results
                Gson gson = new Gson();
                String json = gson.toJson(tools, McpSchema.ListToolsResult.class);
                Map<?, ?> map = gson.fromJson(json, Map.class);

                List<?> mcpTools = (List<?>) map.get("tools");
                Set<String> basicMetaFields = Set.of("name", "description");

                for (Object tool : mcpTools) {
                    Map<String, String> attributes = new HashMap<>();
                    Map<?, ?> toolMap = (Map<?, ?>) tool;

                    for (Object key : toolMap.keySet()) {
                        String keyStr = (String) key;
                        if (!basicMetaFields.contains(keyStr)) {
                            //TODO: change to more flexible way
                            attributes.put("input_schema", StringUtils.toJson(toolMap.get(keyStr)));
                        }
                    }

                    MLToolSpec mlToolSpec = MLToolSpec.builder()
                            .type("McpSseTool")
                            .name(toolMap.get("name").toString())
                            .description(StringUtils.processTextDoc(toolMap.get("description").toString()))
                            .attributes(attributes)
                            .build();
                    mlToolSpec.addRuntimeResource("mcp_client", client);
                    mcpToolSpecs.add(mlToolSpec);
                }
                return null;
            });

            return mcpToolSpecs;
        } catch (PrivilegedActionException e) {
            log.error("Failed to get MCP tools", e);
            return mcpToolSpecs;
        }
    }

    public static void createTools(
        Map<String, Tool.Factory> toolFactories,
        Map<String, String> params,
        List<MLToolSpec> toolSpecs,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        MLAgent mlAgent
    ) {
        for (MLToolSpec toolSpec : toolSpecs) {
            Tool tool = createTool(toolFactories, params, toolSpec, mlAgent.getTenantId());
            tools.put(tool.getName(), tool);
            if (toolSpec.getAttributes() != null) {
                if (tool.getAttributes() == null) {
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.putAll(toolSpec.getAttributes());
                    tool.setAttributes(attributes);
                } else {
                    tool.getAttributes().putAll(toolSpec.getAttributes());
                }
            }
            toolSpecMap.put(tool.getName(), toolSpec);
        }
    }

    public static Tool createTool(
        Map<String, Tool.Factory> toolFactories,
        Map<String, String> params,
        MLToolSpec toolSpec,
        String tenantId
    ) {
        if (!toolFactories.containsKey(toolSpec.getType())) {
            throw new IllegalArgumentException("Tool not found: " + toolSpec.getType());
        }
        Map<String, String> executeParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        executeParams.put(TENANT_ID_FIELD, tenantId);
        for (String key : params.keySet()) {
            String toolNamePrefix = getToolName(toolSpec) + ".";
            if (key.startsWith(toolNamePrefix)) {
                executeParams.put(key.replace(toolNamePrefix, ""), params.get(key));
            }
        }
        Map<String, Object> toolParams = new HashMap<>();
        toolParams.putAll(executeParams);
        toolParams.putAll(toolSpec.getRuntimeResources());
        Tool tool = toolFactories.get(toolSpec.getType()).create(toolParams);
        String toolName = getToolName(toolSpec);
        tool.setName(toolName);

        if (toolSpec.getDescription() != null) {
            tool.setDescription(toolSpec.getDescription());
        }
        if (params.containsKey(toolName + ".description")) {
            tool.setDescription(params.get(toolName + ".description"));
        }

        return tool;
    }

    public static List<String> getToolNames(Map<String, Tool> tools) {
        final List<String> inputTools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = entry.getValue().getName();
            inputTools.add(toolName);
        }
        return inputTools;
    }

    public static Map<String, String> constructToolParams(
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        String question,
        AtomicReference<String> lastActionInput,
        String action,
        String actionInput
    ) {
        Map<String, String> toolParams = new HashMap<>();
        Map<String, String> toolSpecParams = toolSpecMap.get(action).getParameters();
        Map<String, String> toolSpecConfigMap = toolSpecMap.get(action).getConfigMap();
        if (toolSpecParams != null) {
            toolParams.putAll(toolSpecParams);
        }
        if (toolSpecConfigMap != null) {
            toolParams.putAll(toolSpecConfigMap);
        }
        toolParams.put(LLM_GEN_INPUT, actionInput);
        if (isJson(actionInput)) {
            Map<String, String> params = getParameterMap(gson.fromJson(actionInput, Map.class));
            toolParams.putAll(params);
        }
        if (tools.get(action).useOriginalInput()) {
            toolParams.put("input", question);
            lastActionInput.set(question);
        } else if (toolSpecConfigMap != null && toolSpecConfigMap.containsKey("input")) {
            String input = toolSpecConfigMap.get("input");
            StringSubstitutor substitutor = new StringSubstitutor(toolParams, "${parameters.", "}");
            input = substitutor.replace(input);
            toolParams.put("input", input);
            if (isJson(input)) {
                Map<String, String> params = getParameterMap(gson.fromJson(input, Map.class));
                toolParams.putAll(params);
            }
        } else {
            toolParams.put("input", actionInput);
        }
        return toolParams;
    }

    public static void constructLLMInterfaceParams(String llmInterface, Map<String, String> params) {
        if (org.apache.commons.lang3.StringUtils.isBlank(llmInterface)) {
            log.debug("no llm interface");
            return;
        }

        if ("openai/v1/chat/completions".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
            }
            params.put(LLM_RESPONSE_FILTER, "$.choices[0].message.content");

            params.put("tool_template", "{\"type\": \"function\", \"function\": { \"name\": \"${tool.name}\", \"description\": \"${tool.description}\", \"parameters\": ${tool.attributes.input_schema}, \"strict\": ${tool.attributes.strict:-false} } }");
            params.put("tool_calls_path", "$.choices[0].message.tool_calls");
            params.put("tool_calls.tool_name", "function.name");
            params.put("tool_calls.tool_input", "function.arguments");
            params.put("tool_calls.id_path", "id");

            params.put("tool_choice", "auto");
            params.put("parallel_tool_calls", "false");

            params.put("interaction_template.assistant_tool_calls_path", "$.choices[0].message");
            params.put("interaction_template.tool_response", "{ \"role\": \"tool\", \"tool_call_id\": \"${_interactions.tool_call_id}\", \"content\": \"${_interactions.tool_response}\" }");

            params.put("chat_history_template.user_question", "{\"role\": \"user\",\"content\": \"${_chat_history.message.question}\"}");
            params.put("chat_history_template.ai_response", "{\"role\": \"assistant\",\"content\": \"${_chat_history.message.response}\"}");

            params.put("llm_finish_reason_path", "$.choices[0].finish_reason");
            params.put("llm_finish_reason_tool_use", "tool_calls");
            params.put("llm_response_filter", "$.choices[0].message.content");
        } else if ("bedrock/converse/claude".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS + ",tool_configs");
            }
            params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");

            params.put("tool_template", "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}");
            params.put("tool_calls_path", "$.output.message.content[*].toolUse");
            params.put("tool_calls.tool_name", "name");
            params.put("tool_calls.tool_input", "input");
            params.put("tool_calls.id_path", "toolUseId");
            params.put("tool_configs", ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}");

            params.put("interaction_template.assistant_tool_calls_path", "$.output.message");
            params.put("interaction_template.tool_response", "{\"role\":\"user\",\"content\":[{\"toolResult\":{\"toolUseId\":\"${_interactions.tool_call_id}\",\"content\":[{\"text\":\"${_interactions.tool_response}\"}]}}]}");

            params.put("chat_history_template.user_question", "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}");
            params.put("chat_history_template.ai_response", "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}");

            params.put("llm_finish_reason_path", "$.stopReason");
            params.put("llm_finish_reason_tool_use", "tool_use");
        } else if ("bedrock/converse/deepseek_r1".equalsIgnoreCase(llmInterface)) {
            if (!params.containsKey(NO_ESCAPE_PARAMS)) {
                params.put(NO_ESCAPE_PARAMS, "_chat_history,_interactions");
            }
            params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");
            params.put("llm_final_response_post_filter", "$.message.content[0].text");

            params.put("tool_template", "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}");
            params.put("tool_calls_path", "_llm_response.tool_calls");
            params.put("tool_calls.tool_name", "tool_name");
            params.put("tool_calls.tool_input", "input");
            params.put("tool_calls.id_path", "id");

            params.put("interaction_template.assistant_tool_calls_path", "$.output.message");
            params.put("interaction_template.assistant_tool_calls_exclude_path", "[ \"$.output.message.content[?(@.reasoningContent)]\" ]");
            params.put("interaction_template.tool_response", "{\"role\":\"user\",\"content\":[ {\"text\":\"{\\\"tool_call_id\\\":\\\"${_interactions.tool_call_id}\\\",\\\"tool_result\\\": \\\"${_interactions.tool_response}\\\"\"} ]}");

            params.put("chat_history_template.user_question", "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}");
            params.put("chat_history_template.ai_response", "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}");

            params.put("llm_finish_reason_path", "_llm_response.stop_reason");
            params.put("llm_finish_reason_tool_use", "tool_use");
        }
    }
}
