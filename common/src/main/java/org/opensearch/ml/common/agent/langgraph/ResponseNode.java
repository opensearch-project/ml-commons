package org.opensearch.ml.common.agent.langgraph;

import org.bsc.langgraph4j.action.NodeAction;

import java.util.List;
import java.util.Map;

// Node that adds a response
class ResponseNode implements NodeAction<TestState> {
    @Override
    public Map<String, Object> apply(TestState state) {
//        System.out.println("ResponderNode executing. Current messages: " + state.messages());
        List<String> currentMessages = state.messages();
        if (currentMessages.contains("Hello from GreeterNode!")) {
            return Map.of(TestState.MESSAGES_KEY, "Acknowledged greeting!");
        }

        return Map.of(TestState.MESSAGES_KEY, "No greeting found.");
    }
}