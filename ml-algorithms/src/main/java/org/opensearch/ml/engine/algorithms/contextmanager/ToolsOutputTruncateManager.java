/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ActivationRule;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;

import lombok.extern.log4j.Log4j2;

/**
 * Context manager that truncates tool output to prevent context window overflow.
 * This manager processes the current tool output and applies length limits.
 */
@Log4j2
public class ToolsOutputTruncateManager implements ContextManager {

    public static final String TYPE = "ToolsOutputTruncateManager";

    // Configuration keys
    private static final String MAX_OUTPUT_LENGTH_KEY = "max_output_length";

    // Default values
    private static final int DEFAULT_MAX_OUTPUT_LENGTH = 40000;

    private int maxOutputLength;
    private List<ActivationRule> activationRules;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void initialize(Map<String, Object> config) {
        // Initialize configuration with defaults
        this.maxOutputLength = parseIntegerConfig(config, MAX_OUTPUT_LENGTH_KEY, DEFAULT_MAX_OUTPUT_LENGTH);

        if (this.maxOutputLength <= 0) {
            log.warn("Invalid max_output_length value: {}, using default {}", this.maxOutputLength, DEFAULT_MAX_OUTPUT_LENGTH);
            this.maxOutputLength = DEFAULT_MAX_OUTPUT_LENGTH;
        }

        // Initialize activation rules from config
        @SuppressWarnings("unchecked")
        Map<String, Object> activationConfig = (Map<String, Object>) config.get("activation");

        // Validate: context-level activation rules don't make sense for POST_TOOL hook
        // - tokens_exceed: measures entire context window (prompts + history + tool interactions)
        // - message_count_exceed: measures chat history size
        // Both are designed for PRE_LLM hooks where the full context is available.
        // POST_TOOL hook should truncate tool output proactively, not based on accumulated context.
        if (activationConfig != null) {
            if (activationConfig.containsKey("tokens_exceed")) {
                throw new IllegalArgumentException(
                    "ToolsOutputTruncateManager does not support 'tokens_exceed' activation rule. "
                        + "The 'tokens_exceed' rule measures the entire context window and is designed for PRE_LLM hooks. "
                        + "For POST_TOOL hooks, either use no activation rules (always truncate) or remove the 'activation' config entirely."
                );
            }
            if (activationConfig.containsKey("message_count_exceed")) {
                throw new IllegalArgumentException(
                    "ToolsOutputTruncateManager does not support 'message_count_exceed' activation rule. "
                        + "The 'message_count_exceed' rule measures chat history size and is designed for PRE_LLM hooks. "
                        + "For POST_TOOL hooks, either use no activation rules (always truncate) or remove the 'activation' config entirely."
                );
            }
        }

        this.activationRules = ActivationRuleFactory.createRules(activationConfig);

        log.info("Initialized ToolsOutputTruncateManager: maxOutputLength={}", maxOutputLength);
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
        // Process current tool output from parameters
        Map<String, String> parameters = context.getParameters();
        if (parameters == null) {
            log.debug("No parameters available for tool output truncation");
            return;
        }

        Object currentToolOutput = parameters.get("_current_tool_output");
        if (currentToolOutput == null) {
            log.debug("No current tool output to process");
            return;
        }

        String outputString = currentToolOutput.toString();
        int originalLength = outputString.length();

        if (originalLength <= maxOutputLength) {
            log.debug("Tool output length ({}) is within limit ({}), no truncation needed", originalLength, maxOutputLength);
            return;
        }

        // Truncate the output
        String truncatedOutput = outputString.substring(0, maxOutputLength);

        // Add truncation indicator
        truncatedOutput += "... [Output truncated - original length: " + originalLength + " characters]";

        // Update the current tool output in parameters
        parameters.put("_current_tool_output", truncatedOutput);

        int truncatedLength = truncatedOutput.length();
        log.info("Tool output truncated: original length {} -> truncated length {}", originalLength, truncatedLength);
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
