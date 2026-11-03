/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Activation rule that triggers when the chat history message count exceeds a specified threshold.
 */
@AllArgsConstructor
@Getter
public class MessageCountExceedRule implements ActivationRule {

    private final int messageThreshold;

    @Override
    public boolean evaluate(ContextManagerContext context) {
        if (context == null) {
            return false;
        }

        int currentMessageCount = context.getMessageCount();
        return currentMessageCount > messageThreshold;
    }

    @Override
    public String getDescription() {
        return "message_count_exceed: " + messageThreshold;
    }
}
