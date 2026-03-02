/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_LOAD_CHAT_HISTORY;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AGUIInputConverterTest {

    private static final Gson gson = new Gson();

    @Test
    public void testIsAGUIInput_ValidInput() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertTrue(result);
    }

    @Test
    public void testIsAGUIInput_MissingThreadId() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingRunId() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingState() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingMessages() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingTools() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingContext() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingForwardedProps() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MessagesNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.addProperty("messages", "not an array");
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_ToolsNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.addProperty("tools", "not an array");
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_ContextNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.addProperty("context", "not an array");
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_InvalidJson() {
        boolean result = AGUIInputConverter.isAGUIInput("invalid json");

        assertFalse(result);
    }

    @Test
    public void testConvertFromAGUIInput_BasicConversion() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", "tenant-id", false);

        assertNotNull(result);
        assertEquals("agent-id", result.getAgentId());
        assertEquals("tenant-id", result.getTenantId());
        assertEquals(FunctionName.AGENT, result.getFunctionName());
        assertFalse(result.getIsAsync());

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertNotNull(dataset);
        Map<String, String> params = dataset.getParameters();
        assertEquals("thread-123", params.get(AGUI_PARAM_THREAD_ID));
        assertEquals("run-456", params.get(AGUI_PARAM_RUN_ID));
    }

    @Test
    public void testConvertFromAGUIInput_WithState() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonObject state = new JsonObject();
        state.addProperty("key", "value");
        aguiInput.add("state", state);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, true);

        assertNotNull(result);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();
        assertTrue(params.containsKey(AGUI_PARAM_STATE));
        assertEquals("{\"key\":\"value\"}", params.get(AGUI_PARAM_STATE));
    }

    @Test
    public void testConvertFromAGUIInput_WithContext() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonArray context = new JsonArray();
        JsonObject contextItem = new JsonObject();
        contextItem.addProperty("description", "Location");
        contextItem.addProperty("value", "SF");
        context.add(contextItem);
        aguiInput.add("context", context);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();
        assertTrue(params.containsKey(AGUI_PARAM_CONTEXT));
    }

    @Test
    public void testConvertFromAGUIInput_WithMessages_CreatesAgentInput() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        assertNotNull(result.getAgentInput());
        assertTrue(result.getAgentInput().getInput() instanceof List);
        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertNotNull(convertedMessages);
        assertEquals(1, convertedMessages.size());
        assertEquals("user", convertedMessages.get(0).getRole());
    }

    @Test
    public void testConvertFromAGUIInput_PreservesToolMessages() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Question");
        messages.add(userMsg);

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("content", "Tool result");
        toolMsg.addProperty("toolCallId", "call-123");
        messages.add(toolMsg);

        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.addProperty("content", "Answer");
        messages.add(assistantMsg);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(3, convertedMessages.size()); // user, tool, and assistant
        assertEquals("user", convertedMessages.get(0).getRole());
        assertEquals("tool", convertedMessages.get(1).getRole());
        assertEquals("call-123", convertedMessages.get(1).getToolCallId());
        assertEquals("assistant", convertedMessages.get(2).getRole());
    }

    @Test
    public void testConvertFromAGUIInput_PreservesAssistantWithToolCalls() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Question");
        messages.add(userMsg);

        // Assistant with toolCalls but no content
        JsonObject assistantWithToolCalls = new JsonObject();
        assistantWithToolCalls.addProperty("role", "assistant");
        assistantWithToolCalls.add("toolCalls", new JsonArray());
        messages.add(assistantWithToolCalls);

        // Final assistant with content
        JsonObject assistantWithContent = new JsonObject();
        assistantWithContent.addProperty("role", "assistant");
        assistantWithContent.addProperty("content", "Final answer");
        messages.add(assistantWithContent);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(3, convertedMessages.size()); // user, assistant with tool calls, and final assistant
        assertEquals("user", convertedMessages.get(0).getRole());
        assertEquals("assistant", convertedMessages.get(1).getRole());
        assertNotNull(convertedMessages.get(1).getToolCalls());
        assertEquals("assistant", convertedMessages.get(2).getRole());
        assertEquals("Final answer", convertedMessages.get(2).getContent().get(0).getText());
    }

    @Test
    public void testConvertFromAGUIInput_TextContent() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Simple text");
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        ContentBlock content = convertedMessages.get(0).getContent().get(0);
        assertEquals(ContentType.TEXT, content.getType());
        assertEquals("Simple text", content.getText());
    }

    @Test
    public void testConvertFromAGUIInput_MultimodalContent() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();

        // Text content
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", "Describe this image");
        contentArray.add(textContent);

        // Image content
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "binary");
        imageContent.addProperty("mimeType", "image/png");
        imageContent.addProperty("data", "base64encodeddata");
        contentArray.add(imageContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        List<ContentBlock> content = convertedMessages.get(0).getContent();
        assertEquals(2, content.size());

        assertEquals(ContentType.TEXT, content.get(0).getType());
        assertEquals("Describe this image", content.get(0).getText());

        assertEquals(ContentType.IMAGE, content.get(1).getType());
        assertNotNull(content.get(1).getImage());
        assertEquals("png", content.get(1).getImage().getFormat());
        assertEquals("base64encodeddata", content.get(1).getImage().getData());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_InvalidJson() {
        AGUIInputConverter.convertFromAGUIInput("invalid json", "agent-id", null, false);
    }

    @Test
    public void testConvertFromAGUIInput_PreservesParametersInDataset() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonObject forwardedProps = new JsonObject();
        forwardedProps.addProperty("customParam", "customValue");
        aguiInput.add("forwardedProps", forwardedProps);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();

        // All fields should be preserved
        assertTrue(params.containsKey(AGUI_PARAM_THREAD_ID));
        assertTrue(params.containsKey(AGUI_PARAM_RUN_ID));
        assertTrue(params.containsKey(AGUI_PARAM_TOOLS));
    }

    @Test
    public void testConvertFromAGUIInput_ImageContent_ExtractsFormat() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "binary");
        imageContent.addProperty("mimeType", "image/jpeg");
        imageContent.addProperty("data", "imagedata");
        contentArray.add(imageContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        ContentBlock imageBlock = convertedMessages.get(0).getContent().get(0);
        assertEquals(ContentType.IMAGE, imageBlock.getType());
        assertEquals("jpeg", imageBlock.getImage().getFormat());
    }

    @Test
    public void testConvertFromAGUIInput_NonImageBinaryContentSkipped() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();
        JsonObject binaryContent = new JsonObject();
        binaryContent.addProperty("type", "binary");
        binaryContent.addProperty("mimeType", "application/pdf");
        binaryContent.addProperty("data", "pdfdata");
        contentArray.add(binaryContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        // Non-image binary content should be skipped
        assertTrue(convertedMessages.get(0).getContent().isEmpty());
    }

    @Test
    public void testConvertFromAGUIInput_WithToolCalls() {
        String aguiInputJson = """
            {
              "threadId": "thread-123",
              "runId": "run-456",
              "tools": [],
              "messages": [
                {
                  "role": "assistant",
                  "content": "Let me check the weather",
                  "toolCalls": [
                    {
                      "id": "call-123",
                      "type": "function",
                      "function": {
                        "name": "get_weather",
                        "arguments": "{\\"location\\":\\"NYC\\"}"
                      }
                    }
                  ]
                }
              ]
            }
            """;

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(1, convertedMessages.size());
        assertEquals("assistant", convertedMessages.get(0).getRole());
        assertNotNull(convertedMessages.get(0).getToolCalls());
        assertEquals(1, convertedMessages.get(0).getToolCalls().size());
        assertEquals("call-123", convertedMessages.get(0).getToolCalls().get(0).getId());
        assertEquals("get_weather", convertedMessages.get(0).getToolCalls().get(0).getFunction().getName());
        assertEquals("{\"location\":\"NYC\"}", convertedMessages.get(0).getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    public void testConvertFromAGUIInput_WithToolResultMessages() {
        String aguiInputJson = """
            {
              "threadId": "thread-123",
              "runId": "run-456",
              "tools": [],
              "messages": [
                {"role": "user", "content": "What's the weather?"},
                {
                  "role": "assistant",
                  "toolCalls": [{
                    "id": "call-123",
                    "type": "function",
                    "function": {"name": "get_weather", "arguments": "{}"}
                  }]
                },
                {"role": "tool", "content": "72F and sunny", "toolCallId": "call-123"},
                {"role": "assistant", "content": "It's 72F and sunny!"}
              ]
            }
            """;

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();

        // Should have: user, assistant with toolCalls, tool, final assistant
        assertEquals(4, convertedMessages.size());
        assertEquals("user", convertedMessages.get(0).getRole());
        assertEquals("assistant", convertedMessages.get(1).getRole());
        assertNotNull(convertedMessages.get(1).getToolCalls());
        assertEquals(1, convertedMessages.get(1).getToolCalls().size());
        assertEquals("tool", convertedMessages.get(2).getRole());
        assertEquals("call-123", convertedMessages.get(2).getToolCallId());
        assertEquals("assistant", convertedMessages.get(3).getRole());
    }

    @Test
    public void testConvertFromAGUIInput_ContextNotEmbeddedInMessages() {
        String aguiInputJson = """
            {
              "threadId": "thread-123",
              "runId": "run-456",
              "tools": [],
              "messages": [
                {"role": "user", "content": "What's the weather?"}
              ],
              "context": [
                {"description": "Location", "value": "San Francisco"}
              ]
            }
            """;

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(1, convertedMessages.size());
        // Context should NOT be embedded in the message content (it's applied later at LLM call time)
        String content = convertedMessages.get(0).getContent().get(0).getText();
        assertEquals("What's the weather?", content);
        assertFalse(content.contains("Context:"));
        assertFalse(content.contains("Location: San Francisco"));

        // Context should still be stored in params for later use
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertTrue(dataset.getParameters().containsKey(AGUI_PARAM_CONTEXT));
    }

    @Test
    public void testConvertFromAGUIInput_MultipleToolCallsInSingleMessage() {
        String aguiInputJson = """
            {
              "threadId": "thread-123",
              "runId": "run-456",
              "tools": [],
              "messages": [
                {
                  "role": "assistant",
                  "content": "Let me get both weather and time",
                  "toolCalls": [
                    {
                      "id": "call-1",
                      "type": "function",
                      "function": {"name": "get_weather", "arguments": "{}"}
                    },
                    {
                      "id": "call-2",
                      "type": "function",
                      "function": {"name": "get_time", "arguments": "{}"}
                    }
                  ]
                }
              ]
            }
            """;

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(1, convertedMessages.size());
        assertEquals(2, convertedMessages.get(0).getToolCalls().size());
        assertEquals("get_weather", convertedMessages.get(0).getToolCalls().get(0).getFunction().getName());
        assertEquals("get_time", convertedMessages.get(0).getToolCalls().get(1).getFunction().getName());
    }

    // ==================== Test for memory_id parameter ====================

    @Test
    public void testConvertFromAGUIInput_SetsMemoryIdFromThreadId() {
        String threadId = "thread-memory-test-123";
        String aguiInputJson = buildMinimalAGUIInput(threadId, "run-1");

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataSet.getParameters();
        assertEquals(threadId, params.get("memory_id"));
        assertEquals(threadId, params.get(AGUI_PARAM_THREAD_ID));
    }

    // ==================== Tests for empty messages handling ====================

    @Test
    public void testConvertFromAGUIInput_EmptyMessages_NoAgentInput() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray()); // empty array
        aguiInput.add("tools", new JsonArray());

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        assertNotNull(result);
        // AgentInput should be null since messages are empty
        assertTrue("AgentInput should be null for empty messages", result.getAgentInput() == null);

        // AGUI_PARAM_LOAD_CHAT_HISTORY should be set when messages are empty
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertEquals("true", dataset.getParameters().get(AGUI_PARAM_LOAD_CHAT_HISTORY));
    }

    // ==================== Tests for convertToAGUIFormat ====================

    @Test
    public void testConvertToAGUIFormat_NullMessages() {
        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testConvertToAGUIFormat_EmptyMessages() {
        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testConvertToAGUIFormat_SingleTextContent() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello world");

        Message message = new Message("user", List.of(textBlock));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(message));

        assertEquals(1, result.size());
        assertEquals("user", result.get(0).get("role"));
        // Single text content should be a string, not array
        assertEquals("Hello world", result.get(0).get("content"));
    }

    @Test
    public void testConvertToAGUIFormat_MultipleContentBlocks() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image");

        ImageContent imageContent = new ImageContent();
        imageContent.setType(SourceType.BASE64);
        imageContent.setFormat("png");
        imageContent.setData("base64data");

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        imageBlock.setImage(imageContent);

        Message message = new Message("user", List.of(textBlock, imageBlock));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(message));

        assertEquals(1, result.size());
        // Multiple content blocks should be an array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get(0).get("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("Describe this image", content.get(0).get("text"));
        assertEquals("binary", content.get(1).get("type"));
        assertEquals("image/png", content.get(1).get("mimeType"));
        assertEquals("base64data", content.get(1).get("data"));
    }

    @Test
    public void testConvertToAGUIFormat_WithToolCalls() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Let me check");

        Message message = new Message("assistant", List.of(textBlock));
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"NYC\"}");
        ToolCall toolCall = new ToolCall("call-123", "function", function);
        message.setToolCalls(List.of(toolCall));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(message));

        assertEquals(1, result.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) result.get(0).get("toolCalls");
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("call-123", toolCalls.get(0).get("id"));
        assertEquals("function", toolCalls.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, String> func = (Map<String, String>) toolCalls.get(0).get("function");
        assertEquals("get_weather", func.get("name"));
        assertEquals("{\"location\":\"NYC\"}", func.get("arguments"));
    }

    @Test
    public void testConvertToAGUIFormat_WithToolCallId() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("72F and sunny");

        Message message = new Message("tool", List.of(textBlock));
        message.setToolCallId("call-123");

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(message));

        assertEquals(1, result.size());
        assertEquals("tool", result.get(0).get("role"));
        assertEquals("call-123", result.get(0).get("toolCallId"));
    }

    @Test
    public void testConvertToAGUIFormat_MultipleMessages() {
        ContentBlock userContent = new ContentBlock();
        userContent.setType(ContentType.TEXT);
        userContent.setText("What's the weather?");
        Message userMsg = new Message("user", List.of(userContent));

        ContentBlock assistantContent = new ContentBlock();
        assistantContent.setType(ContentType.TEXT);
        assistantContent.setText("It's 72F and sunny!");
        Message assistantMsg = new Message("assistant", List.of(assistantContent));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(userMsg, assistantMsg));

        assertEquals(2, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("What's the weather?", result.get(0).get("content"));
        assertEquals("assistant", result.get(1).get("role"));
        assertEquals("It's 72F and sunny!", result.get(1).get("content"));
    }

    @Test
    public void testConvertToAGUIFormat_RoundTrip() {
        // Test that converting from AGUI format and back produces equivalent messages
        String aguiInputJson = """
            {
              "threadId": "thread-123",
              "runId": "run-456",
              "tools": [],
              "messages": [
                {"role": "user", "content": "Hello"},
                {"role": "assistant", "content": "Hi there!"},
                {"role": "user", "content": "What's 2+2?"},
                {"role": "assistant", "content": "4"}
              ]
            }
            """;

        AgentMLInput agentMLInput = AGUIInputConverter.convertFromAGUIInput(aguiInputJson, "agent-id", null, false);
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) agentMLInput.getAgentInput().getInput();

        List<Map<String, Object>> roundTripped = AGUIInputConverter.convertToAGUIFormat(messages);

        assertEquals(4, roundTripped.size());
        assertEquals("user", roundTripped.get(0).get("role"));
        assertEquals("Hello", roundTripped.get(0).get("content"));
        assertEquals("assistant", roundTripped.get(1).get("role"));
        assertEquals("Hi there!", roundTripped.get(1).get("content"));
        assertEquals("user", roundTripped.get(2).get("role"));
        assertEquals("What's 2+2?", roundTripped.get(2).get("content"));
        assertEquals("assistant", roundTripped.get(3).get("role"));
        assertEquals("4", roundTripped.get(3).get("content"));
    }

    // ==================== Tests for appendContextToLatestUserMessage ====================

    @Test
    public void testAppendContextToLatestUserMessage_AppendsContext() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What's the weather?");
        Message userMsg = new Message("user", new ArrayList<>(List.of(textBlock)));

        List<Message> messages = new ArrayList<>(List.of(userMsg));

        JsonArray contextArray = new JsonArray();
        JsonObject contextItem = new JsonObject();
        contextItem.addProperty("description", "Location");
        contextItem.addProperty("value", "San Francisco");
        contextArray.add(contextItem);

        AGUIInputConverter.appendContextToLatestUserMessage(messages, contextArray);

        String content = messages.get(0).getContent().get(0).getText();
        assertTrue(content.contains("Context:"));
        assertTrue(content.contains("Location: San Francisco"));
        assertTrue(content.contains("What's the weather?"));
    }

    @Test
    public void testAppendContextToLatestUserMessage_EmptyMessages() {
        List<Message> messages = new ArrayList<>();
        JsonArray contextArray = new JsonArray();
        JsonObject contextItem = new JsonObject();
        contextItem.addProperty("description", "Location");
        contextItem.addProperty("value", "SF");
        contextArray.add(contextItem);

        // Should not throw
        AGUIInputConverter.appendContextToLatestUserMessage(messages, contextArray);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testAppendContextToLatestUserMessage_EmptyContext() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        Message userMsg = new Message("user", new ArrayList<>(List.of(textBlock)));
        List<Message> messages = new ArrayList<>(List.of(userMsg));

        // Empty context array should not modify the message
        AGUIInputConverter.appendContextToLatestUserMessage(messages, new JsonArray());

        assertEquals("Hello", messages.get(0).getContent().get(0).getText());
    }

    // ==================== Tests for message IDs in convertToAGUIFormat ====================

    @Test
    public void testConvertToAGUIFormat_IncludesMessageId() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        Message message = new Message("user", List.of(textBlock));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(message));

        assertEquals(1, result.size());
        assertNotNull("Each message should have an id", result.get(0).get("id"));
        assertTrue(result.get(0).get("id") instanceof String);
        assertFalse(((String) result.get(0).get("id")).isEmpty());
    }

    @Test
    public void testConvertToAGUIFormat_UniqueMessageIds() {
        ContentBlock textBlock1 = new ContentBlock();
        textBlock1.setType(ContentType.TEXT);
        textBlock1.setText("Hello");
        Message msg1 = new Message("user", List.of(textBlock1));

        ContentBlock textBlock2 = new ContentBlock();
        textBlock2.setType(ContentType.TEXT);
        textBlock2.setText("Hi");
        Message msg2 = new Message("assistant", List.of(textBlock2));

        List<Map<String, Object>> result = AGUIInputConverter.convertToAGUIFormat(List.of(msg1, msg2));

        assertEquals(2, result.size());
        String id1 = (String) result.get(0).get("id");
        String id2 = (String) result.get(1).get("id");
        assertNotNull(id1);
        assertNotNull(id2);
        assertFalse("Message IDs should be unique", id1.equals(id2));
    }

    private String buildMinimalAGUIInput(String threadId, String runId) {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", threadId);
        aguiInput.addProperty("runId", runId);
        aguiInput.add("state", new JsonObject());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        return gson.toJson(aguiInput);
    }
}
