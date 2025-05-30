/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.search.SearchModule;

public class MLAgentUpdateInputTest {

    private MLAgentUpdateInput updateAgentInput;

    @Before
    public void setUp() throws Exception {

        LLMSpec llmSpec = LLMSpec.builder().modelId("test-model-id").parameters(Map.of("max_iteration", "5")).build();
        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .name("test-tool")
            .type("MLModelTool")
            .parameters(Map.of("model_id", "test-model-id"))
            .build();
        MLMemorySpec memorySpec = MLMemorySpec.builder().type("conversational").build();

        Map<String, String> parameters = new HashMap<>();
        parameters.put("_llm_interface", "test");

        updateAgentInput = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llm(llmSpec)
            .tools(Collections.singletonList(toolSpec))
            .parameters(parameters)
            .memory(memorySpec)
            .appType("rag")
            .lastUpdateTime(Instant.ofEpochMilli(1))
            .build();
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(updateAgentInput);
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertTrue(jsonStr.contains("\"name\":\"test-agent\""));
        assertTrue(jsonStr.contains("\"description\":\"test description\""));
        assertTrue(jsonStr.contains("\"model_id\":\"test-model-id\""));
        assertTrue(jsonStr.contains("\"_llm_interface\":\"test\""));
        assertTrue(jsonStr.contains("\"type\":\"conversational\""));
        assertTrue(jsonStr.contains("\"app_type\":\"rag\""));
        assertTrue(jsonStr.contains("\"last_updated_time\":1"));
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        updateAgentInput = MLAgentUpdateInput.builder().agentId("test-agent-id").build();
        String jsonStr = serializationWithToXContent(updateAgentInput);
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertFalse(jsonStr.contains("\"name\""));
    }

    @Test
    public void parseSuccess() throws Exception {
        String inputStr = """
            {
              "agent_id": "test-agent-id",
              "name": "test-agent",
              "description": "test description",
              "llm": {
                "model_id": "test-model-id",
                "parameters": {
                  "max_iteration": "5"
                }
              },
              "tools": [
                {
                  "name": "test-tool",
                  "type": "MLModelTool",
                  "parameters": {
                    "model_id": "test-model-id"
                  }
                }
              ],
              "parameters": {
                "chat_history": "test"
              },
              "memory": {
                "type": "conversational"
              },
              "app_type": "rag"
            }
            """;
        testParseFromJsonString(inputStr, parsedInput -> {
            assertEquals("test-agent", parsedInput.getName());
            assertEquals("test description", parsedInput.getDescription());
            assertEquals("test-model-id", parsedInput.getLlm().getModelId());
            assertEquals(1, parsedInput.getTools().size());
            assertEquals("test-tool", parsedInput.getTools().getFirst().getName());
            assertEquals("test", parsedInput.getParameters().get("chat_history"));
            assertEquals("conversational", parsedInput.getMemory().getType());
            assertEquals("rag", parsedInput.getAppType());
        });
    }

    @Test
    public void parseWithInvalidField() throws Exception {
        String inputStrWithIllegalField = """
            {
              "agent_id": "test-agent-id",
              "name": "test-agent",
              "description": "test description",
              "invalid_field": "invalid field description"
            }
            """;
        testParseFromJsonString(inputStrWithIllegalField, parsedInput -> {
            try {
                String jsonStr = serializationWithToXContent(parsedInput);
                assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
                assertTrue(jsonStr.contains("\"name\":\"test-agent\""));
                assertTrue(jsonStr.contains("\"description\":\"test description\""));
                assertFalse(jsonStr.contains("\"invalid_field\""));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testToMLAgent() {
        MLAgent originalAgent = MLAgent
            .builder()
            .type(MLAgentType.FLOW.name())
            .createdTime(Instant.ofEpochMilli(1))
            .isHidden(false)
            .name("original-agent")
            .build();

        MLAgent updatedAgent = updateAgentInput.toMLAgent(originalAgent);

        assertEquals(originalAgent.getType(), updatedAgent.getType());
        assertEquals(originalAgent.getCreatedTime(), updatedAgent.getCreatedTime());
        assertEquals(originalAgent.getIsHidden(), updatedAgent.getIsHidden());
        assertEquals(updateAgentInput.getName(), updatedAgent.getName());
        assertEquals(updateAgentInput.getDescription(), updatedAgent.getDescription());
        assertEquals(updateAgentInput.getLlm(), updatedAgent.getLlm());
        assertEquals(updateAgentInput.getTools(), updatedAgent.getTools());
        assertEquals(updateAgentInput.getParameters(), updatedAgent.getParameters());
        assertEquals(updateAgentInput.getMemory(), updatedAgent.getMemory());
        assertEquals(updateAgentInput.getLastUpdateTime(), updatedAgent.getLastUpdateTime());
        assertEquals(updateAgentInput.getAppType(), updatedAgent.getAppType());
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        updateAgentInput.setLlm(null);
        updateAgentInput.setTools(null);
        updateAgentInput.setParameters(null);
        updateAgentInput.setMemory(null);

        readInputStream(updateAgentInput, parsedInput -> {
            assertNull(parsedInput.getLlm());
            assertNull(parsedInput.getTools());
            assertNull(parsedInput.getParameters());
            assertNull(parsedInput.getMemory());
        });
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(updateAgentInput, parsedInput -> {
            assertEquals(updateAgentInput.getAgentId(), parsedInput.getAgentId());
            assertEquals(updateAgentInput.getName(), parsedInput.getName());
            assertEquals(updateAgentInput.getDescription(), parsedInput.getDescription());
            assertEquals(updateAgentInput.getLlm().getModelId(), parsedInput.getLlm().getModelId());
            assertEquals(updateAgentInput.getTools().size(), parsedInput.getTools().size());
            assertEquals(updateAgentInput.getParameters().size(), parsedInput.getParameters().size());
            assertEquals(updateAgentInput.getMemory().getType(), parsedInput.getMemory().getType());
            assertEquals(updateAgentInput.getAppType(), parsedInput.getAppType());
            assertEquals(updateAgentInput.getLastUpdateTime(), parsedInput.getLastUpdateTime());
        });
    }

    private void testParseFromJsonString(String inputStr, Consumer<MLAgentUpdateInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                inputStr
            );
        parser.nextToken();
        MLAgentUpdateInput parsedInput = MLAgentUpdateInput.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLAgentUpdateInput input, Consumer<MLAgentUpdateInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLAgentUpdateInput parsedInput = new MLAgentUpdateInput(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLAgentUpdateInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }
}
