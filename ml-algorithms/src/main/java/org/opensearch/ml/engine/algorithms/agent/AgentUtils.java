package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.*;

import java.util.*;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.spi.tools.Tool;

public class AgentUtils {

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

    public static String addPrefixSuffixToPrompt(Map<String, String> parameters, String prompt) {
        Map<String, String> prefixMap = new HashMap<>();
        String prefix = parameters.containsKey(PROMPT_PREFIX) ? parameters.get(PROMPT_PREFIX) : "";
        String suffix = parameters.containsKey(PROMPT_SUFFIX) ? parameters.get(PROMPT_SUFFIX) : "";
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
        String chatHistory = parameters.containsKey(CHAT_HISTORY) ? parameters.get(CHAT_HISTORY) : "";
        chatHistoryMap.put(CHAT_HISTORY, chatHistory);
        parameters.put(CHAT_HISTORY, chatHistory);
        if (chatHistoryMap.size() > 0) {
            StringSubstitutor substitutor = new StringSubstitutor(chatHistoryMap, "${parameters.", "}");
            return substitutor.replace(prompt);
        }
        return prompt;
    }

    public static String addContextToPrompt(Map<String, String> parameters, String prompt) {
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
