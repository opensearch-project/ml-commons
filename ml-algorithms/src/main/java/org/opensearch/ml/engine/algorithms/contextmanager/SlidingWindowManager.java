/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ActivationRule;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.input.execute.agent.Message;

import lombok.extern.log4j.Log4j2;

/**
 * Context manager that implements a sliding window approach for tool interactions.
 * Keeps only the most recent N interactions to prevent context window overflow.
 * This manager ensures proper handling of different message types while tool execution flow.
 */
@Log4j2
public class SlidingWindowManager implements ContextManager {

    public static final String TYPE = "SlidingWindowManager";

    // Configuration keys
    private static final String MAX_MESSAGES_KEY = "max_messages";

    // Default values
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private int maxMessages;
    private List<ActivationRule> activationRules;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void initialize(Map<String, Object> config) {
        // Initialize configuration with defaults
        this.maxMessages = parseIntegerConfig(config, MAX_MESSAGES_KEY, DEFAULT_MAX_MESSAGES);

        if (this.maxMessages <= 0) {
            log.warn("Invalid max_messages value: {}, using default {}", this.maxMessages, DEFAULT_MAX_MESSAGES);
            this.maxMessages = DEFAULT_MAX_MESSAGES;
        }

        // Initialize activation rules from config
        @SuppressWarnings("unchecked")
        Map<String, Object> activationConfig = (Map<String, Object>) config.get("activation");
        this.activationRules = ActivationRuleFactory.createRules(activationConfig);

        log.info("Initialized SlidingWindowManager: maxMessages={}", maxMessages);
    }

    @Override
    public boolean shouldActivate(ContextManagerContext context) {
        if (activationRules == null || activationRules.isEmpty()) {
            return true;
        }

        for (ActivationRule rule : activationRules) {
            if (!rule.evaluate(context)) {
                log.debug("Activation rule not satisfied: {}", rule.getDescription());
                return false;
            }
        }

        log.debug("All activation rules satisfied, manager will execute");
        return true;
    }

    @Override
    public void execute(ContextManagerContext context) {
        slideToolInteractions(context);
        slideChatHistory(context);
        slideStructuredChatHistory(context);
    }

    private void slideToolInteractions(ContextManagerContext context) {
        List<String> interactions = context.getToolInteractions();

        if (interactions == null || interactions.isEmpty()) {
            log.debug("No tool interactions to process");
            return;
        }

        int originalSize = interactions.size();

        if (originalSize <= maxMessages) {
            log.debug("Interactions size ({}) is within limit ({}), no truncation needed", originalSize, maxMessages);
            return;
        }

        // Find safe start point to avoid breaking tool pairs
        int startIndex = ContextManagerUtils.findSafePoint(interactions, originalSize - maxMessages, true);

        // Keep the most recent interactions from safe start point
        List<String> updatedInteractions = new ArrayList<>(interactions.subList(startIndex, originalSize));

        // Update toolInteractions in context to keep only the most recent ones
        context.setToolInteractions(updatedInteractions);

        // Update the _interactions parameter with smaller size of updated interactions
        Map<String, String> parameters = context.getParameters();
        if (parameters == null) {
            parameters = new HashMap<>();
            context.setParameters(parameters);
        }
        parameters.put("_interactions", ", " + String.join(", ", updatedInteractions));

        int removedMessages = originalSize - updatedInteractions.size();
        log
            .info(
                "Applied sliding window: kept {} most recent tool interactions, removed {} older interactions",
                updatedInteractions.size(),
                removedMessages
            );
    }

    private void slideChatHistory(ContextManagerContext context) {
        List<Interaction> chatHistory = context.getChatHistory();

        if (chatHistory == null || chatHistory.isEmpty()) {
            log.debug("No chat history to process");
            return;
        }

        int originalSize = chatHistory.size();

        if (originalSize <= maxMessages) {
            log.debug("Chat history size ({}) is within limit ({}), no truncation needed", originalSize, maxMessages);
            return;
        }

        // Keep the most recent interactions
        int startIndex = originalSize - maxMessages;
        List<Interaction> updatedHistory = new ArrayList<>(chatHistory.subList(startIndex, originalSize));

        context.setChatHistory(updatedHistory);

        int removedMessages = originalSize - updatedHistory.size();
        log
            .info(
                "Applied sliding window: kept {} most recent chat history interactions, removed {} older interactions",
                updatedHistory.size(),
                removedMessages
            );
    }

    private void slideStructuredChatHistory(ContextManagerContext context) {
        List<Message> structuredHistory = context.getStructuredChatHistory();

        if (structuredHistory == null || structuredHistory.isEmpty()) {
            log.debug("No structured chat history to process");
            return;
        }

        int originalSize = structuredHistory.size();

        if (originalSize <= maxMessages) {
            log.debug("Structured chat history size ({}) is within limit ({}), no truncation needed", originalSize, maxMessages);
            return;
        }

        // Find safe start point to avoid breaking tool-call/tool-result pairs
        int startIndex = ContextManagerUtils.findSafeCutPointForStructuredMessages(structuredHistory, originalSize - maxMessages);

        // Guard: if safe-cut-point advanced to the end (all messages are tool pairs),
        // keep the original history rather than discarding everything.
        if (startIndex >= originalSize) {
            log.debug("Safe cut point reached end of structured history, keeping all messages");
            return;
        }

        List<Message> updatedHistory = new ArrayList<>(structuredHistory.subList(startIndex, originalSize));

        context.setStructuredChatHistory(updatedHistory);

        int removedMessages = originalSize - updatedHistory.size();
        log
            .info(
                "Applied sliding window: kept {} most recent structured messages, removed {} older messages",
                updatedHistory.size(),
                removedMessages
            );
    }

    private int parseIntegerConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else {
                log.warn("Invalid type for config key '{}': {}, using default {}", key, value.getClass().getSimpleName(), defaultValue);
                return defaultValue;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for config key '{}': {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

}
