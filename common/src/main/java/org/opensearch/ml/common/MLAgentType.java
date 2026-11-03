/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Locale;

public enum MLAgentType {
    // V1 Agent Types (legacy)
    FLOW,
    CONVERSATIONAL,
    CONVERSATIONAL_FLOW,
    PLAN_EXECUTE_AND_REFLECT,
    AG_UI,

    // V2 Agent Types (simplified, message-centric)
    // Add to isV2() method when a new V2 agent is added
    CONVERSATIONAL_V2;

    public static MLAgentType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Agent type can't be null");
        }
        try {
            return MLAgentType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong Agent type");
        }
    }

    /**
     * Check if this is a V2 agent type.
     * V2 agents use message-centric architecture, require agentic memory,
     * and support standardized input/output formats.
     *
     * @return true if this is a V2 agent type, false otherwise
     */
    public boolean isV2() {
        return this == CONVERSATIONAL_V2;
    }
}
