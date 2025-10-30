package org.opensearch.ml.engine.agents;

import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.QUESTION;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.INTERACTIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.SYSTEM_PROMPT_FIELD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.hooks.EnhancedPostToolEvent;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.hooks.PreLLMEvent;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;

public class AgentContextUtil {
    private static final Logger log = LogManager.getLogger(AgentContextUtil.class);

    public static ContextManagerContext buildContextManagerContextForToolOutput(
        String toolOutput,
        Map<String, String> parameters,
        List<MLToolSpec> toolSpecs,
        Memory memory
    ) {
        ContextManagerContext.ContextManagerContextBuilder builder = ContextManagerContext.builder();

        String systemPrompt = parameters.get(SYSTEM_PROMPT_FIELD);
        if (systemPrompt != null) {
            builder.systemPrompt(systemPrompt);
        }

        String userPrompt = parameters.get(QUESTION);
        if (userPrompt != null) {
            builder.userPrompt(userPrompt);
        }

        if (toolSpecs != null) {
            builder.toolConfigs(toolSpecs);
        }

        Map<String, String> contextParameters = new HashMap<>();
        contextParameters.putAll(parameters);
        contextParameters.put("_current_tool_output", toolOutput);
        builder.parameters(contextParameters);

        return builder.build();
    }

    public static Object extractProcessedToolOutput(ContextManagerContext context) {
        if (context.getParameters() != null) {
            return context.getParameters().get("_current_tool_output");
        }
        return null;
    }

    public static Object extractFromContext(ContextManagerContext context, String key) {
        if (context.getParameters() != null) {
            return context.getParameters().get(key);
        }
        return null;
    }

    public static ContextManagerContext buildContextManagerContext(
        Map<String, String> parameters,
        List<String> interactions,
        List<MLToolSpec> toolSpecs,
        Memory memory
    ) {
        ContextManagerContext.ContextManagerContextBuilder builder = ContextManagerContext.builder();

        String systemPrompt = parameters.get(SYSTEM_PROMPT_FIELD);
        if (systemPrompt != null) {
            builder.systemPrompt(systemPrompt);
        }

        String userPrompt = parameters.get(QUESTION);
        if (userPrompt != null) {
            builder.userPrompt(userPrompt);
        }

        if (memory instanceof ConversationIndexMemory) {
            String chatHistory = parameters.get(CHAT_HISTORY);
            if (chatHistory != null) {
                List<Interaction> chatHistoryList = new ArrayList<>();
                builder.chatHistory(chatHistoryList);
            }
        }

        if (toolSpecs != null) {
            builder.toolConfigs(toolSpecs);
        }

        List<Map<String, Object>> toolInteractions = new ArrayList<>();
        if (interactions != null) {
            for (String interaction : interactions) {
                Map<String, Object> toolInteraction = new HashMap<>();
                toolInteraction.put("output", interaction);
                toolInteractions.add(toolInteraction);
            }
        }
        builder.toolInteractions(toolInteractions);

        Map<String, String> contextParameters = new HashMap<>();
        contextParameters.putAll(parameters);
        builder.parameters(contextParameters);

        return builder.build();
    }

    public static Object emitPostToolHook(
        Object toolOutput,
        Map<String, String> parameters,
        List<MLToolSpec> toolSpecs,
        Memory memory,
        HookRegistry hookRegistry
    ) {
        if (hookRegistry != null) {
            try {
                ContextManagerContext context = buildContextManagerContextForToolOutput(
                    StringUtils.toJson(toolOutput),
                    parameters,
                    toolSpecs,
                    memory
                );
                EnhancedPostToolEvent event = new EnhancedPostToolEvent(null, null, context, new HashMap<>());
                hookRegistry.emit(event);

                Object processedOutput = extractProcessedToolOutput(context);
                return processedOutput != null ? processedOutput : toolOutput;
            } catch (Exception e) {
                log.error("Failed to emit POST_TOOL hook event", e);
                return toolOutput;
            }
        }
        return toolOutput;
    }

    public static ContextManagerContext emitPreLLMHook(
        Map<String, String> parameters,
        List<String> interactions,
        List<MLToolSpec> toolSpecs,
        Memory memory,
        HookRegistry hookRegistry
    ) {
        if (hookRegistry != null) {
            try {
                ContextManagerContext context = buildContextManagerContext(parameters, interactions, toolSpecs, memory);
                PreLLMEvent event = new PreLLMEvent(context, new HashMap<>());
                hookRegistry.emit(event);
                log.debug("Emitted PRE_LLM hook event and updated context");
                return context;

            } catch (Exception e) {
                log.error("Failed to emit PRE_LLM hook event", e);
            }
        }
        return null;
    }

    public static void updateParametersFromContext(Map<String, String> parameters, ContextManagerContext context) {
        if (context.getSystemPrompt() != null) {
            parameters.put(SYSTEM_PROMPT_FIELD, context.getSystemPrompt());
        }

        if (context.getUserPrompt() != null) {
            parameters.put(QUESTION, context.getUserPrompt());
        }

        if (context.getChatHistory() != null && !context.getChatHistory().isEmpty()) {
        }

        if (context.getToolInteractions() != null && !context.getToolInteractions().isEmpty()) {
            List<String> updatedInteractions = new ArrayList<>();
            for (Map<String, Object> toolInteraction : context.getToolInteractions()) {
                Object output = toolInteraction.get("output");
                if (output instanceof String) {
                    updatedInteractions.add((String) output);
                }
            }
            if (!updatedInteractions.isEmpty()) {
                parameters.put(INTERACTIONS, ", " + String.join(", ", updatedInteractions));
            }
        }

        if (context.getParameters() != null) {
            for (Map.Entry<String, String> entry : context.getParameters().entrySet()) {
                parameters.put(entry.getKey(), entry.getValue());

            }
        }
    }
}
