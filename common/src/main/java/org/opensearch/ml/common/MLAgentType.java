/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

public enum MLAgentType {
    FLOW,
    CONVERSATIONAL,
    CONVERSATIONAL_FLOW;

    public static MLAgentType from(String value) {
        try {
            return MLAgentType.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong Agent type");
        }
    }
}
