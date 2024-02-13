/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.MESSAGE_HISTORY_LIMIT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.EXAMPLES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.OS_INDICES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_DESCRIPTIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.TOOL_NAMES;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.LAST_N_INTERACTIONS;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;

public class AgentUtils {

    public static final String SELECTED_TOOLS = "selected_tools";
    public static final String PROMPT_PREFIX = "prompt.prefix";
    public static final String PROMPT_SUFFIX = "prompt.suffix";
    public static final String RESPONSE_FORMAT_INSTRUCTION = "prompt.format_instruction";
    public static final String TOOL_RESPONSE = "prompt.tool_response";
    public static final String PROMPT_CHAT_HISTORY_PREFIX = "prompt.chat_history_prefix";
    public static final String DISABLE_TRACE = "disable_trace";
    public static final String VERBOSE = "verbose";

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
        if (contextMap.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(contextMap, "${parameters.", "}");
            return substitutor.replace(prompt);
        }
        return prompt;
    }

    public static List<String> MODEL_RESPONSE_PATTERNS = List
        .of(
            "\\{\\s*\"thought\":.*?\\s*,\\s*\"action\":.*?\\s*,\\s*\"action_input\":.*?\\}",
            "\\{\\s*\"thought\"\\s*:\\s*\".*?\"\\s*,\\s*\"action\"\\s*:\\s*\".*?\"\\s*,\\s*\"action_input\"\\s*:\\s*\".*?\"\\s*}",
            "\\{\\s*\"thought\"\\s*:\\s*\".*?\"\\s*,\\s*\"final_answer\"\\s*:\\s*\".*?\"\\s*}"
        );

    public static String extractModelResponseJson(String text) {
        return extractModelResponseJson(text, null);
    }

    public static String extractModelResponseJson(String text, List<String> llmResponsePatterns) {
        Pattern pattern1 = Pattern.compile("```json\\s*([\\s\\S]+?)\\s*```");
        Matcher matcher1 = pattern1.matcher(text);

        if (matcher1.find()) {
            return matcher1.group(1);
        } else {
            String matchedPart = findMatchedPart(text, MODEL_RESPONSE_PATTERNS);
            if (matchedPart == null && llmResponsePatterns != null) {
                matchedPart = findMatchedPart(text, llmResponsePatterns);
            }
            if (matchedPart != null) {
                return matchedPart;
            }
            throw new IllegalArgumentException("Model output is invalid");
        }
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
        if (selectedToolsStr != null) {
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

    public static void createTools(
        Map<String, Tool.Factory> toolFactories,
        Map<String, String> params,
        List<MLToolSpec> toolSpecs,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap
    ) {
        for (MLToolSpec toolSpec : toolSpecs) {
            Tool tool = createTool(toolFactories, params, toolSpec);
            tools.put(tool.getName(), tool);
            toolSpecMap.put(tool.getName(), toolSpec);
        }
    }

    public static Tool createTool(Map<String, Tool.Factory> toolFactories, Map<String, String> params, MLToolSpec toolSpec) {
        if (!toolFactories.containsKey(toolSpec.getType())) {
            throw new IllegalArgumentException("Tool not found: " + toolSpec.getType());
        }
        Map<String, String> executeParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        for (String key : params.keySet()) {
            String toolNamePrefix = getToolName(toolSpec) + ".";
            if (key.startsWith(toolNamePrefix)) {
                executeParams.put(key.replace(toolNamePrefix, ""), params.get(key));
            }
        }
        Tool tool = toolFactories.get(toolSpec.getType()).create(executeParams);
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
}
