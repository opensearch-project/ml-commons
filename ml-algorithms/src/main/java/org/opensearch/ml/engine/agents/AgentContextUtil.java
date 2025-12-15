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
            // TODO to add chatHistory into context, currently there is no context manager working on chat_history
        }

        if (toolSpecs != null) {
            builder.toolConfigs(toolSpecs);
        }

        builder.toolInteractions(interactions != null ? interactions : new ArrayList<>());

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
                if (toolOutput == null) {
                    log.warn("Tool output is null, skipping POST_TOOL hook");
                    return null;
                }
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
        ContextManagerContext context = buildContextManagerContext(parameters, interactions, toolSpecs, memory);

        if (hookRegistry == null) {
            return context;
        }

        try {
            PreLLMEvent event = new PreLLMEvent(context, new HashMap<>());
            hookRegistry.emit(event);
            return context;

        } catch (Exception e) {
            log.error("Failed to emit PRE_LLM hook event", e);
            return context;
        }
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
            parameters.put(INTERACTIONS, ", " + String.join(", ", context.getToolInteractions()));
        }

        if (context.getParameters() != null) {
            for (Map.Entry<String, String> entry : context.getParameters().entrySet()) {
                parameters.put(entry.getKey(), entry.getValue());

            }
        }
    }
}
