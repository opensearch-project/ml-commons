/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.*;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;

public class MLAgentGetResponseTest {

    MLAgent mlAgent;

    @Before
    public void setUp() {
        mlAgent = MLAgent
            .builder()
            .name("test_agent")
            .appType("test_app")
            .type(MLAgentType.FLOW.name())
            .tools(Arrays.asList(MLToolSpec.builder().type("ListIndexTool").build()))
            .build();
    }

    @Test
    public void Create_MLAgentResponse_With_StreamInput() throws IOException {
        // Create a BytesStreamOutput to simulate the StreamOutput
        MLAgentGetResponse agentGetResponse = new MLAgentGetResponse(mlAgent);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                agentGetResponse.writeTo(out);
            }
        };
        MLAgentGetResponse parsedResponse = MLAgentGetResponse.fromActionResponse(actionResponse);
        assertNotSame(agentGetResponse, parsedResponse);
        assertEquals(agentGetResponse.getMlAgent(), parsedResponse.getMlAgent());
    }

    @Test
    public void mLAgentGetResponse_Builder() throws IOException {

        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();

        assertEquals(mlAgentGetResponse.mlAgent, mlAgent);
    }

    @Test
    public void writeTo() throws IOException {
        // create ml agent using MLAgent and mlAgentGetResponse
        mlAgent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "test",
                        "test",
                        "test",
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        false,
                        Collections.emptyMap(),
                        null,
                        null
                    )
                ),
            Map.of("test", "test"),
            new MLMemorySpec("test", "123", 0, null),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();
        // use write out for both agents
        BytesStreamOutput output = new BytesStreamOutput();
        mlAgent.writeTo(output);
        mlAgentGetResponse.writeTo(output);
        MLAgent agent1 = mlAgentGetResponse.mlAgent;

        assertEquals(mlAgent.getAppType(), agent1.getAppType());
        assertEquals(mlAgent.getDescription(), agent1.getDescription());
        assertEquals(mlAgent.getCreatedTime(), agent1.getCreatedTime());
        assertEquals(mlAgent.getName(), agent1.getName());
        assertEquals(mlAgent.getParameters(), agent1.getParameters());
        assertEquals(mlAgent.getType(), agent1.getType());
    }

    @Test
    public void toXContent() throws IOException {
        mlAgent = new MLAgent("mock", MLAgentType.FLOW.name(), "test", null, null, null, null, null, null, "test", false, null, null, null);
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        ToXContent.Params params = EMPTY_PARAMS;
        XContentBuilder getResponseXContentBuilder = mlAgentGetResponse.toXContent(builder, params);
        assertEquals(getResponseXContentBuilder, mlAgent.toXContent(builder, params));
    }

    @Test
    public void fromActionResponse_Success() throws IOException {
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();
        assertEquals(mlAgentGetResponse.fromActionResponse(mlAgentGetResponse), mlAgentGetResponse);

    }

    @Test
    public void fromActionResponse_Success_fromActionResponse() throws IOException {
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();

        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlAgentGetResponse.writeTo(out);
            }
        };
        MLAgentGetResponse response = mlAgentGetResponse.fromActionResponse(actionResponse);
        assertEquals(response.getMlAgent(), mlAgent);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder().mlAgent(mlAgent).build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        mlAgentGetResponse.fromActionResponse(actionResponse);
    }
}
