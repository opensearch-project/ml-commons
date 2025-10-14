package org.opensearch.ml.common.agent.langgraph;

import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

public class TestNode implements NodeAction<TestState> {

    @Override
    public Map<String, Object> apply(TestState state) {
//        System.out.println("TestNode executing. Current messages: " + state.messages());
        return Map.of(TestState.MESSAGES_KEY, "Hello from GreeterNode!");
    }
}
