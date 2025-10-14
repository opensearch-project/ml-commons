package org.opensearch.ml.common.agent.langgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestState extends AgentState {

    public static final String MESSAGES_KEY = "messages";

    public TestState(Map<String, Object> initData) {
        super(initData);
    }

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new)
    );

    public List<String> messages() {
        return this.<List<String>>value("messages")
                .orElse( List.of() );
    }
}
