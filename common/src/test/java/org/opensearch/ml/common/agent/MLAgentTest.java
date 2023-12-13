package org.opensearch.ml.common.agent;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MLAgentTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("agent name is null");

        MLAgent agent = new MLAgent(null, "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, null, Instant.EPOCH, Instant.EPOCH, "test");
    }

    @Test
    public void writeTo() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent.getAppType(), agent1.getAppType());
        Assert.assertEquals(agent.getDescription(), agent1.getDescription());
        Assert.assertEquals(agent.getCreatedTime(), agent1.getCreatedTime());
        Assert.assertEquals(agent.getName(), agent1.getName());
        Assert.assertEquals(agent.getParameters(), agent1.getParameters());
        Assert.assertEquals(agent.getType(), agent1.getType());
    }

    @Test
    public void writeTo_NullLLM() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", null, List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getLlm(), null);
    }

    @Test
    public void writeTo_NullTools() throws IOException {
        MLAgent agent = new MLAgent("test", "flow", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getTools(), null);
    }

    @Test
    public void writeTo_NullParameters() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getParameters(), null);
    }

    @Test
    public void writeTo_NullMemory() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), null, Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getMemory(), null);
    }

    @Test
    public void toXContent() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Map.of("test", "test"), false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        agent.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        String expectedStr = "{\"name\":\"test\",\"type\":\"test\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\"}";

        Assert.assertEquals(content, expectedStr);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"type\":\"test\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\"}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLAgent agent = MLAgent.parse(parser);

        Assert.assertEquals(agent.getName(), "test");
        Assert.assertEquals(agent.getType(), "test");
        Assert.assertEquals(agent.getDescription(), "test");
        Assert.assertEquals(agent.getLlm().getModelId(), "test_model");
        Assert.assertEquals(agent.getLlm().getParameters(), Map.of("test_key", "test_value"));
        Assert.assertEquals(agent.getTools().get(0).getName(), "test");
        Assert.assertEquals(agent.getTools().get(0).getType(), "test");
        Assert.assertEquals(agent.getTools().get(0).getDescription(), "test");
        Assert.assertEquals(agent.getTools().get(0).getParameters(), Map.of("test", "test"));
        Assert.assertEquals(agent.getTools().get(0).isIncludeOutputInAgentResponse(), false);
        Assert.assertEquals(agent.getCreatedTime(), Instant.EPOCH);
        Assert.assertEquals(agent.getLastUpdateTime(), Instant.EPOCH);
        Assert.assertEquals(agent.getAppType(), "test");
        Assert.assertEquals(agent.getMemory().getSessionId(), "123");
        Assert.assertEquals(agent.getParameters(), Map.of("test", "test"));
    }

    @Test
    public void fromStream() throws IOException {
        MLAgent agent = new MLAgent("test", "test", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test");
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = MLAgent.fromStream(output.bytes().streamInput());

        Assert.assertEquals(agent.getAppType(), agent1.getAppType());
        Assert.assertEquals(agent.getDescription(), agent1.getDescription());
        Assert.assertEquals(agent.getCreatedTime(), agent1.getCreatedTime());
        Assert.assertEquals(agent.getName(), agent1.getName());
        Assert.assertEquals(agent.getParameters(), agent1.getParameters());
        Assert.assertEquals(agent.getType(), agent1.getType());
    }
}