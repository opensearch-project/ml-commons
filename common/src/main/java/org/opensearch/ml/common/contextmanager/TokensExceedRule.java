/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Activation rule that triggers when the context token count exceeds a specified threshold.
 */
@AllArgsConstructor
@Getter
public class TokensExceedRule implements ActivationRule {

    private final int tokenThreshold;

    @Override
    public boolean evaluate(ContextManagerContext context) {
        if (context == null) {
            return false;
        }

        int currentTokenCount = context.getEstimatedTokenCount();
        return currentTokenCount > tokenThreshold;
    }

    @Override
    public String getDescription() {
        return "tokens_exceed: " + tokenThreshold;
    }
}
