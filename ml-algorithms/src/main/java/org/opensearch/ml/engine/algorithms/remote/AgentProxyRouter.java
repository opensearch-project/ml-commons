/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.util.List;

import org.opensearch.ml.common.agent.ConnectorSpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.log4j.Log4j2;

/**
 * Router for selecting the appropriate agent proxy connector based on AGUI context.
 * Evaluates routing rules against context fields to determine which external agent should handle the request.
 */
@Log4j2
public class AgentProxyRouter {

    /**
     * Selects the appropriate connector based on AGUI context and routing rules.
     *
     * @param contextArray AGUI context array from the request
     * @param connectors List of connector specs with optional routing rules
     * @return The selected ConnectorSpec
     * @throws IllegalArgumentException if no connector matches and no default is configured
     */
    public static ConnectorSpec selectConnector(JsonArray contextArray, List<ConnectorSpec> connectors) {
        if (connectors == null || connectors.isEmpty()) {
            throw new IllegalArgumentException("No connectors configured for agent proxy");
        }

        // Iterate through connectors in order
        for (ConnectorSpec connector : connectors) {
            if (connector.hasRoutingRules()) {
                // Connector has routing rules - check if any rule matches
                if (matchesRules(contextArray, connector.getRoutingRules())) {
                    log.debug("Connector matched via routing rules: {}", connector.getProxyUrl());
                    return connector;
                }
            } else {
                // Connector without routing rules is a catch-all/default
                log.debug("Using default connector (no routing rules): {}", connector.getProxyUrl());
                return connector;
            }
        }

        // No connector matched (all had rules, none matched, and no default)
        throw new IllegalArgumentException(
            "No connector matched context and no default connector configured. "
                + "Add a connector without routing_rules to serve as default."
        );
    }

    /**
     * Check if any routing rule matches the given context array.
     *
     * @param contextArray AGUI context array
     * @param routingRules List of routing rules to evaluate
     * @return true if at least one rule matches
     */
    private static boolean matchesRules(JsonArray contextArray, List<ConnectorSpec.RoutingRule> routingRules) {
        if (contextArray == null || contextArray.size() == 0) {
            return false;
        }

        if (routingRules == null || routingRules.isEmpty()) {
            return false;
        }

        // Check each routing rule
        for (ConnectorSpec.RoutingRule rule : routingRules) {
            if (matchesRule(contextArray, rule)) {
                log
                    .debug(
                        "Routing rule matched: context_description={}, value_pattern={}",
                        rule.getContextDescription(),
                        rule.getValuePattern()
                    );
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a single routing rule matches any item in the context array.
     *
     * @param contextArray AGUI context array
     * @param rule Routing rule to evaluate
     * @return true if the rule matches
     */
    private static boolean matchesRule(JsonArray contextArray, ConnectorSpec.RoutingRule rule) {
        String targetDescription = rule.getContextDescription();

        // Iterate through context items to find matching description
        for (JsonElement contextElement : contextArray) {
            if (!contextElement.isJsonObject()) {
                continue;
            }

            JsonObject contextItem = contextElement.getAsJsonObject();

            // Get description and value fields
            String description = getStringField(contextItem, "description");
            String value = getStringField(contextItem, "value");

            if (description == null || value == null) {
                continue;
            }

            // Check if description matches
            if (description.equals(targetDescription)) {
                // Check if value matches the pattern
                if (rule.matches(value)) {
                    log.debug("Context matched: description={}, value={}", description, value);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Helper method to safely extract string field from JsonObject.
     */
    private static String getStringField(JsonObject obj, String fieldName) {
        if (obj == null || !obj.has(fieldName)) {
            return null;
        }

        JsonElement element = obj.get(fieldName);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        return element.getAsString();
    }
}
