/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
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

        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .name("test-tool")
            .type("MLModelTool")
            .parameters(Map.of("model_id", "test-model-id"))
            .build();

        Map<String, String> parameters = new HashMap<>();
        parameters.put("_llm_interface", "test");

        Map<String, String> llmParameters = new HashMap<>();
        llmParameters.put("max_iteration", "5");

        updateAgentInput = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llmModelId("test-model-id")
            .llmParameters(llmParameters)
            .tools(Collections.singletonList(toolSpec))
            .parameters(parameters)
            .memoryType("conversation_index")
            .memorySessionId("test-session")
            .memoryWindowSize(10)
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
        assertTrue(jsonStr.contains("\"type\":\"conversation_index\""));
        assertTrue(jsonStr.contains("\"app_type\":\"rag\""));
        assertTrue(jsonStr.contains("\"last_updated_time\":1"));
    }

    @Test
    public void testValidationWithInvalidMemoryType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("test-agent").memoryType("invalid_type").build();
        });
        assertEquals("Invalid memory type: invalid_type", e.getMessage());
    }

    @Test
    public void testValidationWithInvalidAgentName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("").build();
        });
        assertEquals(
            String.format("Agent name cannot be empty or exceed max length of %d characters", MLAgent.AGENT_NAME_MAX_LENGTH),
            e.getMessage()
        );

        e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("a".repeat(MLAgent.AGENT_NAME_MAX_LENGTH + 1)).build();
        });
        assertEquals(
            String.format("Agent name cannot be empty or exceed max length of %d characters", MLAgent.AGENT_NAME_MAX_LENGTH),
            e.getMessage()
        );
    }

    @Test
    public void testValidationWithDuplicateTools() {
        MLToolSpec tool1 = MLToolSpec.builder().name("tool1").type("type1").build();
        MLToolSpec tool2 = MLToolSpec.builder().name("tool1").type("type2").build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("test-agent").tools(Arrays.asList(tool1, tool2)).build();
        });
        assertEquals("Duplicate tool defined: tool1", e.getMessage());
    }

    @Test
    public void testValidationWithEmptyAgentName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("").build();
        });
        assertTrue(e.getMessage().contains("Agent name cannot be empty"));
    }

    @Test
    public void testValidationWithBlankAgentName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("   ").build();
        });
        assertTrue(e.getMessage().contains("Agent name cannot be empty"));
    }

    @Test
    public void testValidationWithTooLongAgentName() {
        String longName = "a".repeat(MLAgent.AGENT_NAME_MAX_LENGTH + 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name(longName).build();
        });
        assertTrue(e.getMessage().contains("exceed max length"));
    }

    @Test
    public void testValidationWithMaxLengthAgentName() {
        String maxLengthName = "a".repeat(MLAgent.AGENT_NAME_MAX_LENGTH);
        // Should not throw exception
        MLAgentUpdateInput input = MLAgentUpdateInput.builder().agentId("test-agent-id").name(maxLengthName).build();
        assertEquals(maxLengthName, input.getName());
    }

    @Test
    public void testValidationWithNullAgentName() {
        // Should not throw exception - null names are allowed
        MLAgentUpdateInput input = MLAgentUpdateInput.builder().agentId("test-agent-id").name(null).build();
        assertNull(input.getName());
    }

    @Test
    public void testValidationWithDuplicateToolsByType() {
        // Test duplicate tools identified by type when name is null
        MLToolSpec tool1 = MLToolSpec.builder().type("duplicate_type").build();
        MLToolSpec tool2 = MLToolSpec.builder().type("duplicate_type").build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").tools(Arrays.asList(tool1, tool2)).build();
        });
        assertEquals("Duplicate tool defined: duplicate_type", e.getMessage());
    }

    @Test
    public void testValidationWithValidUniqueTools() {
        MLToolSpec tool1 = MLToolSpec.builder().name("tool1").type("type1").build();
        MLToolSpec tool2 = MLToolSpec.builder().name("tool2").type("type2").build();
        MLToolSpec tool3 = MLToolSpec.builder().type("type3").build(); // No name, uses type

        // Should not throw exception
        MLAgentUpdateInput input = MLAgentUpdateInput.builder().agentId("test-agent-id").tools(Arrays.asList(tool1, tool2, tool3)).build();

        assertEquals(3, input.getTools().size());
    }

    @Test
    public void testValidationWithDuplicateToolsByTypeOnly() {
        // Test duplicate tools identified by type when name is null
        MLToolSpec tool3 = MLToolSpec.builder().type("type3").build();
        MLToolSpec tool4 = MLToolSpec.builder().type("type3").build();

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> {
            MLAgentUpdateInput.builder().agentId("test-agent-id").name("test-agent").tools(Arrays.asList(tool3, tool4)).build();
        });
        assertEquals("Duplicate tool defined: type3", e2.getMessage());
    }

    @Test
    public void testToMLAgentWithNullFields() {
        MLAgent originalAgent = MLAgent
            .builder()
            .type(MLAgentType.FLOW.name())
            .createdTime(Instant.ofEpochMilli(1))
            .isHidden(false)
            .name("original-agent")
            .description("original description")
            .llm(LLMSpec.builder().modelId("original-model").build())
            .tools(Collections.singletonList(MLToolSpec.builder().name("original-tool").type("original-type").build()))
            .parameters(Map.of("original", "param"))
            .memory(MLMemorySpec.builder().type("conversation_index").build())
            .build();

        MLAgentUpdateInput emptyInput = MLAgentUpdateInput.builder().agentId("test-agent-id").build();

        MLAgent updatedAgent = emptyInput.toMLAgent(originalAgent);
        assertEquals(originalAgent.getName(), updatedAgent.getName());
        assertEquals(originalAgent.getDescription(), updatedAgent.getDescription());
        assertEquals(originalAgent.getLlm(), updatedAgent.getLlm());
        assertEquals(originalAgent.getTools(), updatedAgent.getTools());
        assertEquals(originalAgent.getParameters(), updatedAgent.getParameters());
        assertEquals(originalAgent.getMemory(), updatedAgent.getMemory());
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        updateAgentInput = MLAgentUpdateInput.builder().agentId("test-agent-id").build();
        String jsonStr = serializationWithToXContent(updateAgentInput);
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertFalse(jsonStr.contains("\"name\""));
    }

    @Test
    public void testParseSuccess() throws Exception {
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
                "type": "conversation_index",
                "session_id": "test-session",
                "window_size": 5
              },
              "app_type": "rag"
            }
            """;
        testParseFromJsonString(inputStr, parsedInput -> {
            assertEquals("test-agent", parsedInput.getName());
            assertEquals("test description", parsedInput.getDescription());
            assertEquals("test-model-id", parsedInput.getLlmModelId());
            assertEquals("5", parsedInput.getLlmParameters().get("max_iteration"));
            assertEquals(1, parsedInput.getTools().size());
            assertEquals("test-tool", parsedInput.getTools().getFirst().getName());
            assertEquals("test", parsedInput.getParameters().get("chat_history"));
            assertEquals("conversation_index", parsedInput.getMemoryType());
            assertEquals("test-session", parsedInput.getMemorySessionId());
            assertEquals(Integer.valueOf(5), parsedInput.getMemoryWindowSize());
            assertEquals("rag", parsedInput.getAppType());
        });
    }

    @Test
    public void testParseWithInvalidField() throws Exception {
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
        // Check LLM fields separately since we now use separate fields
        assertEquals(updateAgentInput.getLlmModelId(), updatedAgent.getLlm().getModelId());
        assertEquals(updateAgentInput.getLlmParameters(), updatedAgent.getLlm().getParameters());
        assertEquals(updateAgentInput.getTools(), updatedAgent.getTools());
        assertEquals(updateAgentInput.getParameters(), updatedAgent.getParameters());
        // Check memory fields separately since we now use separate fields
        assertEquals(updateAgentInput.getMemoryType(), updatedAgent.getMemory().getType());
        assertEquals(updateAgentInput.getMemorySessionId(), updatedAgent.getMemory().getSessionId());
        assertEquals(updateAgentInput.getMemoryWindowSize(), updatedAgent.getMemory().getWindowSize());
        assertEquals(updateAgentInput.getLastUpdateTime(), updatedAgent.getLastUpdateTime());
        assertEquals(updateAgentInput.getAppType(), updatedAgent.getAppType());
    }

    @Test
    public void testReadInputStreamSuccessWithNullFields() throws IOException {
        // Create a new input with null LLM fields
        MLAgentUpdateInput inputWithNulls = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llmModelId(null)
            .llmParameters(null)
            .tools(null)
            .parameters(null)
            .memoryType(null)
            .memorySessionId(null)
            .memoryWindowSize(null)
            .appType("rag")
            .lastUpdateTime(Instant.ofEpochMilli(1))
            .build();

        readInputStream(inputWithNulls, parsedInput -> {
            assertNull(parsedInput.getLlmModelId());
            assertNull(parsedInput.getLlmParameters());
            assertNull(parsedInput.getTools());
            assertNull(parsedInput.getParameters());
            assertNull(parsedInput.getMemoryType());
            assertNull(parsedInput.getMemorySessionId());
            assertNull(parsedInput.getMemoryWindowSize());
        });
    }

    @Test
    public void testReadInputStreamSuccess() throws IOException {
        readInputStream(updateAgentInput, parsedInput -> {
            assertEquals(updateAgentInput.getAgentId(), parsedInput.getAgentId());
            assertEquals(updateAgentInput.getName(), parsedInput.getName());
            assertEquals(updateAgentInput.getDescription(), parsedInput.getDescription());
            assertEquals(updateAgentInput.getLlmModelId(), parsedInput.getLlmModelId());
            assertEquals(updateAgentInput.getLlmParameters(), parsedInput.getLlmParameters());
            assertEquals(updateAgentInput.getTools().size(), parsedInput.getTools().size());
            assertEquals(updateAgentInput.getParameters().size(), parsedInput.getParameters().size());
            assertEquals(updateAgentInput.getMemoryType(), parsedInput.getMemoryType());
            assertEquals(updateAgentInput.getMemorySessionId(), parsedInput.getMemorySessionId());
            assertEquals(updateAgentInput.getMemoryWindowSize(), parsedInput.getMemoryWindowSize());
            assertEquals(updateAgentInput.getAppType(), parsedInput.getAppType());
            assertEquals(updateAgentInput.getLastUpdateTime(), parsedInput.getLastUpdateTime());
        });
    }

    @Test
    public void testParseWithAllFields() throws Exception {
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
                "type": "conversation_index",
                "session_id": "test-session",
                "window_size": 5
              },
              "app_type": "rag",
              "last_updated_time": 1234567890,
              "tenant_id": "test-tenant"
            }
            """;
        testParseFromJsonString(inputStr, parsedInput -> {
            assertEquals("test-agent", parsedInput.getName());
            assertEquals("test description", parsedInput.getDescription());
            assertEquals("test-model-id", parsedInput.getLlmModelId());
            assertEquals(1, parsedInput.getTools().size());
            assertEquals("test-tool", parsedInput.getTools().getFirst().getName());
            assertEquals("test", parsedInput.getParameters().get("chat_history"));
            assertEquals("conversation_index", parsedInput.getMemoryType());
            assertEquals("test-session", parsedInput.getMemorySessionId());
            assertEquals(Integer.valueOf(5), parsedInput.getMemoryWindowSize());
            assertEquals("rag", parsedInput.getAppType());
            assertEquals(1234567890L, parsedInput.getLastUpdateTime().toEpochMilli());
            assertEquals("test-tenant", parsedInput.getTenantId());
        });
    }

    @Test
    public void testToXContentWithAllFields() throws Exception {
        MLAgentUpdateInput input = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llmModelId("test-model-id")
            .llmParameters(Map.of("max_iteration", "5"))
            .tools(
                Collections
                    .singletonList(
                        MLToolSpec.builder().name("test-tool").type("MLModelTool").parameters(Map.of("model_id", "test-model-id")).build()
                    )
            )
            .parameters(Map.of("chat_history", "test"))
            .memoryType("conversation_index")
            .memorySessionId("test-session")
            .memoryWindowSize(5)
            .appType("rag")
            .lastUpdateTime(Instant.ofEpochMilli(1234567890))
            .tenantId("test-tenant")
            .build();

        String jsonStr = serializationWithToXContent(input);
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertTrue(jsonStr.contains("\"name\":\"test-agent\""));
        assertTrue(jsonStr.contains("\"description\":\"test description\""));
        assertTrue(jsonStr.contains("\"model_id\":\"test-model-id\""));
        assertTrue(jsonStr.contains("\"max_iteration\":\"5\""));
        assertTrue(jsonStr.contains("\"chat_history\":\"test\""));
        assertTrue(jsonStr.contains("\"type\":\"conversation_index\""));
        assertTrue(jsonStr.contains("\"app_type\":\"rag\""));
        assertTrue(jsonStr.contains("\"last_updated_time\":1234567890"));
        assertTrue(jsonStr.contains("\"tenant_id\":\"test-tenant\""));
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        MLAgentUpdateInput input = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llmModelId("test-model-id")
            .llmParameters(Map.of("max_iteration", "5"))
            .tools(
                Collections
                    .singletonList(
                        MLToolSpec.builder().name("test-tool").type("MLModelTool").parameters(Map.of("model_id", "test-model-id")).build()
                    )
            )
            .parameters(Map.of("chat_history", "test"))
            .memoryType("conversation_index")
            .memorySessionId("test-session")
            .memoryWindowSize(10)
            .appType("rag")
            .lastUpdateTime(Instant.ofEpochMilli(1234567890))
            .tenantId("test-tenant")
            .build();

        readInputStream(input, parsedInput -> {
            assertEquals(input.getAgentId(), parsedInput.getAgentId());
            assertEquals(input.getName(), parsedInput.getName());
            assertEquals(input.getDescription(), parsedInput.getDescription());
            assertEquals(input.getLlmModelId(), parsedInput.getLlmModelId());
            assertEquals(input.getLlmParameters(), parsedInput.getLlmParameters());
            assertEquals(input.getTools().size(), parsedInput.getTools().size());
            assertEquals(input.getTools().getFirst().getName(), parsedInput.getTools().getFirst().getName());
            assertEquals(input.getTools().getFirst().getType(), parsedInput.getTools().getFirst().getType());
            assertEquals(input.getTools().getFirst().getParameters(), parsedInput.getTools().getFirst().getParameters());
            assertEquals(input.getParameters(), parsedInput.getParameters());
            assertEquals(input.getMemoryType(), parsedInput.getMemoryType());
            assertEquals(input.getMemorySessionId(), parsedInput.getMemorySessionId());
            assertEquals(input.getMemoryWindowSize(), parsedInput.getMemoryWindowSize());
            assertEquals(input.getAppType(), parsedInput.getAppType());
            assertEquals(input.getLastUpdateTime(), parsedInput.getLastUpdateTime());
            assertEquals(input.getTenantId(), parsedInput.getTenantId());
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

    @Test
    public void testLLMPartialUpdate() {
        // Create original agent with LLM configuration
        Map<String, String> originalParams = new HashMap<>();
        originalParams.put("temperature", "0.7");
        originalParams.put("max_tokens", "1000");
        originalParams.put("top_p", "0.9");

        LLMSpec originalLlm = LLMSpec.builder().modelId("original-model").parameters(originalParams).build();

        MLAgent originalAgent = MLAgent
            .builder()
            .name("Test Agent")
            .description("Test description")
            .llm(originalLlm)
            .type(MLAgentType.CONVERSATIONAL.name())
            .build();

        // Test Case 1: Update only model ID
        MLAgentUpdateInput updateInput1 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmModelId("new-model")
            // No llmParameters specified - should keep existing ones
            .build();

        MLAgent updatedAgent1 = updateInput1.toMLAgent(originalAgent);

        assertEquals("new-model", updatedAgent1.getLlm().getModelId());
        assertEquals(originalParams, updatedAgent1.getLlm().getParameters());

        // Test Case 2: Update only parameters
        Map<String, String> updateParams2 = new HashMap<>();
        updateParams2.put("temperature", "0.5"); // Override existing
        updateParams2.put("frequency_penalty", "0.1"); // Add new

        MLAgentUpdateInput updateInput2 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmParameters(updateParams2)
            // No llmModelId specified - should keep existing one
            .build();

        MLAgent updatedAgent2 = updateInput2.toMLAgent(originalAgent);

        assertEquals("original-model", updatedAgent2.getLlm().getModelId());

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("temperature", "0.5");        // Overridden
        expectedParams.put("max_tokens", "1000");        // Kept from original
        expectedParams.put("top_p", "0.9");             // Kept from original
        expectedParams.put("frequency_penalty", "0.1");  // Added

        assertEquals(expectedParams, updatedAgent2.getLlm().getParameters());

        // Test Case 3: Update both model ID and parameters
        Map<String, String> updateParams3 = new HashMap<>();
        updateParams3.put("top_p", "0.95"); // Override existing
        updateParams3.put("presence_penalty", "0.2"); // Add new

        MLAgentUpdateInput updateInput3 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmModelId("another-model")
            .llmParameters(updateParams3)
            .build();

        MLAgent updatedAgent3 = updateInput3.toMLAgent(originalAgent);

        assertEquals("another-model", updatedAgent3.getLlm().getModelId());

        Map<String, String> expectedParams3 = new HashMap<>();
        expectedParams3.put("temperature", "0.7");       // Kept from original
        expectedParams3.put("max_tokens", "1000");       // Kept from original
        expectedParams3.put("top_p", "0.95");           // Overridden
        expectedParams3.put("presence_penalty", "0.2");  // Added

        assertEquals(expectedParams3, updatedAgent3.getLlm().getParameters());

        // Test Case 4: No LLM update - should keep original
        MLAgentUpdateInput updateInput4 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("Updated Name")
            // No LLM fields specified
            .build();

        MLAgent updatedAgent4 = updateInput4.toMLAgent(originalAgent);

        assertEquals("Updated Name", updatedAgent4.getName());
        assertEquals(originalLlm.getModelId(), updatedAgent4.getLlm().getModelId());
        assertEquals(originalLlm.getParameters(), updatedAgent4.getLlm().getParameters());
    }

    @Test
    public void testMemoryPartialUpdate() {
        // Create original agent with memory
        MLMemorySpec originalMemory = MLMemorySpec.builder().type("conversation_index").sessionId("original-session").windowSize(5).build();

        MLAgent originalAgent = MLAgent
            .builder()
            .name("Test Agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .memory(originalMemory)
            .build();

        // Test Case 1: Update only window size
        MLAgentUpdateInput updateInput1 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .memoryWindowSize(10)
            // No memoryType or memorySessionId specified
            .build();

        MLAgent updatedAgent1 = updateInput1.toMLAgent(originalAgent);

        assertEquals("conversation_index", updatedAgent1.getMemory().getType());      // Preserved
        assertEquals("original-session", updatedAgent1.getMemory().getSessionId());  // Preserved
        assertEquals(Integer.valueOf(10), updatedAgent1.getMemory().getWindowSize()); // Updated

        // Test Case 2: Update only session ID
        MLAgentUpdateInput updateInput2 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .memorySessionId("new-session-123")
            // No memoryType or memoryWindowSize specified
            .build();

        MLAgent updatedAgent2 = updateInput2.toMLAgent(originalAgent);

        assertEquals("conversation_index", updatedAgent2.getMemory().getType());        // Preserved
        assertEquals("new-session-123", updatedAgent2.getMemory().getSessionId());     // Updated
        assertEquals(Integer.valueOf(5), updatedAgent2.getMemory().getWindowSize());   // Preserved

        // Test Case 3: Update multiple memory fields
        MLAgentUpdateInput updateInput3 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .memoryType("conversation_index")
            .memorySessionId("another-session")
            .memoryWindowSize(15)
            .build();

        MLAgent updatedAgent3 = updateInput3.toMLAgent(originalAgent);

        assertEquals("conversation_index", updatedAgent3.getMemory().getType());
        assertEquals("another-session", updatedAgent3.getMemory().getSessionId());
        assertEquals(Integer.valueOf(15), updatedAgent3.getMemory().getWindowSize());

        // Test Case 4: No memory update - should keep original
        MLAgentUpdateInput updateInput4 = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("Updated Name")
            // No memory fields specified
            .build();

        MLAgent updatedAgent4 = updateInput4.toMLAgent(originalAgent);

        assertEquals("Updated Name", updatedAgent4.getName());
        assertEquals(originalMemory.getType(), updatedAgent4.getMemory().getType());
        assertEquals(originalMemory.getSessionId(), updatedAgent4.getMemory().getSessionId());
        assertEquals(originalMemory.getWindowSize(), updatedAgent4.getMemory().getWindowSize());
    }

    @Test
    public void testParseWithEmptyMemoryObject() throws Exception {
        String inputStr = """
            {
              "agent_id": "test-agent-id",
              "memory": {}
            }
            """;

        testParseFromJsonString(inputStr, parsedInput -> {
            assertNull(parsedInput.getMemoryType());
            assertNull(parsedInput.getMemorySessionId());
            assertNull(parsedInput.getMemoryWindowSize());
        });
    }

    @Test
    public void testParseWithEmptyLLMObject() throws Exception {
        String inputStr = """
            {
              "agent_id": "test-agent-id",
              "llm": {}
            }
            """;

        testParseFromJsonString(inputStr, parsedInput -> {
            assertNull(parsedInput.getLlmModelId());
            assertNull(parsedInput.getLlmParameters());
        });
    }

    @Test
    public void testParseWithUnknownMemoryFields() throws Exception {
        String inputStr = """
            {
              "agent_id": "test-agent-id",
              "memory": {
                "type": "conversation_index",
                "unknown_field": "should_be_ignored",
                "another_unknown": 123
              }
            }
            """;

        testParseFromJsonString(inputStr, parsedInput -> {
            assertEquals("conversation_index", parsedInput.getMemoryType());
            assertNull(parsedInput.getMemorySessionId());
            assertNull(parsedInput.getMemoryWindowSize());
        });
    }

    @Test
    public void testParseWithUnknownLLMFields() throws Exception {
        String inputStr = """
            {
              "agent_id": "test-agent-id",
              "llm": {
                "model_id": "test-model",
                "unknown_field": "should_be_ignored",
                "another_unknown": 123
              }
            }
            """;

        testParseFromJsonString(inputStr, parsedInput -> {
            assertEquals("test-model", parsedInput.getLlmModelId());
            assertNull(parsedInput.getLlmParameters());
        });
    }

    @Test
    public void testToMLAgentWithConversationalAgentCanUpdateLLMParameters() {
        // Create original CONVERSATIONAL agent with existing LLM
        LLMSpec originalLlm = LLMSpec.builder().modelId("original-model-id").parameters(Map.of("existing_param", "value")).build();

        MLAgent originalAgent = MLAgent.builder().name("Test Agent").type(MLAgentType.CONVERSATIONAL.name()).llm(originalLlm).build();

        // Update LLM parameters without providing model ID (should use existing model ID)
        MLAgentUpdateInput updateInput = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmParameters(Map.of("temperature", "0.7"))
            // No llmModelId provided, but CONVERSATIONAL agent has existing LLM
            .build();

        // This should work fine and use the existing model ID
        MLAgent updatedAgent = updateInput.toMLAgent(originalAgent);

        assertNotNull(updatedAgent.getLlm());
        assertEquals("original-model-id", updatedAgent.getLlm().getModelId());
        assertEquals("0.7", updatedAgent.getLlm().getParameters().get("temperature"));
        assertEquals("value", updatedAgent.getLlm().getParameters().get("existing_param"));
    }

    @Test
    public void testToMLAgentCanUpdateLLMParametersWhenModelIdProvided() {
        // Create original agent without LLM (any type)
        MLAgent originalAgent = MLAgent.builder().name("Test Agent").type(MLAgentType.FLOW.name()).build(); // No original LLM

        // Update LLM parameters WITH providing model ID (should work for any agent)
        MLAgentUpdateInput updateInput = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmModelId("new-model-id")
            .llmParameters(Map.of("temperature", "0.7"))
            .build();

        // This should work fine since we provided the model ID
        MLAgent updatedAgent = updateInput.toMLAgent(originalAgent);

        assertNotNull(updatedAgent.getLlm());
        assertEquals("new-model-id", updatedAgent.getLlm().getModelId());
        assertEquals("0.7", updatedAgent.getLlm().getParameters().get("temperature"));
    }

    @Test
    public void testToMLAgentPreservesAllOriginalFields() {
        Instant createdTime = Instant.now();
        MLAgent originalAgent = MLAgent
            .builder()
            .name("Original Agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("Original description")
            .createdTime(createdTime)
            .isHidden(true)
            .llm(LLMSpec.builder().modelId("original-model").parameters(Map.of("temp", "0.5")).build())
            .tools(Collections.singletonList(MLToolSpec.builder().name("original-tool").type("original-type").build()))
            .parameters(Map.of("original", "param"))
            .memory(MLMemorySpec.builder().type("conversation_index").sessionId("original-session").windowSize(5).build())
            .appType("original-app")
            .build();

        // Update only the name
        MLAgentUpdateInput updateInput = MLAgentUpdateInput.builder().agentId("test-agent-id").name("Updated Name").build();

        MLAgent updatedAgent = updateInput.toMLAgent(originalAgent);

        // Check that only name was updated, everything else preserved
        assertEquals("Updated Name", updatedAgent.getName());
        assertEquals(originalAgent.getType(), updatedAgent.getType());
        assertEquals(originalAgent.getDescription(), updatedAgent.getDescription());
        assertEquals(originalAgent.getCreatedTime(), updatedAgent.getCreatedTime());
        assertEquals(originalAgent.getIsHidden(), updatedAgent.getIsHidden());
        assertEquals(originalAgent.getLlm().getModelId(), updatedAgent.getLlm().getModelId());
        assertEquals(originalAgent.getLlm().getParameters(), updatedAgent.getLlm().getParameters());
        assertEquals(originalAgent.getTools(), updatedAgent.getTools());
        assertEquals(originalAgent.getParameters(), updatedAgent.getParameters());
        assertEquals(originalAgent.getMemory().getType(), updatedAgent.getMemory().getType());
        assertEquals(originalAgent.getMemory().getSessionId(), updatedAgent.getMemory().getSessionId());
        assertEquals(originalAgent.getMemory().getWindowSize(), updatedAgent.getMemory().getWindowSize());
        // appType is set to null because updateInput.appType is null
        assertNull(updatedAgent.getAppType());
    }

    @Test
    public void testToXContentWithNullValues() throws Exception {
        MLAgentUpdateInput input = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name(null)
            .description(null)
            .llmModelId(null)
            .llmParameters(null)
            .tools(null)
            .parameters(null)
            .memoryType(null)
            .memorySessionId(null)
            .memoryWindowSize(null)
            .appType(null)
            .lastUpdateTime(null)
            .tenantId(null)
            .build();

        String jsonStr = serializationWithToXContent(input);

        // Should only contain agent_id
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertFalse(jsonStr.contains("\"name\""));
        assertFalse(jsonStr.contains("\"description\""));
        assertFalse(jsonStr.contains("\"llm\""));
        assertFalse(jsonStr.contains("\"tools\""));
        assertFalse(jsonStr.contains("\"parameters\""));
        assertFalse(jsonStr.contains("\"memory\""));
        assertFalse(jsonStr.contains("\"app_type\""));
        assertFalse(jsonStr.contains("\"last_updated_time\""));
        assertFalse(jsonStr.contains("\"tenant_id\""));
    }

    @Test
    public void testToXContentWithEmptyCollections() throws Exception {
        MLAgentUpdateInput input = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmParameters(Collections.emptyMap())
            .tools(Collections.emptyList())
            .parameters(Collections.emptyMap())
            .build();

        String jsonStr = serializationWithToXContent(input);

        // Should only contain agent_id (empty collections should not be serialized)
        assertTrue(jsonStr.contains("\"agent_id\":\"test-agent-id\""));
        assertFalse(jsonStr.contains("\"llm\""));
        assertFalse(jsonStr.contains("\"tools\""));
        assertFalse(jsonStr.contains("\"parameters\""));
    }

    @Test
    public void testCombinedLLMAndMemoryPartialUpdates() {
        // Create original agent with both LLM and memory
        LLMSpec originalLlm = LLMSpec
            .builder()
            .modelId("original-model")
            .parameters(Map.of("temperature", "0.5", "max_tokens", "100"))
            .build();

        MLMemorySpec originalMemory = MLMemorySpec.builder().type("conversation_index").sessionId("original-session").windowSize(5).build();

        MLAgent originalAgent = MLAgent
            .builder()
            .name("Test Agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(originalLlm)
            .memory(originalMemory)
            .build();

        // Update some LLM and memory fields simultaneously
        MLAgentUpdateInput updateInput = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .llmParameters(Map.of("temperature", "0.8")) // Update LLM parameter
            .memoryWindowSize(10) // Update memory window size
            .build();

        MLAgent updatedAgent = updateInput.toMLAgent(originalAgent);

        // Check LLM merging
        assertEquals("original-model", updatedAgent.getLlm().getModelId()); // Preserved
        assertEquals("0.8", updatedAgent.getLlm().getParameters().get("temperature")); // Updated
        assertEquals("100", updatedAgent.getLlm().getParameters().get("max_tokens")); // Preserved

        // Check memory merging
        assertEquals("conversation_index", updatedAgent.getMemory().getType()); // Preserved
        assertEquals("original-session", updatedAgent.getMemory().getSessionId()); // Preserved
        assertEquals(Integer.valueOf(10), updatedAgent.getMemory().getWindowSize()); // Updated
    }

    @Test
    public void testStreamInputOutputWithVersion() throws IOException {
        MLAgentUpdateInput input = MLAgentUpdateInput
            .builder()
            .agentId("test-agent-id")
            .name("test-agent")
            .description("test description")
            .llmModelId("test-model-id")
            .llmParameters(Map.of("temperature", "0.7"))
            .memoryType("conversation_index")
            .memorySessionId("test-session")
            .memoryWindowSize(10)
            .appType("rag")
            .lastUpdateTime(Instant.ofEpochMilli(1234567890))
            .tenantId("test-tenant")
            .build();

        // Test with different versions
        BytesStreamOutput output = new BytesStreamOutput();
        input.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        MLAgentUpdateInput parsedInput = new MLAgentUpdateInput(streamInput);

        assertEquals(input.getAgentId(), parsedInput.getAgentId());
        assertEquals(input.getName(), parsedInput.getName());
        assertEquals(input.getDescription(), parsedInput.getDescription());
        assertEquals(input.getLlmModelId(), parsedInput.getLlmModelId());
        assertEquals(input.getLlmParameters(), parsedInput.getLlmParameters());
        assertEquals(input.getMemoryType(), parsedInput.getMemoryType());
        assertEquals(input.getMemorySessionId(), parsedInput.getMemorySessionId());
        assertEquals(input.getMemoryWindowSize(), parsedInput.getMemoryWindowSize());
        assertEquals(input.getAppType(), parsedInput.getAppType());
        assertEquals(input.getLastUpdateTime(), parsedInput.getLastUpdateTime());
        assertEquals(input.getTenantId(), parsedInput.getTenantId());
    }
}
