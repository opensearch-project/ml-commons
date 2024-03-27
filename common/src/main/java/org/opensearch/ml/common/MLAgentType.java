/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Locale;

public enum MLAgentType {
    FLOW,
    CONVERSATIONAL,
    CONVERSATIONAL_FLOW;

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
}
