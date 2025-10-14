package org.opensearch.ml.common.agent.langgraph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class TestGraph {

    public static void main(String[] args) throws GraphStateException {
        TestNode testNode = new TestNode();
        ResponseNode responseNode = new ResponseNode();

        StateGraph<TestState> graph = new StateGraph<>(TestState.SCHEMA, initData -> new TestState(initData))
                .addNode("test", AsyncNodeAction.node_async(testNode))
                .addNode("responder", AsyncNodeAction.node_async(responseNode))
                .addEdge(START, "test")
                .addEdge("test", "responder")
                .addEdge("responder", END);

        CompiledGraph<TestState> compiledGraph = graph.compile();
        for (var item : compiledGraph.stream(Map.of(TestState.MESSAGES_KEY, "Let's try"))) {
            System.out.println(item);
        }
    }
}
