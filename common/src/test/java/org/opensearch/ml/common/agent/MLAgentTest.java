/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLAgentType;
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
        exceptionRule.expectMessage("Agent name can't be null");

        MLAgent agent = new MLAgent(null, MLAgentType.CONVERSATIONAL.name(), "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
    }

    @Test
    public void constructor_NullType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Agent type can't be null");

        MLAgent agent = new MLAgent("test_agent", null, "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
    }

    @Test
    public void constructor_NullLLMSpec() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("We need model information for the conversational agent type");

        MLAgent agent = new MLAgent("test_agent", MLAgentType.CONVERSATIONAL.name(), "test", null, List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
    }

    @Test
    public void constructor_DuplicateTool() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Duplicate tool defined: test_tool_name");
        MLToolSpec mlToolSpec = new MLToolSpec("test_tool_type", "test_tool_name", "test", Collections.EMPTY_MAP, false);
        MLAgent agent = new MLAgent("test_name", MLAgentType.CONVERSATIONAL.name(), "test_description", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(mlToolSpec, mlToolSpec), null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
    }

    @Test
    public void writeTo() throws IOException {
        MLAgent agent = new MLAgent("test", "CONVERSATIONAL", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
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
        MLAgent agent = new MLAgent("test", "FLOW", "test", null, List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getLlm(), null);
    }

    @Test
    public void writeTo_NullTools() throws IOException {
        MLAgent agent = new MLAgent("test", "FLOW", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getTools(), null);
    }

    @Test
    public void writeTo_NullParameters() throws IOException {
        MLAgent agent = new MLAgent("test", MLAgentType.CONVERSATIONAL.name(), "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), null, new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getParameters(), null);
    }

    @Test
    public void writeTo_NullMemory() throws IOException {
        MLAgent agent = new MLAgent("test", "CONVERSATIONAL", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), null, Instant.EPOCH, Instant.EPOCH, "test", false);
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        Assert.assertEquals(agent1.getMemory(), null);
    }

    @Test
    public void toXContent() throws IOException {
        MLAgent agent = new MLAgent("test", "CONVERSATIONAL", "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Map.of("test", "test"), false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        agent.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        String expectedStr = "{\"name\":\"test\",\"type\":\"CONVERSATIONAL\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\",\"is_hidden\":false}";

        Assert.assertEquals(content, expectedStr);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"type\":\"CONVERSATIONAL\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\",\"is_hidden\":false}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLAgent agent = MLAgent.parse(parser);

        Assert.assertEquals(agent.getName(), "test");
        Assert.assertEquals(agent.getType(), "CONVERSATIONAL");
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
        Assert.assertEquals(agent.getIsHidden(), false);
    }

    @Test
    public void fromStream() throws IOException {
        MLAgent agent = new MLAgent("test", MLAgentType.CONVERSATIONAL.name(), "test", new LLMSpec("test_model", Map.of("test_key", "test_value")), List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)), Map.of("test", "test"), new MLMemorySpec("test", "123", 0), Instant.EPOCH, Instant.EPOCH, "test", false);
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

    @Test
    public void constructor_InvalidAgentType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(" is not a valid Agent Type");

        new MLAgent("test_name", "INVALID_TYPE", "test_description", null, null, null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
    }

    @Test
    public void constructor_NonConversationalNoLLM() {
        try {
            MLAgent agent = new MLAgent("test_name", MLAgentType.FLOW.name(), "test_description", null, null, null, null, Instant.EPOCH, Instant.EPOCH, "test", false);
            assertNotNull(agent); // Ensuring object creation was successful without throwing an exception
        } catch (IllegalArgumentException e) {
            fail("Should not throw an exception for non-conversational types without LLM");
        }
    }

    @Test
    public void writeTo_ReadFrom_HiddenFlag_VersionCompatibility() throws IOException {
        MLAgent agent = new MLAgent("test", "FLOW", "test", null, null, null, null, Instant.EPOCH, Instant.EPOCH, "test", true);
        BytesStreamOutput output = new BytesStreamOutput();
        Version oldVersion = Version.fromString("2.12.0");
        output.setVersion(oldVersion); // Version before MINIMAL_SUPPORTED_VERSION_FOR_HIDDEN_AGENT
        agent.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(oldVersion);
        MLAgent agentOldVersion = new MLAgent(streamInput);
        assertNull(agentOldVersion.getIsHidden()); // Hidden should be null for old versions

        output = new BytesStreamOutput();
        output.setVersion(Version.V_2_13_0); // Version at or after MINIMAL_SUPPORTED_VERSION_FOR_HIDDEN_AGENT
        agent.writeTo(output);
        StreamInput streamInput1 = output.bytes().streamInput();
        streamInput1.setVersion(Version.V_2_13_0);
        MLAgent agentNewVersion = new MLAgent(output.bytes().streamInput());
        assertEquals(Boolean.TRUE, agentNewVersion.getIsHidden()); // Hidden should be true for new versions
    }

    @Test
    public void parse_MissingFields() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"type\":\"FLOW\"}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLAgent agent = MLAgent.parse(parser);

        assertEquals("test", agent.getName());
        assertEquals("FLOW", agent.getType());
        assertNull(agent.getDescription());
        assertNull(agent.getLlm());
        assertNull(agent.getTools());
        assertNull(agent.getParameters());
        assertNull(agent.getMemory());
        assertNull(agent.getCreatedTime());
        assertNull(agent.getLastUpdateTime());
        assertNull(agent.getAppType());
        assertFalse(agent.getIsHidden()); // Default value for boolean when not specified
    }
}
