/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.NEW_CHAT_HISTORY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
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
    private MLAGUIAgentRunner aguiAgentRunner;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = new HashMap<>();
        toolFactories.put(TOOL_NAME, toolFactory);

        aguiAgentRunner = new MLAGUIAgentRunner(
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
        when(tool.validate(any())).thenReturn(true);

        // Mock LLM response with final answer
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            Map<String, String> tensorData = new HashMap<>();
            tensorData.put("final_answer", "Test response");
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(tensorData).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            MLTaskResponse response = MLTaskResponse.builder().output(output).build();
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(ActionType.class), any(ActionRequest.class), any(ActionListener.class));
    }

    @Test
    public void testProcessAGUIMessages_EmptyMessages() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should not throw exception and should complete
        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIMessages_UserMessageOnly() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Chat history should be empty for single message
        String chatHistory = params.get(NEW_CHAT_HISTORY);
        assertTrue(chatHistory == null || chatHistory.isEmpty());
        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIMessages_UserAndAssistantMessages() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        // Add required templates for chat history
        params.put(MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE, "Human: ${question}");
        params.put(MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE, "AI: ${response}");

        JsonArray messages = new JsonArray();

        // First user message
        JsonObject userMsg1 = new JsonObject();
        userMsg1.addProperty("role", "user");
        userMsg1.addProperty("content", "First question");
        messages.add(userMsg1);

        // Assistant response
        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.addProperty("content", "First answer");
        messages.add(assistantMsg);

        // Second user message
        JsonObject userMsg2 = new JsonObject();
        userMsg2.addProperty("role", "user");
        userMsg2.addProperty("content", "Second question");
        messages.add(userMsg2);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Chat history should contain the first interaction
        String chatHistory = params.get(NEW_CHAT_HISTORY);
        assertNotNull(chatHistory);
        assertTrue(chatHistory.contains("First question"));
        assertTrue(chatHistory.contains("First answer"));
        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIMessages_ToolCallAndResult() {
        MLAgent mlAgent = createBasicMLAgent();
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

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should have tool call results
        String toolCallResults = params.get(AGUI_PARAM_TOOL_CALL_RESULTS);
        assertNotNull(toolCallResults);
        assertTrue(toolCallResults.contains("call-123"));
        assertTrue(toolCallResults.contains("72 degrees"));

        // Should have assistant tool call messages
        String assistantToolCallMessages = params.get(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES);
        assertNotNull(assistantToolCallMessages);

        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIMessages_IncludeToolMessagesInChatHistory() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        // Add required templates for chat history
        params.put(MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE, "Human: ${question}");
        params.put(MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE, "AI: ${response}");

        JsonArray messages = new JsonArray();

        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "First question");
        messages.add(userMsg);

        // Assistant message with tool call (should NOW be included)
        JsonObject assistantToolCallMsg = new JsonObject();
        assistantToolCallMsg.addProperty("role", "assistant");
        assistantToolCallMsg.addProperty("content", "Let me check the weather");
        assistantToolCallMsg.add("toolCalls", new JsonArray());
        messages.add(assistantToolCallMsg);

        // Tool result (should NOW be included)
        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("content", "Tool result: 72 degrees");
        toolMsg.addProperty("toolCallId", "call-123");
        messages.add(toolMsg);

        // Final assistant answer (should be included)
        JsonObject assistantAnswerMsg = new JsonObject();
        assistantAnswerMsg.addProperty("role", "assistant");
        assistantAnswerMsg.addProperty("content", "Final answer");
        messages.add(assistantAnswerMsg);

        // New user message
        JsonObject userMsg2 = new JsonObject();
        userMsg2.addProperty("role", "user");
        userMsg2.addProperty("content", "Second question");
        messages.add(userMsg2);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Chat history should NOW include tool messages and assistant messages with tool calls
        String chatHistory = params.get(NEW_CHAT_HISTORY);
        assertNotNull(chatHistory);
        assertTrue(chatHistory.contains("First question"));
        assertTrue(chatHistory.contains("Let me check the weather"));  // Assistant with tool call
        assertTrue(chatHistory.contains("Tool result: 72 degrees"));    // Tool message
        assertTrue(chatHistory.contains("Final answer"));

        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIMessages_MultipleToolCalls_OnlyMostRecent() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        // Add required templates for chat history
        params.put(MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE, "Human: ${question}");
        params.put(MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE, "AI: ${response}");

        JsonArray messages = new JsonArray();

        // First tool call sequence
        JsonObject assistantMsg1 = new JsonObject();
        assistantMsg1.addProperty("role", "assistant");
        JsonArray toolCalls1 = new JsonArray();
        JsonObject toolCall1 = new JsonObject();
        toolCall1.addProperty("id", "call-old");
        toolCalls1.add(toolCall1);
        assistantMsg1.add("toolCalls", toolCalls1);
        messages.add(assistantMsg1);

        JsonObject toolResult1 = new JsonObject();
        toolResult1.addProperty("role", "tool");
        toolResult1.addProperty("content", "Old result");
        toolResult1.addProperty("toolCallId", "call-old");
        messages.add(toolResult1);

        // Assistant final answer
        JsonObject assistantAnswer = new JsonObject();
        assistantAnswer.addProperty("role", "assistant");
        assistantAnswer.addProperty("content", "Intermediate answer");
        messages.add(assistantAnswer);

        // Second tool call sequence (most recent - should be included)
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Follow up question");
        messages.add(userMsg);

        JsonObject assistantMsg2 = new JsonObject();
        assistantMsg2.addProperty("role", "assistant");
        JsonArray toolCalls2 = new JsonArray();
        JsonObject toolCall2 = new JsonObject();
        toolCall2.addProperty("id", "call-new");
        toolCalls2.add(toolCall2);
        assistantMsg2.add("toolCalls", toolCalls2);
        messages.add(assistantMsg2);

        JsonObject toolResult2 = new JsonObject();
        toolResult2.addProperty("role", "tool");
        toolResult2.addProperty("content", "New result");
        toolResult2.addProperty("toolCallId", "call-new");
        messages.add(toolResult2);

        params.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should only have the most recent tool call results
        String toolCallResults = params.get(AGUI_PARAM_TOOL_CALL_RESULTS);
        assertNotNull(toolCallResults);
        assertTrue(toolCallResults.contains("call-new"));
        assertTrue(toolCallResults.contains("New result"));
        // Old tool call should not be included
        assertTrue(!toolCallResults.contains("call-old"));

        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIContext_ValidContext() {
        MLAgent mlAgent = createBasicMLAgent();
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
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Context should be processed into a formatted string
        String processedContext = params.get(MLChatAgentRunner.CONTEXT);
        assertNotNull(processedContext);
        assertTrue(processedContext.contains("User Location"));
        assertTrue(processedContext.contains("San Francisco"));
        assertTrue(processedContext.contains("User Timezone"));
        assertTrue(processedContext.contains("PST"));

        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIContext_EmptyContext() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        params.put(AGUI_PARAM_CONTEXT, "[]");
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Empty context should not set the CONTEXT parameter
        String processedContext = params.get(MLChatAgentRunner.CONTEXT);
        assertTrue(processedContext == null || processedContext.isEmpty());

        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testProcessAGUIContext_NullContext() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Null context should not cause any issues
        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testRun_SetsAgentType() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_MESSAGES, "[]");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should set agent_type parameter
        assertEquals("ag_ui", params.get("agent_type"));
        verify(agentActionListener).onResponse(any());
    }

    @Test
    public void testRun_ErrorHandling() {
        MLAgent mlAgent = createBasicMLAgent();
        Map<String, String> params = new HashMap<>();

        // Set invalid messages JSON to trigger error
        params.put(AGUI_PARAM_MESSAGES, "invalid json");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, null);

        // Should call listener.onFailure
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(exceptionCaptor.capture());
        assertNotNull(exceptionCaptor.getValue());
    }

    // Note: Test for HookRegistry constructor will be added once HookRegistry is merged

    // Helper method to create a basic ML Agent
    private MLAgent createBasicMLAgent() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("test-model-id").build();
        MLToolSpec toolSpec = MLToolSpec.builder().name(TOOL_NAME).type(TOOL_NAME).build();

        return MLAgent.builder().name("TestAGUIAgent").type(MLAgentType.AG_UI.name()).llm(llmSpec).tools(Arrays.asList(toolSpec)).build();
    }
}
