package org.opensearch.ml.common.transport.agent;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.io.stream.*;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;

import java.io.*;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class MLAgentGetResponseTest {

    MLAgent mlAgent;

    @Test
    public void Create_MLAgentResponse_With_StreamInput() throws IOException {
        // Create a BytesStreamOutput to simulate the StreamOutput
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();

        //create a test agent using input
        bytesStreamOutput.writeString("Test Agent");
        bytesStreamOutput.writeString("flow");
        bytesStreamOutput.writeBoolean(false);
        bytesStreamOutput.writeBoolean(false);
        bytesStreamOutput.writeBoolean(false);
        bytesStreamOutput.writeBoolean(false);
        bytesStreamOutput.writeBoolean(false);
        bytesStreamOutput.writeInstant(Instant.parse("2023-12-31T12:00:00Z"));
        bytesStreamOutput.writeInstant(Instant.parse("2023-12-31T12:00:00Z"));
        bytesStreamOutput.writeString("test");

        StreamInput testInputStream = bytesStreamOutput.bytes().streamInput();

        MLAgentGetResponse mlAgentGetResponse = new MLAgentGetResponse(testInputStream);
        MLAgent testMlAgent = mlAgentGetResponse.mlAgent;
        assertEquals("flow",testMlAgent.getType());
        assertEquals("Test Agent",testMlAgent.getName());
        assertEquals("test",testMlAgent.getAppType());
    }

    @Test
    public void mLAgentGetResponse_Builder() throws IOException {

        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder()
                .mlAgent(mlAgent)
                .build();

        assertEquals(mlAgentGetResponse.mlAgent, mlAgent);
    }
    @Test
    public void writeTo() throws IOException {
        //create ml agent using MLAgent and mlAgentGetResponse
        mlAgent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder()
                .mlAgent(mlAgent)
                .build();
        //use write out for both agents
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
        mlAgent = new MLAgent("mock", "flow", "test", null, null, null, null, null, null, "test");
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder()
                .mlAgent(mlAgent)
                .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        ToXContent.Params params = EMPTY_PARAMS;
        XContentBuilder getResponseXContentBuilder = mlAgentGetResponse.toXContent(builder, params);
        assertEquals(getResponseXContentBuilder, mlAgent.toXContent(builder, params));
    }

    @Test
    public void FromActionResponse() throws IOException {
        MLAgentGetResponse mlAgentGetResponse = MLAgentGetResponse.builder()
                .mlAgent(mlAgent)
                .build();
        assertEquals(mlAgentGetResponse.fromActionResponse(mlAgentGetResponse), mlAgentGetResponse);

        }
    }
