/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Locale;

public enum MLAgentType {
    FLOW,
    CONVERSATIONAL,
    CONVERSATIONAL_FLOW,
    PLAN_EXECUTE_AND_REFLECT,
    AG_UI,
    CONVERSATIONAL_V2;

    public static MLAgentType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Agent type can't be null");
        }
        try {
            // Handle "conversational/v2" format
            String normalizedValue = value.replace("/", "_").toUpperCase(Locale.ROOT);
            return MLAgentType.valueOf(normalizedValue);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong Agent type");
        }
    }
}
