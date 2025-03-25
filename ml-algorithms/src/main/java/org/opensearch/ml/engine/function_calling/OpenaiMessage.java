package org.opensearch.ml.engine.function_calling;

import lombok.Data;

@Data
public class OpenaiMessage implements LLMMessage {

    private String role;
    private String content;
    private String toolCallId;

    OpenaiMessage() {
        this("tool");
    }

    OpenaiMessage(String role) {
        this(role, null);
    }

    OpenaiMessage(String role, Object content) {
        this.role = role;
        this.content = (String) content;
    }
}
