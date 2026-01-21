/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AGUIInputConverterTest {

    @Test
    public void testIsAGUIInput_ValidInput() {
        String validInput = createValidAGUIInput();
        assertTrue(AGUIInputConverter.isAGUIInput(validInput));
    }

    @Test
    public void testIsAGUIInput_MissingThreadId() {
        JsonObject input = new JsonObject();
        input.addProperty("runId", "run-123");
        input.add("messages", new JsonArray());
        input.add("tools", new JsonArray());

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_MissingRunId() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.add("messages", new JsonArray());
        input.add("tools", new JsonArray());

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_MissingMessages() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.addProperty("runId", "run-123");
        input.add("tools", new JsonArray());

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_MissingTools() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.addProperty("runId", "run-123");
        input.add("messages", new JsonArray());

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_MessagesNotArray() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.addProperty("runId", "run-123");
        input.addProperty("messages", "not an array");
        input.add("tools", new JsonArray());

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_ToolsNotArray() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.addProperty("runId", "run-123");
        input.add("messages", new JsonArray());
        input.addProperty("tools", "not an array");

        assertFalse(AGUIInputConverter.isAGUIInput(input.toString()));
    }

    @Test
    public void testIsAGUIInput_InvalidJson() {
        String invalidJson = "{ invalid json }";
        assertFalse(AGUIInputConverter.isAGUIInput(invalidJson));
    }

    @Test
    public void testConvertFromAGUIInput_BasicFields() {
        String aguiInput = createValidAGUIInput();
        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput, "agent-123", "tenant-456", false);

        assertNotNull(result);
        assertEquals("agent-123", result.getAgentId());
        assertEquals("tenant-456", result.getTenantId());
        assertEquals(FunctionName.AGENT, result.getAlgorithm());
        assertFalse(result.getIsAsync());

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertNotNull(dataSet);
        Map<String, String> parameters = dataSet.getParameters();

        assertEquals("thread-123", parameters.get(AGUI_PARAM_THREAD_ID));
        assertEquals("run-123", parameters.get(AGUI_PARAM_RUN_ID));
        assertNotNull(parameters.get(AGUI_PARAM_MESSAGES));
        assertNotNull(parameters.get(AGUI_PARAM_TOOLS));
    }

    @Test
    public void testConvertFromAGUIInput_WithState() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonObject state = new JsonObject();
        state.addProperty("key1", "value1");
        aguiInput.add("state", state);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", true);

        assertNotNull(result);
        assertTrue(result.getIsAsync());
        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        assertNotNull(parameters.get(AGUI_PARAM_STATE));
        assertTrue(parameters.get(AGUI_PARAM_STATE).contains("key1"));
    }

    @Test
    public void testConvertFromAGUIInput_WithContext() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray context = new JsonArray();
        JsonObject contextItem = new JsonObject();
        contextItem.addProperty("description", "Test context");
        contextItem.addProperty("value", "Test value");
        context.add(contextItem);
        aguiInput.add("context", context);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        assertNotNull(parameters.get(AGUI_PARAM_CONTEXT));
    }

    @Test
    public void testConvertFromAGUIInput_WithForwardedProps() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonObject forwardedProps = new JsonObject();
        forwardedProps.addProperty("prop1", "value1");
        aguiInput.add("forwardedProps", forwardedProps);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        assertNotNull(parameters.get(AGUI_PARAM_FORWARDED_PROPS));
    }

    @Test
    public void testConvertFromAGUIInput_UserMessage() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "What is the weather?");
        messages.add(userMessage);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        assertEquals("What is the weather?", parameters.get("question"));
        assertNull(parameters.get(AGUI_PARAM_TOOL_CALL_RESULTS));
    }

    @Test
    public void testConvertFromAGUIInput_ToolCallResult() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();

        // Add assistant message with tool call
        JsonObject assistantMessage = new JsonObject();
        assistantMessage.addProperty("role", "assistant");
        assistantMessage.add("toolCalls", new JsonArray());
        messages.add(assistantMessage);

        // Add tool result message
        JsonObject toolResultMessage = new JsonObject();
        toolResultMessage.addProperty("role", "user");
        toolResultMessage.addProperty("content", "Tool result content");
        toolResultMessage.addProperty("toolCallId", "call-123");
        messages.add(toolResultMessage);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        assertNotNull(parameters.get(AGUI_PARAM_TOOL_CALL_RESULTS));
        assertTrue(parameters.get(AGUI_PARAM_TOOL_CALL_RESULTS).contains("call-123"));
        assertTrue(parameters.get(AGUI_PARAM_TOOL_CALL_RESULTS).contains("Tool result content"));
    }

    @Test
    public void testConvertFromAGUIInput_MultipleUserMessages() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();

        // Add first user message
        JsonObject userMessage1 = new JsonObject();
        userMessage1.addProperty("role", "user");
        userMessage1.addProperty("content", "First question");
        messages.add(userMessage1);

        // Add assistant message
        JsonObject assistantMessage = new JsonObject();
        assistantMessage.addProperty("role", "assistant");
        assistantMessage.addProperty("content", "First answer");
        messages.add(assistantMessage);

        // Add second user message (should be used as question)
        JsonObject userMessage2 = new JsonObject();
        userMessage2.addProperty("role", "user");
        userMessage2.addProperty("content", "Second question");
        messages.add(userMessage2);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> parameters = dataSet.getParameters();

        // Should extract the last user message as the question
        assertEquals("Second question", parameters.get("question"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_NoUserMessage() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();

        // Add only assistant message
        JsonObject assistantMessage = new JsonObject();
        assistantMessage.addProperty("role", "assistant");
        assistantMessage.addProperty("content", "Assistant response");
        messages.add(assistantMessage);

        aguiInput.add("messages", messages);

        AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_InvalidMessages() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        aguiInput.addProperty("messages", "not a valid json array");

        AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_InvalidJson() {
        String invalidJson = "{ invalid json }";
        AGUIInputConverter.convertFromAGUIInput(invalidJson, "agent-123", "tenant-456", false);
    }

    @Test
    public void testConvertFromAGUIInput_EmptyMessages() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();
        aguiInput.add("messages", messages);

        try {
            AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No user message found"));
        }
    }

    @Test
    public void testConvertFromAGUIInput_NullContent() {
        JsonObject aguiInput = JsonParser.parseString(createValidAGUIInput()).getAsJsonObject();
        JsonArray messages = new JsonArray();

        // Add user message with null content
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.add("content", null);
        messages.add(userMessage);

        aguiInput.add("messages", messages);

        try {
            AGUIInputConverter.convertFromAGUIInput(aguiInput.toString(), "agent-123", "tenant-456", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No user message found"));
        }
    }

    // Helper method to create valid AG-UI input
    private String createValidAGUIInput() {
        JsonObject input = new JsonObject();
        input.addProperty("threadId", "thread-123");
        input.addProperty("runId", "run-123");

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", "Test question");
        messages.add(message);
        input.add("messages", messages);

        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("name", "test_tool");
        tools.add(tool);
        input.add("tools", tools);

        return input.toString();
    }
}
