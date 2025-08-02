/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTOR_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
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
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.EXAMPLES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.FINAL_ANSWER;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.OS_INDICES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.THOUGHT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.THOUGHT_RESPONSE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_DESCRIPTIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_NAMES;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.RESPONSE_FIELD;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.LAST_N_INTERACTIONS;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.McpConnectorExecutor;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.tools.McpSseTool;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.transport.client.Client;

import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

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

    public static final String LLM_RESPONSE_EXCLUDE_PATH = "llm_response_exclude_path";
    public static final String LLM_RESPONSE_FILTER = "llm_response_filter";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TOOL_CALL_ID = "tool_call_id";
    public static final String LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE = "bedrock/converse/claude";
    public static final String LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS = "openai/v1/chat/completions";
    public static final String LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1 = "bedrock/converse/deepseek_r1";

    public static final String TOOL_CALLS_PATH = "tool_calls_path";
    public static final String TOOL_CALLS_TOOL_NAME = "tool_calls.tool_name";
    public static final String TOOL_CALLS_TOOL_INPUT = "tool_calls.tool_input";
    public static final String TOOL_CALL_ID_PATH = "tool_calls.id_path";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";

    public static final String NO_ESCAPE_PARAMS = "no_escape_params";
    public static final String TOOLS = "_tools";
    public static final String TOOL_TEMPLATE = "tool_template";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS = "interaction_template.assistant_tool_calls";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH = "interaction_template.assistant_tool_calls_path";
    public static final String INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH =
        "interaction_template.assistant_tool_calls_exclude_path";
    public static final String INTERACTIONS_PREFIX = "${_interactions.";
    public static final String LLM_FINAL_RESPONSE_POST_FILTER = "llm_final_response_post_filter";
    public static final String LLM_FINISH_REASON_PATH = "llm_finish_reason_path";
    public static final String LLM_FINISH_REASON_TOOL_USE = "llm_finish_reason_tool_use";
    public static final String TOOL_FILTERS_FIELD = "tool_filters";

    // For function calling, do not escape the below params in connector by default
    public static final String DEFAULT_NO_ESCAPE_PARAMS = "_chat_history,_tools,_interactions,tool_configs";

    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String DEFAULT_DATETIME_PREFIX = "Current date and time: ";
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

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

    public static String addToolsToFunctionCalling(
        Map<String, Tool> tools,
        Map<String, String> parameters,
        List<String> inputTools,
        String prompt
    ) {
        String toolTemplate = parameters.get(TOOL_TEMPLATE);
        List<String> toolInfos = new ArrayList<>();
        for (String toolName : inputTools) {
            if (!tools.containsKey(toolName)) {
                throw new IllegalArgumentException("Tool [" + toolName + "] not registered for model");
            }
            Tool tool = tools.get(toolName);
            Map<String, Object> toolParams = new HashMap<>();
            toolParams.put(NAME, tool.getName());
            toolParams.put(DESCRIPTION, tool.getDescription());
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
        parameters.put(TOOLS, String.join(", ", toolInfos));
        return prompt;
    }

    public static String addToolsToPromptString(
        Map<String, Tool> tools,
        Map<String, String> parameters,
        List<String> inputTools,
        String prompt
    ) {
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
        List<String> interactions,
        FunctionCalling functionCalling
    ) {
        Map<String, String> modelOutput = new HashMap<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String llmResponseExcludePath = parameters.get(LLM_RESPONSE_EXCLUDE_PATH);
        if (llmResponseExcludePath != null) {
            dataAsMap = removeJsonPath(dataAsMap, llmResponseExcludePath, true);
        }
        if (dataAsMap.size() == 1 && dataAsMap.containsKey(RESPONSE_FIELD)) {
            String llmReasoningResponse = (String) dataAsMap.get(RESPONSE_FIELD);
            String thoughtResponse = null;
            try {
                thoughtResponse = extractModelResponseJson(llmReasoningResponse, llmResponsePatterns);
                modelOutput.put(THOUGHT_RESPONSE, thoughtResponse);
            } catch (IllegalArgumentException e) {
                modelOutput.put(THOUGHT_RESPONSE, llmReasoningResponse);
                thoughtResponse = llmReasoningResponse;
            }
            parseThoughtResponse(modelOutput, thoughtResponse);
        } else if (parameters.containsKey(TOOL_CALLS_PATH)) {
            modelOutput.put(THOUGHT_RESPONSE, StringUtils.toJson(dataAsMap));
            Object response;
            boolean isToolUseResponse = false;
            try {
                response = JsonPath.read(dataAsMap, parameters.get(LLM_RESPONSE_FILTER));
            } catch (PathNotFoundException e) {
                // If the regular response path fails, try the tool calls path
                response = JsonPath.read(dataAsMap, parameters.get(TOOL_CALLS_PATH));
                isToolUseResponse = true;
            }

            String llmFinishReasonPath = parameters.get(LLM_FINISH_REASON_PATH);
            String llmFinishReason = "";
            if (llmFinishReasonPath.startsWith("_llm_response.")) {// TODO: support _llm_response for all other places
                Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), RESPONSE_FIELD);
                llmFinishReason = JsonPath.read(llmResponse, llmFinishReasonPath.substring("_llm_response.".length()));
            } else {
                llmFinishReason = JsonPath.read(dataAsMap, llmFinishReasonPath);
            }
            if (parameters.get(LLM_FINISH_REASON_TOOL_USE).equalsIgnoreCase(llmFinishReason) || isToolUseResponse) {
                List<Map<String, String>> toolCalls = null;
                try {
                    String toolName = "";
                    String toolInput = "";
                    String toolCallId = "";
                    if (functionCalling != null) {
                        toolCalls = functionCalling.handle(tmpModelTensorOutput, parameters);
                        // TODO: support multiple tool calls here
                        toolName = toolCalls.getFirst().get("tool_name");
                        toolInput = toolCalls.getFirst().get("tool_input");
                        toolCallId = toolCalls.getFirst().get("tool_call_id");
                    } else {
                        String toolCallsPath = parameters.get(TOOL_CALLS_PATH);
                        if (toolCallsPath.startsWith("_llm_response.")) {
                            Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), RESPONSE_FIELD);
                            toolCalls = JsonPath.read(llmResponse, toolCallsPath.substring("_llm_response.".length()));
                        } else {
                            toolCalls = JsonPath.read(dataAsMap, toolCallsPath);
                        }
                        toolName = JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALLS_TOOL_NAME));
                        toolInput = StringUtils.toJson(JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALLS_TOOL_INPUT)));
                        toolCallId = JsonPath.read(toolCalls.get(0), parameters.get(TOOL_CALL_ID_PATH));
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
                        interactions
                            .add(
                                substitute(
                                    parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS),
                                    Map.of("tool_calls", StringUtils.toJson(toolCalls)),
                                    INTERACTIONS_PREFIX
                                )
                            );
                    }
                    modelOutput.put(THOUGHT, "");
                    modelOutput.put(ACTION, toolName);
                    modelOutput.put(ACTION_INPUT, toolInput);
                    modelOutput.put(TOOL_CALL_ID, toolCallId);
                } catch (PathNotFoundException e) {
                    if (StringUtils.isJson(response.toString())) {
                        Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), RESPONSE_FIELD);
                        modelOutput.put(FINAL_ANSWER, StringUtils.toJson(postFilterFinalAnswer(parameters, llmResponse)));
                    } else {
                        modelOutput.put(FINAL_ANSWER, StringUtils.toJson(response));
                    }
                }
            } else {
                if (StringUtils.isJson(response.toString())) {
                    Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), RESPONSE_FIELD);
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

    public static Map<String, ?> removeJsonPath(Map<String, ?> json, String excludePaths, boolean inPlace) {
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        List<String> excludedPath = gson.fromJson(excludePaths, listType);
        return removeJsonPath(json, excludedPath, inPlace);
    }

    public static Map<String, ?> removeJsonPath(Map<String, ?> json, List<String> excludePaths, boolean inPlace) {

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
            Map<String, Object> copy = StringUtils.fromJson(gson.toJson(json), RESPONSE_FIELD);
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
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        List<MLToolSpec> mlToolSpecs = mlAgent.getTools();
        if (mlToolSpecs != null) {
            toolSpecs.addAll(mlToolSpecs);
        }
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
        return toolSpecs;
    }

    public static void getMcpToolSpecs(
        MLAgent mlAgent,
        Client client,
        SdkClient sdkClient,
        Encryptor encryptor,
        ActionListener<List<MLToolSpec>> finalListener
    ) {
        String tenantId = mlAgent.getTenantId();

        String mcpConnectorConfigJSON = (mlAgent.getParameters() != null) ? mlAgent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        // If mcpConnectorConfigJSON is null i.e no config for MCP Connectors, return an empty list
        if (mcpConnectorConfigJSON == null) {
            finalListener.onResponse(Collections.emptyList());
            return;
        }

        Type listType = new TypeToken<List<Map<String, Object>>>() {
        }.getType();
        List<Map<String, Object>> mcpConnectorConfigs = gson.fromJson(mcpConnectorConfigJSON, listType);

        // Use AtomicInteger to track completion of all async operations
        AtomicInteger remainingConnectors = new AtomicInteger(mcpConnectorConfigs.size());
        List<MLToolSpec> finalToolSpecs = Collections.synchronizedList(new ArrayList<>());

        // We make multiple Async calls in for loop, which happen in parallel
        for (Map<String, Object> mcpConnectorConfig : mcpConnectorConfigs) {
            String connectorId = (String) mcpConnectorConfig.get(MCP_CONNECTOR_ID_FIELD);
            List<String> toolFilters = (List<String>) mcpConnectorConfig.get(TOOL_FILTERS_FIELD);

            getMCPToolSpecsFromConnector(connectorId, tenantId, sdkClient, client, encryptor, ActionListener.wrap(mcpToolspecs -> {
                List<MLToolSpec> filteredTools;
                if (toolFilters == null || toolFilters.isEmpty()) {
                    filteredTools = mcpToolspecs;
                } else {
                    filteredTools = new ArrayList<>();
                    List<Pattern> compiledPatterns = toolFilters.stream().map(Pattern::compile).collect(Collectors.toList());

                    for (MLToolSpec toolSpec : mcpToolspecs) {
                        for (Pattern pattern : compiledPatterns) {
                            if (pattern.matcher(toolSpec.getName()).matches()) {
                                filteredTools.add(toolSpec);
                                break;
                            }
                        }
                    }
                }

                finalToolSpecs.addAll(filteredTools);

                // If this is the last connector, send the final response
                if (remainingConnectors.decrementAndGet() == 0) {
                    finalListener.onResponse(finalToolSpecs);
                }
            }, e -> {
                log.error("Error processing connector: " + connectorId, e);
                // Even on error, we need to check if this is the last connector
                if (remainingConnectors.decrementAndGet() == 0) {
                    finalListener.onResponse(finalToolSpecs);
                }
            }));
        }
    }

    private static void getMCPToolSpecsFromConnector(
        String connectorId,
        String tenantId,
        SdkClient sdkClient,
        Client client,
        Encryptor encryptor,
        ActionListener<List<MLToolSpec>> toolListener
    ) {
        getConnector(connectorId, tenantId, sdkClient, client, ActionListener.wrap(connector -> {
            try {
                if (!(connector instanceof McpConnector)) {
                    log.error("Connector with ID " + connectorId + " is not of type McpConnector");
                    toolListener.onResponse(Collections.emptyList());
                    return;
                }
                connector.decrypt("", (credential, tid) -> encryptor.decrypt(credential, tenantId), tenantId);
                McpConnectorExecutor connectorExecutor = MLEngineClassLoader
                    .initInstance(connector.getProtocol(), connector, Connector.class);
                List<MLToolSpec> mcpToolSpecs = connectorExecutor.getMcpToolSpecs();
                toolListener.onResponse(mcpToolSpecs);
            } catch (Exception e) {
                log.error("Failed to get tools from connector: " + connectorId, e);
                toolListener.onResponse(Collections.emptyList());
            }
        }, e -> {
            log.error("Failed to get the MCP Connector: " + connectorId, e);
            toolListener.onResponse(Collections.emptyList());
        }));

    }

    public static void getConnector(
        String connectorId,
        String tenantId,
        SdkClient sdkClient,
        Client client,
        ActionListener<Connector> listener
    ) {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_CONNECTOR_INDEX)
            .id(connectorId)
            .tenantId(tenantId)
            .build();

        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Connector Request, id:{}", connectorId);
                ctx.restore();
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        log.error("Failed to get connector index", cause);
                        listener.onFailure(new OpenSearchStatusException("Failed to find connector", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get ML connector {}", connectorId, cause);
                        listener.onFailure(cause);
                    }
                } else {
                    try {
                        GetResponse gr = r.parser() == null ? null : GetResponse.fromXContent(r.parser());
                        if (gr != null && gr.isExists()) {
                            try (
                                XContentParser parser = createXContentParserFromRegistry(
                                    NamedXContentRegistry.EMPTY,
                                    gr.getSourceAsBytesRef()
                                )
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                Connector connector = Connector.createConnector(parser);
                                listener.onResponse(connector);
                            } catch (Exception e) {
                                log.error("Failed to parse connector:{}", connectorId);
                                listener.onFailure(e);
                            }
                        } else {
                            listener
                                .onFailure(new OpenSearchStatusException("Failed to find connector:" + connectorId, RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                }
            });
        }
    }

    public static XContentParser createXContentParserFromRegistry(NamedXContentRegistry xContentRegistry, BytesReference bytesReference)
        throws IOException {
        return XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, bytesReference, XContentType.JSON);
    }

    public static void createTools(
        Map<String, Tool.Factory> toolFactories,
        Map<String, String> params,
        List<MLToolSpec> toolSpecs,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        MLAgent mlAgent
    ) {
        if (toolSpecs == null) {
            return;
        }
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
        Map<String, Object> runtimeResources = toolSpec.getRuntimeResources();
        if (runtimeResources != null) {
            toolParams.putAll(runtimeResources);
        }
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
            if (toolParams.containsKey("input")) {
                String input = toolParams.get("input");
                StringSubstitutor substitutor = new StringSubstitutor(toolParams, "${parameters.", "}");
                input = substitutor.replace(input);
                toolParams.put("input", input);
            } else {
                toolParams.put("input", actionInput);
            }
        }
        return toolParams;
    }

    public static void cleanUpResource(Map<String, Tool> tools) {
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            Tool tool = entry.getValue();
            if (tool instanceof McpSseTool) {
                // TODO: make this more general, avoid checking specific tool type
                ((McpSseTool) tool).getMcpSyncClient().closeGracefully();
            }
        }
    }

    /**
     * Generates a formatted current date and time string in UTC timezone.
     * 
     * <p>This method returns the current date and time formatted according to the provided pattern.
     * If no format is provided or the format is invalid, it uses the default format:
     * "yyyy-MM-dd'T'HH:mm:ss'Z'" (e.g., "2024-01-15T14:30:00Z").
     * 
     * <p>The method always returns the time in UTC timezone regardless of the system's local timezone.
     * 
     * @param dateFormat The date format pattern to use. Can be null or empty to use the default format.
     *                   Must be a valid {@link java.time.format.DateTimeFormatter} pattern.
     *                   Examples:
     *                   <ul>
     *                     <li>"yyyy-MM-dd HH:mm:ss" → "2024-01-15 14:30:00"</li>
     *                     <li>"EEEE, MMMM d, yyyy 'at' h:mm a z" → "Monday, January 15, 2024 at 2:30 PM UTC"</li>
     *                     <li>"MM/dd/yyyy h:mm a" → "01/15/2024 2:30 PM"</li>
     *                     <li>"yyyy-MM-dd'T'HH:mm:ss'Z'" → "2024-01-15T14:30:00Z"</li>
     *                   </ul>
     * @return A string containing the current date and time prefixed with "Current date and time: ".
     *         If the provided format is invalid, a warning is logged and the default format is used.
     * @see java.time.format.DateTimeFormatter
     * @see java.time.ZoneId
     */
    public static String getCurrentDateTime(String dateFormat) {
        Instant now = Instant.now();
        DateTimeFormatter formatter;

        if (!isBlank(dateFormat)) {
            try {
                formatter = DateTimeFormatter.ofPattern(dateFormat).withZone(UTC_ZONE);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid date format provided: {}. Using default format.", dateFormat);
                formatter = DateTimeFormatter.ofPattern(DEFAULT_DATETIME_FORMAT).withZone(UTC_ZONE);
            }
        } else {
            formatter = DateTimeFormatter.ofPattern(DEFAULT_DATETIME_FORMAT).withZone(UTC_ZONE);
        }

        return DEFAULT_DATETIME_PREFIX + formatter.format(now);
    }
}
