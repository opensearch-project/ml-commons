/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MLAGUIAgentRunnerTest {

    private static final String TOOL_NAME = "testTool";
    private static final Gson gson = new Gson();

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private Map<String, Memory.Factory> memoryFactoryMap;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private Encryptor encryptor;

    @Mock
    private Tool.Factory toolFactory;

    @Mock
    private Tool tool;

    @Mock
    private ActionListener<Object> agentActionListener;

    private Settings settings;
    private Map<String, Tool.Factory> toolFactories;
    private TestableMLAGUIAgentRunner aguiAgentRunner;

    // Custom test class that exposes processAGUIMessages and processAGUIContext for testing
    private static class TestableMLAGUIAgentRunner extends MLAGUIAgentRunner {
        public TestableMLAGUIAgentRunner(
            Client client,
            Settings settings,
            ClusterService clusterService,
            NamedXContentRegistry xContentRegistry,
            Map<String, Tool.Factory> toolFactories,
            Map<String, Memory.Factory> memoryFactoryMap,
            SdkClient sdkClient,
            Encryptor encryptor
        ) {
            super(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap, sdkClient, encryptor);
        }

        // Expose the private methods for testing
        public void testProcessAGUIMessages(Map<String, String> params, String llmInterface) {
            // Use reflection to call the private method
            try {
                java.lang.reflect.Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIMessages", Map.class, String.class);
                method.setAccessible(true);
                method.invoke(this, params, llmInterface);
            } catch (Exception e) {
                throw new RuntimeException("Failed to call processAGUIMessages", e);
            }
        }

        public void testProcessAGUIContext(Map<String, String> params) {
            try {
                java.lang.reflect.Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", Map.class);
                method.setAccessible(true);
                method.invoke(this, params);
            } catch (Exception e) {
                throw new RuntimeException("Failed to call processAGUIContext", e);
            }
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = new HashMap<>();
        toolFactories.put(TOOL_NAME, toolFactory);

        aguiAgentRunner = new TestableMLAGUIAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            sdkClient,
            encryptor
        );

        when(toolFactory.create(any())).thenReturn(tool);
        when(tool.getName()).thenReturn(TOOL_NAME);
        when(tool.getDescription()).thenReturn("Test tool description");
    }

    @Test
    public void testProcessAGUIMessages_EmptyMessages() {
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.testProcessAGUIMessages(params, null);

        // Empty messages should not add any parameters
        assertFalse(params.containsKey(AGUI_PARAM_TOOL_CALL_RESULTS));
        assertFalse(params.containsKey(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES));
    }

    @Test
    public void testProcessAGUIMessages_NullMessages() {
        Map<String, String> params = new HashMap<>();

        aguiAgentRunner.testProcessAGUIMessages(params, null);

        // Null messages should not cause errors and should not add parameters
        assertFalse(params.containsKey(AGUI_PARAM_TOOL_CALL_RESULTS));
        assertFalse(params.containsKey(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES));
    }

    @Test
    public void testProcessAGUIMessages_SingleUserMessage() {
        Map<String, String> params = new HashMap<>();

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        aguiAgentRunner.testProcessAGUIMessages(params, null);

        // Single message should not extract any tool data
        assertFalse(params.containsKey(AGUI_PARAM_TOOL_CALL_RESULTS));
        assertFalse(params.containsKey(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES));
    }

    @Test
    public void testProcessAGUIMessages_WithToolCallsAndResults() {
        Map<String, String> params = new HashMap<>();

        JsonArray messages = new JsonArray();

        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "What is the weather?");
        messages.add(userMsg);

        // Assistant message with tool calls
        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");

        JsonArray toolCalls = new JsonArray();
        JsonObject toolCall = new JsonObject();
        toolCall.addProperty("id", "call-123");
        toolCall.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", "get_weather");
        function.addProperty("arguments", "{\"location\":\"NYC\"}");
        toolCall.add("function", function);

        toolCalls.add(toolCall);
        assistantMsg.add("toolCalls", toolCalls);
        messages.add(assistantMsg);

        // Tool result message
        JsonObject toolResultMsg = new JsonObject();
        toolResultMsg.addProperty("role", "tool");
        toolResultMsg.addProperty("content", "72 degrees");
        toolResultMsg.addProperty("toolCallId", "call-123");
        messages.add(toolResultMsg);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        // When llmInterface is not null, FunctionCalling will be used
        // For testing, we pass null to skip FunctionCalling formatting
        aguiAgentRunner.testProcessAGUIMessages(params, null);

        // Should have tool call results
        assertTrue(params.containsKey(AGUI_PARAM_TOOL_CALL_RESULTS));
        String toolCallResults = params.get(AGUI_PARAM_TOOL_CALL_RESULTS);
        assertNotNull(toolCallResults);
        assertTrue(toolCallResults.contains("call-123"));
        assertTrue(toolCallResults.contains("72 degrees"));

        // Without llmInterface, assistant tool call messages won't be formatted
        // But tool results should still be extracted
    }

    @Test
    public void testProcessAGUIMessages_MultipleToolResults() {
        Map<String, String> params = new HashMap<>();

        JsonArray messages = new JsonArray();

        // First tool call and result
        JsonObject assistantMsg1 = new JsonObject();
        assistantMsg1.addProperty("role", "assistant");
        JsonArray toolCalls1 = new JsonArray();
        JsonObject toolCall1 = new JsonObject();
        toolCall1.addProperty("id", "call-1");
        toolCalls1.add(toolCall1);
        assistantMsg1.add("toolCalls", toolCalls1);
        messages.add(assistantMsg1);

        JsonObject toolResult1 = new JsonObject();
        toolResult1.addProperty("role", "tool");
        toolResult1.addProperty("content", "First result");
        toolResult1.addProperty("toolCallId", "call-1");
        messages.add(toolResult1);

        // Second tool call and result
        JsonObject assistantMsg2 = new JsonObject();
        assistantMsg2.addProperty("role", "assistant");
        JsonArray toolCalls2 = new JsonArray();
        JsonObject toolCall2 = new JsonObject();
        toolCall2.addProperty("id", "call-2");
        toolCalls2.add(toolCall2);
        assistantMsg2.add("toolCalls", toolCalls2);
        messages.add(assistantMsg2);

        JsonObject toolResult2 = new JsonObject();
        toolResult2.addProperty("role", "tool");
        toolResult2.addProperty("content", "Second result");
        toolResult2.addProperty("toolCallId", "call-2");
        messages.add(toolResult2);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        // Pass null for llmInterface to skip FunctionCalling formatting
        aguiAgentRunner.testProcessAGUIMessages(params, null);

        // Should extract ALL tool results
        String toolCallResults = params.get(AGUI_PARAM_TOOL_CALL_RESULTS);
        assertNotNull(toolCallResults);
        assertTrue(toolCallResults.contains("call-1"));
        assertTrue(toolCallResults.contains("First result"));
        assertTrue(toolCallResults.contains("call-2"));
        assertTrue(toolCallResults.contains("Second result"));
    }

    @Test
    public void testProcessAGUIContext_ValidContext() {
        Map<String, String> params = new HashMap<>();

        JsonArray context = new JsonArray();
        JsonObject contextItem1 = new JsonObject();
        contextItem1.addProperty("description", "User Location");
        contextItem1.addProperty("value", "San Francisco");
        context.add(contextItem1);

        JsonObject contextItem2 = new JsonObject();
        contextItem2.addProperty("description", "User Timezone");
        contextItem2.addProperty("value", "PST");
        context.add(contextItem2);

        params.put(AGUI_PARAM_CONTEXT, gson.toJson(context));

        aguiAgentRunner.testProcessAGUIContext(params);

        // Context should be processed into a formatted string
        String processedContext = params.get(MLChatAgentRunner.CONTEXT);
        assertNotNull(processedContext);
        assertTrue(processedContext.contains("User Location"));
        assertTrue(processedContext.contains("San Francisco"));
        assertTrue(processedContext.contains("User Timezone"));
        assertTrue(processedContext.contains("PST"));
    }

    @Test
    public void testProcessAGUIContext_EmptyContext() {
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_CONTEXT, "[]");

        aguiAgentRunner.testProcessAGUIContext(params);

        // Empty context should not set the CONTEXT parameter
        String processedContext = params.get(MLChatAgentRunner.CONTEXT);
        assertTrue(processedContext == null || processedContext.isEmpty());
    }

    @Test
    public void testProcessAGUIContext_NullContext() {
        Map<String, String> params = new HashMap<>();

        aguiAgentRunner.testProcessAGUIContext(params);

        // Null context should not cause errors
        assertFalse(params.containsKey(MLChatAgentRunner.CONTEXT));
    }

    @Test
    public void testRun_SetsAgentType() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_MESSAGES, "[]");

        // We don't wait for completion, just verify the agent_type was set
        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should set agent_type parameter before delegation
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testRun_ErrorHandling_InvalidMessages() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        // Set invalid messages JSON to trigger error
        params.put(AGUI_PARAM_MESSAGES, "invalid json");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should call listener.onFailure
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(exceptionCaptor.capture());
        assertNotNull(exceptionCaptor.getValue());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalArgumentException);
    }

    @Test
    public void testProcessAGUIContext_MultipleFields() {
        Map<String, String> params = new HashMap<>();

        JsonArray context = new JsonArray();
        for (int i = 1; i <= 5; i++) {
            JsonObject contextItem = new JsonObject();
            contextItem.addProperty("description", "Field" + i);
            contextItem.addProperty("value", "Value" + i);
            context.add(contextItem);
        }

        params.put(AGUI_PARAM_CONTEXT, gson.toJson(context));

        aguiAgentRunner.testProcessAGUIContext(params);

        String processedContext = params.get(MLChatAgentRunner.CONTEXT);
        assertNotNull(processedContext);

        // Verify all fields are present
        for (int i = 1; i <= 5; i++) {
            assertTrue(processedContext.contains("Field" + i));
            assertTrue(processedContext.contains("Value" + i));
        }
    }

    // Helper method to create a basic ML Agent
    private MLAgent createBasicMLAgent() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("test-model-id").build();
        MLToolSpec toolSpec = MLToolSpec.builder().name(TOOL_NAME).type(TOOL_NAME).build();

        return MLAgent.builder().name("TestAGUIAgent").type(MLAgentType.AG_UI.name()).llm(llmSpec).tools(Arrays.asList(toolSpec)).build();
    }
}
