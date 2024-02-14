/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Data;

@Data
public class ConversationIndexMessage extends BaseMessage {

    private String sessionId;
    private String question;
    private String response;
    private Boolean finalAnswer;
    private Instant createdTime;

    @Builder(builderMethodName = "conversationIndexMessageBuilder")
    public ConversationIndexMessage(String type, String sessionId, String question, String response, boolean finalAnswer) {
        super(type, response);
        this.sessionId = sessionId;
        this.question = question;
        this.response = response;
        this.finalAnswer = finalAnswer;
        this.createdTime = Instant.now();
    }

    @Override
    public String toString() {
        return "Human:" + question + "\nAssistant:" + response;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (sessionId != null) {
            builder.field("session_id", sessionId);
        }
        if (question != null) {
            builder.field("question", question);
        }
        if (response != null) {
            builder.field("response", response);
        }
        if (finalAnswer != null) {
            builder.field("final_answer", finalAnswer);
        }
        builder.field("created_time", createdTime);
        builder.endObject();
        return builder;
    }
}
