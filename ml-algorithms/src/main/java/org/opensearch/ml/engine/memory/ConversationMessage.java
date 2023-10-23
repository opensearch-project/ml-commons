/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class ConversationMessage extends BaseMessage {

    @Getter
    @Setter
    private boolean finalAnswer;

    @Builder(builderMethodName = "conversationMessageBuilder")
    public ConversationMessage(String type, String content, boolean finalAnswer) {
        super(type, content);
        this.finalAnswer = finalAnswer;
    }

    @Override
    public String toString() {
        return type + ": " + content;
    }
}
