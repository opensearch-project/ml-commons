/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.search.SearchModule;

public class MLAgentTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MLToolSpec mlToolSpec = new MLToolSpec(
        "test",
        "test",
        "test",
        Collections.emptyMap(),
        Collections.emptyMap(),
        false,
        Collections.emptyMap(),
        null,
        null
    );

    @Test
    public void constructor_NullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Agent name can't be null");

        MLAgent agent = new MLAgent(
            null,
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
    }

    @Test
    public void constructor_NullType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Agent type can't be null");

        MLAgent agent = new MLAgent(
            "test_agent",
            null,
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
    }

    @Test
    public void constructor_NullLLMSpec() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("We need model information for the conversational agent type");

        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            null,
            List.of(mlToolSpec),
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
    }

    @Test
    public void constructor_DuplicateTool() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Duplicate tool defined: test");

        MLAgent agent = new MLAgent(
            "test_name",
            MLAgentType.CONVERSATIONAL.name(),
            "test_description",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec, mlToolSpec),
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
    }

    @Test
    public void writeTo() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "CONVERSATIONAL",
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
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
        MLAgent agent = new MLAgent(
            "test",
            "FLOW",
            "test",
            null,
            List.of(mlToolSpec),
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
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        assertNull(agent1.getLlm());
    }

    @Test
    public void writeTo_NullTools() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "FLOW",
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(),
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
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        assertNull(agent1.getTools());
    }

    @Test
    public void writeTo_NullParameters() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
            null,
            new MLMemorySpec("test", "123", 0, null),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        assertNull(agent1.getParameters());
    }

    @Test
    public void writeTo_NullMemory() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "CONVERSATIONAL",
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
            Map.of("test", "test"),
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
        BytesStreamOutput output = new BytesStreamOutput();
        agent.writeTo(output);
        MLAgent agent1 = new MLAgent(output.bytes().streamInput());

        assertNull(agent1.getMemory());
    }

    @Test
    public void toXContent() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "CONVERSATIONAL",
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "test",
                        "test",
                        "test",
                        Map.of("test", "test"),
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
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        agent.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        String expectedStr =
            "{\"name\":\"test\",\"type\":\"CONVERSATIONAL\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\",\"is_hidden\":false}";

        Assert.assertEquals(content, expectedStr);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr =
            "{\"name\":\"test\",\"type\":\"CONVERSATIONAL\",\"description\":\"test\",\"llm\":{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}},\"tools\":[{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}],\"parameters\":{\"test\":\"test\"},\"memory\":{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"},\"created_time\":0,\"last_updated_time\":0,\"app_type\":\"test\",\"is_hidden\":false}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
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
        assertFalse(agent.getTools().get(0).isIncludeOutputInAgentResponse());
        Assert.assertEquals(agent.getCreatedTime(), Instant.EPOCH);
        Assert.assertEquals(agent.getLastUpdateTime(), Instant.EPOCH);
        Assert.assertEquals(agent.getAppType(), "test");
        Assert.assertEquals(agent.getMemory().getSessionId(), "123");
        Assert.assertEquals(agent.getParameters(), Map.of("test", "test"));
        Assert.assertEquals(agent.getIsHidden(), false);
    }

    @Test
    public void fromStream() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
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

        new MLAgent(
            "test_name",
            "INVALID_TYPE",
            "test_description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null,
            null,
            null
        );
    }

    @Test
    public void constructor_NonConversationalNoLLM() {
        try {
            MLAgent agent = new MLAgent(
                "test_name",
                MLAgentType.FLOW.name(),
                "test_description",
                null,
                null,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                "test",
                false,
                null,
                null,
                null
            );
            assertNotNull(agent); // Ensuring object creation was successful without throwing an exception
        } catch (IllegalArgumentException e) {
            fail("Should not throw an exception for non-conversational types without LLM");
        }
    }

    @Test
    public void writeTo_ReadFrom_HiddenFlag_VersionCompatibility() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "FLOW",
            "test",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            true,
            null,
            null,
            null
        );

        // Serialize and deserialize with an older version
        BytesStreamOutput output = new BytesStreamOutput();
        Version oldVersion = CommonValue.VERSION_2_12_0; // Before hidden flag support
        output.setVersion(oldVersion);
        agent.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(oldVersion);
        MLAgent agentOldVersion = new MLAgent(streamInput);
        assertNull(agentOldVersion.getIsHidden()); // Hidden should be null for old versions

        // Serialize and deserialize with a newer version
        output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_2_13_0); // After hidden flag support
        agent.writeTo(output);

        StreamInput streamInput1 = output.bytes().streamInput();
        streamInput1.setVersion(CommonValue.VERSION_2_13_0);
        MLAgent agentNewVersion = new MLAgent(streamInput1);
        assertEquals(Boolean.TRUE, agentNewVersion.getIsHidden()); // Hidden should be true for new versions
    }

    @Test
    public void parse_MissingFields() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"type\":\"FLOW\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
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

    @Test
    public void getTags() {
        MLAgent agent = new MLAgent(
            "test_agent",
            "CONVERSATIONAL",
            "test description",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(mlToolSpec),
            Map.of("_llm_interface", "bedrock"),
            new MLMemorySpec("conversation_index", "123", 10, null),
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            true,
            null,
            null,
            null
        );

        org.opensearch.telemetry.metrics.tags.Tags tags = agent.getTags();
        Map<String, ?> tagsMap = tags.getTagsMap();

        assertEquals(true, tagsMap.get("is_hidden"));
        assertEquals("CONVERSATIONAL", tagsMap.get("type"));
        assertEquals("conversation_index", tagsMap.get("memory_type"));
        assertEquals("bedrock", tagsMap.get("_llm_interface"));
    }

    @Test
    public void getTags_NullValues() {
        MLAgent agent = new MLAgent(
            "test_agent",
            "flow",
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            null,
            null,
            null,
            null
        );

        org.opensearch.telemetry.metrics.tags.Tags tags = agent.getTags();
        Map<String, ?> tagsMap = tags.getTagsMap();

        assertEquals(false, tagsMap.get("is_hidden"));
        assertEquals("flow", tagsMap.get("type"));
        assertFalse(tagsMap.containsKey("memory_type"));
        assertFalse(tagsMap.containsKey("_llm_interface"));
    }

    @Test
    public void constructor_ConflictingContextManagement() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Cannot specify both context_management_name and context_management");

        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            "template_name",
            new ContextManagementTemplate(),
            null
        );
    }

    @Test
    public void hasContextManagement_WithTemplateName() {
        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            "template_name",
            null,
            null
        );

        assertTrue(agent.hasContextManagement());
        assertTrue(agent.hasContextManagementTemplate());
        assertEquals("template_name", agent.getContextManagementTemplateName());
        assertNull(agent.getInlineContextManagement());
    }

    @Test
    public void hasContextManagement_WithInlineConfig() {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("test_template")
            .description("test description")
            .hooks(Map.of("POST_TOOL", List.of()))
            .build();

        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            null,
            template,
            null
        );

        assertTrue(agent.hasContextManagement());
        assertFalse(agent.hasContextManagementTemplate());
        assertNull(agent.getContextManagementTemplateName());
        assertEquals(template, agent.getInlineContextManagement());
    }

    @Test
    public void hasContextManagement_NoContextManagement() {
        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            null,
            null,
            null
        );

        assertFalse(agent.hasContextManagement());
        assertFalse(agent.hasContextManagementTemplate());
        assertNull(agent.getContextManagementTemplateName());
        assertNull(agent.getInlineContextManagement());
    }

    @Test
    public void writeTo_ReadFrom_ContextManagementName() throws IOException {
        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            "template_name",
            null,
            null
        );

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_3_0);
        agent.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_3_0);
        MLAgent deserializedAgent = new MLAgent(streamInput);

        assertEquals("template_name", deserializedAgent.getContextManagementTemplateName());
        assertNull(deserializedAgent.getInlineContextManagement());
        assertTrue(deserializedAgent.hasContextManagement());
        assertTrue(deserializedAgent.hasContextManagementTemplate());
    }

    @Test
    public void writeTo_ReadFrom_ContextManagementInline() throws IOException {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("test_template")
            .description("test description")
            .hooks(Map.of("POST_TOOL", List.of()))
            .build();

        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            null,
            template,
            null
        );

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_3_0);
        agent.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_3_0);
        MLAgent deserializedAgent = new MLAgent(streamInput);

        assertNull(deserializedAgent.getContextManagementTemplateName());
        assertNotNull(deserializedAgent.getInlineContextManagement());
        assertEquals("test_template", deserializedAgent.getInlineContextManagement().getName());
        assertEquals("test description", deserializedAgent.getInlineContextManagement().getDescription());
        assertTrue(deserializedAgent.hasContextManagement());
        assertFalse(deserializedAgent.hasContextManagementTemplate());
    }

    @Test
    public void writeTo_ReadFrom_ContextManagement_VersionCompatibility() throws IOException {
        MLAgent agent = new MLAgent(
            "test_agent",
            MLAgentType.FLOW.name(),
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            "template_name",
            null,
            null
        );

        // Serialize with older version (before context management support)
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_2_0);
        agent.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_2_0);
        MLAgent deserializedAgent = new MLAgent(streamInput);

        // Context management fields should be null for older versions
        assertNull(deserializedAgent.getContextManagementTemplateName());
        assertNull(deserializedAgent.getInlineContextManagement());
        assertFalse(deserializedAgent.hasContextManagement());
    }

    @Test
    public void parse_WithContextManagementName() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"type\":\"FLOW\",\"context_management_name\":\"template_name\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLAgent agent = MLAgent.parseFromUserInput(parser);

        assertEquals("test", agent.getName());
        assertEquals("FLOW", agent.getType());
        assertEquals("template_name", agent.getContextManagementTemplateName());
        assertNull(agent.getInlineContextManagement());
        assertTrue(agent.hasContextManagement());
        assertTrue(agent.hasContextManagementTemplate());
    }

    @Test
    public void parse_WithInlineContextManagement() throws IOException {
        String jsonStr =
            "{\"name\":\"test\",\"type\":\"FLOW\",\"context_management\":{\"name\":\"inline_template\",\"description\":\"test\",\"hooks\":{\"POST_TOOL\":[]}}}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLAgent agent = MLAgent.parseFromUserInput(parser);

        assertEquals("test", agent.getName());
        assertEquals("FLOW", agent.getType());
        assertNull(agent.getContextManagementTemplateName());
        assertNotNull(agent.getInlineContextManagement());
        assertEquals("inline_template", agent.getInlineContextManagement().getName());
        assertEquals("test", agent.getInlineContextManagement().getDescription());
        assertTrue(agent.hasContextManagement());
        assertFalse(agent.hasContextManagementTemplate());
    }

    @Test
    public void toXContent_WithContextManagementName() throws IOException {
        MLAgent agent = new MLAgent(
            "test",
            "FLOW",
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            "template_name",
            null,
            null
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        agent.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        assertTrue(content.contains("\"context_management_name\":\"template_name\""));
        assertFalse(content.contains("\"context_management\":"));
    }

    @Test
    public void toXContent_WithInlineContextManagement() throws IOException {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("inline_template")
            .description("test description")
            .hooks(Map.of("POST_TOOL", List.of()))
            .build();

        MLAgent agent = new MLAgent(
            "test",
            "FLOW",
            "test description",
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "test_app",
            false,
            null,
            template,
            null
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        agent.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        assertFalse(content.contains("\"context_management_name\":"));
        assertTrue(content.contains("\"context_management\":"));
        assertTrue(content.contains("\"inline_template\""));
    }
}
