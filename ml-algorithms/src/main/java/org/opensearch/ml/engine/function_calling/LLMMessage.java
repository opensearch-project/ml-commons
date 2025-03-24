package org.opensearch.ml.engine.function_calling;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class LLMMessage {

    private String role;
    private List<Object> content = new ArrayList<>();

    LLMMessage() {
        this("user");
    }

    LLMMessage(String role) {
        this(role, null);
    }

    LLMMessage(String role, List<Object> content) {
        this.role = role;
        if (content != null) {
            this.content = content;
        }
    }
}
