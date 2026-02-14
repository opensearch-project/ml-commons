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
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;

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
        assertTrue(params.containsKey(AGUI_PARAM_MESSAGES));
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
    public void testConvertFromAGUIInput_ContextAppendedToLatestUserMessage() {
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
        String content = convertedMessages.get(0).getContent().get(0).getText();
        assertTrue(content.contains("Context:"));
        assertTrue(content.contains("Location: San Francisco"));
        assertTrue(content.contains("What's the weather?"));
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
