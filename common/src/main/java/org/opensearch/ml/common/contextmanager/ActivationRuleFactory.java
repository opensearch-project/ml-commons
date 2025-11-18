/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j2;

/**
 * Factory class for creating activation rules from configuration.
 * Supports creating rules from configuration maps and combining multiple rules.
 */
@Log4j2
public class ActivationRuleFactory {

    public static final String TOKENS_EXCEED_KEY = "tokens_exceed";
    public static final String MESSAGE_COUNT_EXCEED_KEY = "message_count_exceed";

    /**
     * Create activation rules from a configuration map.
     * @param activationConfig the configuration map containing rule definitions
     * @return a list of activation rules, or empty list if no valid rules found
     */
    public static List<ActivationRule> createRules(Map<String, Object> activationConfig) {
        List<ActivationRule> rules = new ArrayList<>();

        if (activationConfig == null || activationConfig.isEmpty()) {
            return rules;
        }

        // Create tokens_exceed rule
        if (activationConfig.containsKey(TOKENS_EXCEED_KEY)) {
            try {
                Object tokenValue = activationConfig.get(TOKENS_EXCEED_KEY);
                int tokenThreshold = parseIntegerValue(tokenValue, TOKENS_EXCEED_KEY);
                if (tokenThreshold > 0) {
                    rules.add(new TokensExceedRule(tokenThreshold));
                    log.debug("Created TokensExceedRule with threshold: {}", tokenThreshold);
                } else {
                    throw new IllegalArgumentException("Invalid token threshold value: " + tokenValue + ". Must be positive integer.");
                }
            } catch (Exception e) {
                log.error("Failed to create TokensExceedRule: {}", e.getMessage());
            }
        }

        // Create message_count_exceed rule
        if (activationConfig.containsKey(MESSAGE_COUNT_EXCEED_KEY)) {
            try {
                Object messageValue = activationConfig.get(MESSAGE_COUNT_EXCEED_KEY);
                int messageThreshold = parseIntegerValue(messageValue, MESSAGE_COUNT_EXCEED_KEY);
                if (messageThreshold > 0) {
                    rules.add(new MessageCountExceedRule(messageThreshold));
                    log.debug("Created MessageCountExceedRule with threshold: {}", messageThreshold);
                } else {
                    throw new IllegalArgumentException(
                        "Invalid message count threshold value: " + messageValue + ". Must be positive integer."
                    );
                }
            } catch (Exception e) {
                log.error("Failed to create MessageCountExceedRule: {}", e.getMessage());
            }
        }

        return rules;
    }

    /**
     * Create a composite rule that requires ALL rules to be satisfied (AND logic).
     * @param rules the list of rules to combine
     * @return a composite rule, or null if the list is empty
     */
    public static ActivationRule createCompositeRule(List<ActivationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        if (rules.size() == 1) {
            return rules.get(0);
        }

        return new CompositeActivationRule(rules);
    }

    /**
     * Parse an integer value from configuration, handling various input types.
     * @param value the value to parse
     * @param fieldName the field name for error reporting
     * @return the parsed integer value
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    private static int parseIntegerValue(Object value, String fieldName) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value for " + fieldName + ": " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported value type for " + fieldName + ": " + value.getClass().getSimpleName());
        }
    }

    /**
     * Composite activation rule that implements AND logic for multiple rules.
     */
    private static class CompositeActivationRule implements ActivationRule {
        private final List<ActivationRule> rules;

        public CompositeActivationRule(List<ActivationRule> rules) {
            this.rules = new ArrayList<>(rules);
        }

        @Override
        public boolean evaluate(ContextManagerContext context) {
            // All rules must evaluate to true (AND logic)
            for (ActivationRule rule : rules) {
                if (!rule.evaluate(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("composite_rule: [");
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) {
                    sb.append(" AND ");
                }
                sb.append(rules.get(i).getDescription());
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
